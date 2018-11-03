package org.dcache.nfs;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.net.HostAndPort;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import javax.cache.Cache;
import javax.cache.Caching;
import javax.security.auth.Subject;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.utils.ZKPaths;
import org.apache.zookeeper.CreateMode;
import org.dcache.nfs.bep.FileAttributeServiceGrpc;
import org.dcache.nfs.bep.FileSize;
import org.dcache.nfs.bep.StatusCode;
import org.dcache.nfs.status.BadHandleException;
import org.dcache.nfs.status.BadStateidException;
import org.dcache.nfs.status.NfsIoException;
import org.dcache.nfs.v4.AbstractNFSv4Operation;
import org.dcache.nfs.v4.CompoundContext;
import org.dcache.nfs.v4.NFSServerV41;
import org.dcache.nfs.v4.NFSv4OperationFactory;
import org.dcache.nfs.v4.NfsIdMapping;
import org.dcache.nfs.v4.OperationBIND_CONN_TO_SESSION;
import org.dcache.nfs.v4.OperationCREATE_SESSION;
import org.dcache.nfs.v4.OperationDESTROY_CLIENTID;
import org.dcache.nfs.v4.OperationDESTROY_SESSION;
import org.dcache.nfs.v4.OperationEXCHANGE_ID;
import org.dcache.nfs.v4.OperationGETATTR;
import org.dcache.nfs.v4.OperationILLEGAL;
import org.dcache.nfs.v4.OperationPUTFH;
import org.dcache.nfs.v4.OperationPUTROOTFH;
import org.dcache.nfs.v4.OperationRECLAIM_COMPLETE;
import org.dcache.nfs.v4.OperationSEQUENCE;
import org.dcache.nfs.v4.xdr.COMMIT4res;
import org.dcache.nfs.v4.xdr.COMMIT4resok;
import org.dcache.nfs.v4.xdr.READ4res;
import org.dcache.nfs.v4.xdr.READ4resok;
import org.dcache.nfs.v4.xdr.WRITE4res;
import org.dcache.nfs.v4.xdr.WRITE4resok;
import org.dcache.nfs.v4.xdr.count4;
import org.dcache.nfs.v4.xdr.nfs4_prot;
import org.dcache.nfs.v4.xdr.nfs_argop4;
import org.dcache.nfs.v4.xdr.nfs_opnum4;
import org.dcache.nfs.v4.xdr.nfs_resop4;
import org.dcache.nfs.v4.xdr.nfsace4;
import org.dcache.nfs.v4.xdr.stable_how4;
import org.dcache.nfs.v4.xdr.stateid4;
import org.dcache.nfs.vfs.AclCheckable;
import org.dcache.nfs.vfs.DirectoryStream;
import org.dcache.nfs.vfs.FsStat;
import org.dcache.nfs.vfs.Inode;
import org.dcache.nfs.vfs.Stat;
import org.dcache.nfs.vfs.VirtualFileSystem;
import org.dcache.nfs.zk.Paths;
import org.dcache.nfs.zk.ZkDataServer;
import org.dcache.oncrpc4j.rpc.OncRpcException;
import org.dcache.oncrpc4j.rpc.OncRpcProgram;
import org.dcache.oncrpc4j.rpc.OncRpcSvc;
import org.dcache.oncrpc4j.rpc.OncRpcSvcBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataServer {

    private final static Logger LOGGER = LoggerFactory.getLogger(DataServer.class);
    private static final String PNFS_DS_ADDRESS = "PNFS_DS_ADDRESS";

    private CuratorFramework zkCurator;
    private int port;
    private InetSocketAddress[] localInetAddresses;
    private String zkNode;

    private OncRpcSvc svc;
    private NFSServerV41 nfs;
    private IoChannelCache fsc;
    private String idFile;

    // we use 'other' part of stateid as sequence number can change
    private Cache<byte[], byte[]> mdsStateIdCache;


    private ManagedChannel channel;
    private FileAttributeServiceGrpc.FileAttributeServiceBlockingStub blockingStub;

    /**
     * Set TCP port number used by data server.
     *
     * @param port tcp port number.
     */
    public void setPort(int port) {
        this.port = port;
    }

    public void setCuratorFramework(CuratorFramework curatorFramework) {
        zkCurator = curatorFramework;
    }

    public void init() throws Exception {
        localInetAddresses = getLocalAddresses(port);

        nfs = new NFSServerV41.Builder()
                .withOperationFactory(new EDSNFSv4OperationFactory())
                .withVfs(VFS)
                .build();

        svc = new OncRpcSvcBuilder()
                .withPort(port)
                .withTCP()
                .withoutAutoPublish()
                .withRpcService(new OncRpcProgram(nfs4_prot.NFS4_PROGRAM, nfs4_prot.NFS_V4), nfs)
                .build();

        svc.start();

        long myId = ZkDataServer.getOrAllocateId(zkCurator, idFile);
        Mirror mirror = new Mirror(myId, localInetAddresses);
        zkNode = zkCurator.create()
                .creatingParentContainersIfNeeded()
                .withMode(CreateMode.EPHEMERAL)
                .forPath(ZKPaths.makePath(Paths.ZK_PATH, Paths.ZK_PATH_NODE + myId), ZkDataServer.toBytes(mirror));

        mdsStateIdCache = Caching
                .getCachingProvider()
                .getCacheManager()
                .getCache("open-stateid", byte[].class, byte[].class);

        channel = ManagedChannelBuilder
                .forAddress("mds", 2017)
                .usePlaintext() // disable SSL
                .build();

        blockingStub = FileAttributeServiceGrpc
                .newBlockingStub(channel);
    }

    public void setIoChannelCache(IoChannelCache fsCache) {
        this.fsc = fsCache;
    }

    public void setIdFile(String path) {
        idFile = path;
    }

    public void destroy() throws Exception {
        svc.stop();
        zkCurator.delete().forPath(zkNode);
    }

    private InetSocketAddress[] getLocalAddresses(int port) throws SocketException {
        List<InetSocketAddress> localaddresses = new ArrayList<>();

        // check for explicit address before discovery
        String suppliedAddress = System.getProperty(PNFS_DS_ADDRESS);

        if (Strings.isNullOrEmpty(suppliedAddress)) {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                if (!iface.isUp() || iface.getName().startsWith("br-")) {
                    continue;
                }

                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    localaddresses.add(new InetSocketAddress(addr, port));
                }
            }
            return localaddresses.toArray(new InetSocketAddress[0]);
        } else {
            return Splitter.on(',')
                    .trimResults()
                    .omitEmptyStrings()
                    .splitToList(suppliedAddress)
                    .stream()
                    .map(HostAndPort::fromString)
                    .map(s -> new InetSocketAddress(s.getHost(), s.getPort()))
                    .toArray(InetSocketAddress[]::new);
        }
    }

    private final VirtualFileSystem VFS = new VirtualFileSystem() {

        @Override
        public int access(Inode inode, int mode) throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void commit(Inode inode, long offset, int count) throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Inode create(Inode parent, Stat.Type type, String path, Subject subject, int mode) throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public FsStat getFsStat() throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Inode getRootInode() throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Inode lookup(Inode parent, String path) throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Inode link(Inode parent, Inode link, String path, Subject subject) throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public DirectoryStream list(Inode inode, byte[] verifier, long cookie) throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public byte[] directoryVerifier(Inode inode) throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Inode mkdir(Inode parent, String path, Subject subject, int mode) throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public boolean move(Inode src, String oldName, Inode dest, String newName) throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Inode parentOf(Inode inode) throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public int read(Inode inode, byte[] data, long offset, int count) throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public String readlink(Inode inode) throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void remove(Inode parent, String path) throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Inode symlink(Inode parent, String path, String link, Subject subject, int mode) throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public VirtualFileSystem.WriteResult write(Inode inode, byte[] data, long offset, int count, VirtualFileSystem.StabilityLevel stabilityLevel) throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Stat getattr(Inode inode) throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void setattr(Inode inode, Stat stat) throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public nfsace4[] getAcl(Inode inode) throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void setAcl(Inode inode, nfsace4[] acl) throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public boolean hasIOLayout(Inode inode) throws IOException {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public AclCheckable getAclCheckable() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public NfsIdMapping getIdMapper() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }
    };

    private class EDSNFSv4OperationFactory implements NFSv4OperationFactory {

        @Override
        public AbstractNFSv4Operation getOperation(nfs_argop4 op) {

            switch (op.argop) {
                case nfs_opnum4.OP_COMMIT:
                    return new DSOperationCOMMIT(op);
                case nfs_opnum4.OP_GETATTR:
                    return new OperationGETATTR(op);
                case nfs_opnum4.OP_PUTFH:
                    return new OperationPUTFH(op);
                case nfs_opnum4.OP_PUTROOTFH:
                    return new OperationPUTROOTFH(op);
                case nfs_opnum4.OP_READ:
                    return new DSOperationREAD(op);
                case nfs_opnum4.OP_WRITE:
                    return new DSOperationWRITE(op);
                case nfs_opnum4.OP_EXCHANGE_ID:
                    return new OperationEXCHANGE_ID(op);
                case nfs_opnum4.OP_CREATE_SESSION:
                    return new OperationCREATE_SESSION(op);
                case nfs_opnum4.OP_DESTROY_SESSION:
                    return new OperationDESTROY_SESSION(op);
                case nfs_opnum4.OP_SEQUENCE:
                    return new OperationSEQUENCE(op);
                case nfs_opnum4.OP_RECLAIM_COMPLETE:
                    return new OperationRECLAIM_COMPLETE(op);
                case nfs_opnum4.OP_BIND_CONN_TO_SESSION:
                    return new OperationBIND_CONN_TO_SESSION(op);
                case nfs_opnum4.OP_DESTROY_CLIENTID:
                    return new OperationDESTROY_CLIENTID(op);
                case nfs_opnum4.OP_ILLEGAL:
            }

            return new OperationILLEGAL(op);
        }
    }

    private class DSOperationWRITE extends AbstractNFSv4Operation {

        public DSOperationWRITE(nfs_argop4 args) {
            super(args, nfs_opnum4.OP_WRITE);
        }

        @Override
        public void process(CompoundContext context, nfs_resop4 result) throws IOException {

            final WRITE4res res = result.opwrite;

            long offset = _args.opwrite.offset.value;

            _args.opwrite.offset.checkOverflow(_args.opwrite.data.remaining(), "offset + length overflow");

            Inode inode = context.currentInode();
            stateid4 stateid = _args.opwrite.stateid;
            byte[] fh = mdsStateIdCache.get(stateid.other);
            if (fh == null) {
                throw new BadStateidException();
            }

            if (!Arrays.equals(fh, inode.toNfsHandle())) {
                throw new BadHandleException();
            }

            FileChannel out = fsc.get(inode).getChannel();

            _args.opwrite.data.rewind();
            int bytesWritten = out.write(_args.opwrite.data, offset);

            if (bytesWritten < 0) {
                throw new NfsIoException("IO not allowd");
            }

            res.status = nfsstat.NFS_OK;
            res.resok4 = new WRITE4resok();
            res.resok4.count = new count4(bytesWritten);
            res.resok4.committed = stable_how4.UNSTABLE4;
            res.resok4.writeverf = context.getRebootVerifier();
        }
    }

    private class DSOperationREAD extends AbstractNFSv4Operation {

        public DSOperationREAD(nfs_argop4 args) {
            super(args, nfs_opnum4.OP_READ);
        }

        @Override
        public void process(CompoundContext context, nfs_resop4 result) throws ChimeraNFSException, IOException {
            final READ4res res = result.opread;

            Inode inode = context.currentInode();

            stateid4 stateid = _args.opread.stateid;
            byte[] fh = mdsStateIdCache.get(stateid.other);
            if (fh == null) {
                throw new BadStateidException();
            }

            if (!Arrays.equals(fh, inode.toNfsHandle())) {
                throw new BadHandleException();
            }

            boolean eof = false;

            long offset = _args.opread.offset.value;
            int count = _args.opread.count.value;

            ByteBuffer bb = ByteBuffer.allocateDirect(count);
            FileChannel in = fsc.get(inode).getChannel();

            int bytesReaded = in.read(bb, offset);
            if (bytesReaded < 0) {
                eof = true;
            }

            res.status = nfsstat.NFS_OK;
            res.resok4 = new READ4resok();
            res.resok4.data = bb;
            res.resok4.eof = eof;
        }
    }

    private class DSOperationCOMMIT extends AbstractNFSv4Operation {

        public DSOperationCOMMIT(nfs_argop4 args) {
            super(args, nfs_opnum4.OP_COMMIT);
        }

        @Override
        public void process(CompoundContext context, nfs_resop4 result) throws ChimeraNFSException, IOException, OncRpcException {

            final COMMIT4res res = result.opcommit;

            Inode inode = context.currentInode();
            RandomAccessFile out = fsc.get(inode);

            FileSize size = FileSize.newBuilder()
                    .setSize(out.length())
                    .setFh(ByteString.copyFrom(inode.toNfsHandle()))
                    .build();
            StatusCode status = blockingStub.setFileSize(size);

            res.status = status.getStatus();
            nfsstat.throwIfNeeded(res.status);

            res.resok4 = new COMMIT4resok();
            res.resok4.writeverf = context.getRebootVerifier();
        }
    }
}

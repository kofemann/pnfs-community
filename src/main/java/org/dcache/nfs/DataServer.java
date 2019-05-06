package org.dcache.nfs;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.net.InetAddresses;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.UUID;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.utils.ZKPaths;
import org.apache.zookeeper.CreateMode;
import org.dcache.nfs.status.BadHandleException;
import org.dcache.nfs.status.BadStateidException;
import org.dcache.nfs.status.NfsIoException;
import org.dcache.nfs.v4.AbstractNFSv4Operation;
import org.dcache.nfs.v4.CompoundContext;
import org.dcache.nfs.v4.NFSServerV41;
import org.dcache.nfs.v4.NFSv4OperationFactory;
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
import org.dcache.nfs.v4.xdr.stable_how4;
import org.dcache.nfs.v4.xdr.stateid4;
import org.dcache.nfs.vfs.Inode;
import org.dcache.nfs.zk.Paths;
import org.dcache.nfs.zk.ZkDataServer;
import org.dcache.oncrpc4j.rpc.OncRpcException;
import org.dcache.oncrpc4j.rpc.OncRpcProgram;
import org.dcache.oncrpc4j.rpc.OncRpcSvc;
import org.dcache.oncrpc4j.rpc.OncRpcSvcBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.dcache.nfs.Utils.getLocalAddresses;

public class DataServer {

    private final static Logger LOGGER = LoggerFactory.getLogger(DataServer.class);
    private static final String PNFS_DS_ADDRESS = "PNFS_DS_ADDRESS";

    private CuratorFramework zkCurator;
    private int port;
    private int bepPort;
    private InetSocketAddress[] localInetAddresses;
    private String zkNode;

    private OncRpcSvc svc;
    private NFSServerV41 nfs;
    private IoChannelCache fsc;
    private String idFile;

    // we use 'other' part of stateid as sequence number can change
    private IMap<byte[], byte[]> mdsStateIdCache;

    private HazelcastInstance hz;

    private BackendServer bepSrv;

    public void setHazelcastClient(HazelcastInstance hz) {
        this.hz = hz;
    }

    /**
     * Set TCP port number used by data server.
     *
     * @param port tcp port number.
     */
    public void setPort(int port) {
        this.port = port;
    }

    public void setBepPort(int port) {
        this.bepPort = port;
    }

    public void setCuratorFramework(CuratorFramework curatorFramework) {
        zkCurator = curatorFramework;
    }

    public void init() throws Exception {
        localInetAddresses = getLocalOrConfiguredAddresses(port);

        nfs = new NFSServerV41.Builder()
                .withOperationFactory(new EDSNFSv4OperationFactory())
                .build();

        svc = new OncRpcSvcBuilder()
                .withPort(port)
                .withTCP()
                .withoutAutoPublish()
                .withRpcService(new OncRpcProgram(nfs4_prot.NFS4_PROGRAM, nfs4_prot.NFS_V4), nfs)
                .build();

        svc.start();
        bepSrv = new BackendServer(bepPort, fsc);
        InetSocketAddress[] bep = getLocalAddresses(bepPort);

        UUID myId = getOrAllocateId(idFile);
        Mirror mirror = new Mirror(myId, localInetAddresses, bep);
        zkNode = zkCurator.create()
                .creatingParentContainersIfNeeded()
                .withMode(CreateMode.EPHEMERAL)
                .forPath(ZKPaths.makePath(Paths.ZK_PATH, Paths.ZK_PATH_NODE + myId), ZkDataServer.toBytes(mirror));

        mdsStateIdCache = hz.getMap("open-stateid");

    }

    public void setIoChannelCache(IoChannelCache fsCache) {
        this.fsc = fsCache;
    }

    public void setIdFile(String path) {
        idFile = path;
    }

    public void destroy() throws Exception {
        svc.stop();
        bepSrv.shutdown();
        zkCurator.delete().forPath(zkNode);
    }

    private InetSocketAddress[] getLocalOrConfiguredAddresses(int port) throws SocketException {

        // check for explicit address before discovery
        String suppliedAddress = System.getProperty(PNFS_DS_ADDRESS);

        if (Strings.isNullOrEmpty(suppliedAddress)) {
            return getLocalAddresses(port);
        } else {
            return Splitter.on(',')
                    .trimResults()
                    .omitEmptyStrings()
                    .splitToList(suppliedAddress)
                    .stream()
                    .map(InetAddresses::forUriString)
                    .map(a -> new InetSocketAddress(a, port))
                    .toArray(InetSocketAddress[]::new);
        }
    }

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
            res.resok4.committed = stable_how4.DATA_SYNC4;
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
            bb.flip();
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

            out.getFD().sync();

            res.status = nfsstat.NFS_OK;
            res.resok4 = new COMMIT4resok();
            res.resok4.writeverf = context.getRebootVerifier();
        }
    }


    public static UUID getOrAllocateId(String idFile) throws IOException {

        Path p = new File(idFile).toPath();

        if (Files.isRegularFile(p)) {
            byte[] b = Files.readAllBytes(p);
            return UUID.fromString(new String(b, US_ASCII));
        } else if (Files.exists(p)) {
            throw new FileAlreadyExistsException("Path existis and not a regular file");
        }

        UUID id = UUID.randomUUID();
        Files.write(p, id.toString().getBytes(US_ASCII),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE, StandardOpenOption.DSYNC);
        return id;
    }

}

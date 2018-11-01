package org.dcache.nfs;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.apache.curator.framework.CuratorFramework;
import org.dcache.chimera.ChimeraFsException;
import org.dcache.chimera.FsInode;
import org.dcache.chimera.FsInodeType;
import org.dcache.chimera.JdbcFs;
import org.dcache.nfs.bep.FileAttributeServiceGrpc;
import org.dcache.nfs.bep.FileSize;
import org.dcache.nfs.bep.StatusCode;
import org.dcache.nfs.status.BadHandleException;
import org.dcache.nfs.vfs.Inode;

/**
 *
 */
public class BackendServer {

    private JdbcFs fs;
    private int port;
    private Server server;

    /**
     * Zookeeper client.
     */
    private CuratorFramework zkCurator;

    public void setFs(JdbcFs fs) {
        this.fs = fs;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setZkCurator(CuratorFramework zkCurator) {
        this.zkCurator = zkCurator;
    }


    public void init() throws IOException {
        server = ServerBuilder
                .forPort(2017)
                .addService(new FileAttributeService())
                .build();
        server.start();
    }

    public void shutdown() {
        server.shutdown();
    }

    private class FileAttributeService extends FileAttributeServiceGrpc.FileAttributeServiceImplBase {

        @Override
        public void setFileSiz(FileSize request, StreamObserver<StatusCode> responseObserver) {
            long size = request.getSize();
            byte[] fh = request.getFh().toByteArray();

            int status = nfsstat.NFS_OK;
            try {
                FsInode fsInode = toFsInode(new Inode(fh));
                org.dcache.chimera.posix.Stat stat = new org.dcache.chimera.posix.Stat();
                stat.setSize(size);
                fs.setInodeAttributes(fsInode, 0, stat);

            } catch (IOException e) {
                status = nfsstat.NFSERR_IO;
            }

            StatusCode reply = StatusCode.newBuilder().setStatus(status).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }
    }

    private FsInode toFsInode(Inode inode) throws IOException {
        return inodeFromBytes(inode.getFileId());
    }

    /**
     * Get a {code FsInode} corresponding to provided bytes.
     *
     * @param handle to construct inode from.
     * @return object inode.
     * @throws ChimeraFsException
     */
    public FsInode inodeFromBytes(byte[] handle) throws BadHandleException {
        FsInode inode;

        if (handle.length < 4) {
            throw new BadHandleException("Bad file handle");
        }

        ByteBuffer b = ByteBuffer.wrap(handle);
        int fsid = b.get();
        int type = b.get();
        int len = b.get(); // eat the file id size.
        long ino = b.getLong();
        int opaqueLen = b.get();
        if (opaqueLen > b.remaining()) {
            throw new BadHandleException("Bad/old file handle");
        }

        FsInodeType inodeType = FsInodeType.valueOf(type);

        switch (inodeType) {
            case INODE:
                if (opaqueLen != 1) {
                    throw new BadHandleException("Bad file handle: invalid level len :" + opaqueLen);
                }
                int level = b.get() - 0x30; // 0x30 is ascii code for '0'
                if (level != 0) {
                    throw new BadHandleException("Bad file handle: invalid level:" + level);
                }
                inode = new FsInode(fs, ino, level);
                break;
            default:
                // no other fansy types
                throw new BadHandleException("Unsupported file handle type: " + inodeType);
        }
        return inode;
    }

}

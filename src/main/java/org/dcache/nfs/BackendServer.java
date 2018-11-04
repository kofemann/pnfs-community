package org.dcache.nfs;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import org.apache.curator.framework.CuratorFramework;
import org.dcache.nfs.bep.FileAttributeServiceGrpc;
import org.dcache.nfs.bep.GetFileSizeRequest;
import org.dcache.nfs.bep.GetFileSizeResponse;
import org.dcache.nfs.bep.SetFileSizeRequest;
import org.dcache.nfs.bep.SetFileSizeResponse;
import org.dcache.nfs.vfs.Inode;

/**
 *
 */
public class BackendServer {

    private IoChannelCache fs;
    private int port;
    private Server server;

    /**
     * Zookeeper client.
     */
    private CuratorFramework zkCurator;

    public void setFs(IoChannelCache fs) {
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
        public void setFileSize(SetFileSizeRequest request, StreamObserver<SetFileSizeResponse> responseObserver) {
            long size = request.getSize();
            byte[] fh = request.getFh().toByteArray();

            int status = nfsstat.NFS_OK;
            try {
                fs.get(new Inode(fh)).setLength(size);
            } catch (IOException e) {
                status = nfsstat.NFSERR_IO;
            }

            SetFileSizeResponse reply = SetFileSizeResponse.newBuilder().setStatus(status).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }

        @Override
        public void getFileSize(GetFileSizeRequest request, StreamObserver<GetFileSizeResponse> responseObserver) {

            byte[] fh = request.getFh().toByteArray();
            int status = nfsstat.NFS_OK;
            long size = 0;

            try {
                size = fs.get(new Inode(fh)).length();
            } catch (IOException e) {
                status = nfsstat.NFSERR_IO;
            }

            GetFileSizeResponse reply = GetFileSizeResponse.newBuilder()
                    .setStatus(status)
                    .setSize(size)
                    .build();

            responseObserver.onNext(reply);
            responseObserver.onCompleted();

        }
    }

}

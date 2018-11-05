package org.dcache.nfs;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
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

    private final IoChannelCache fs;
    private final Server server;

    public BackendServer(int port, IoChannelCache fs) throws IOException {
        this.fs = fs;
        server = ServerBuilder
                .forPort(port)
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

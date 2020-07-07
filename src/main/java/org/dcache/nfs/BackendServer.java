package org.dcache.nfs;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import org.dcache.nfs.bep.DataServerBepServiceGrpc;
import org.dcache.nfs.bep.RemoveFileRequest;
import org.dcache.nfs.bep.RemoveFileResponse;
import org.dcache.nfs.bep.SetFileSizeRequest;
import org.dcache.nfs.bep.SetFileSizeResponse;
import org.dcache.nfs.vfs.Inode;

/** */
public class BackendServer {

  private final IoChannelCache fs;
  private final Server server;

  public BackendServer(int port, IoChannelCache fs) throws IOException {
    this.fs = fs;
    server = ServerBuilder.forPort(port).addService(new DataServerBepService()).build();
    server.start();
  }

  public void shutdown() {
    server.shutdown();
  }

  private class DataServerBepService extends DataServerBepServiceGrpc.DataServerBepServiceImplBase {

    @Override
    public void setFileSize(
        SetFileSizeRequest request, StreamObserver<SetFileSizeResponse> responseObserver) {
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
    public void removeFile(
        RemoveFileRequest request, StreamObserver<RemoveFileResponse> responseObserver) {

      byte[] fh = request.getFh().toByteArray();

      fs.remove(new Inode(fh));

      RemoveFileResponse reply = RemoveFileResponse.newBuilder().setStatus(nfsstat.NFS_OK).build();
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }
  }
}

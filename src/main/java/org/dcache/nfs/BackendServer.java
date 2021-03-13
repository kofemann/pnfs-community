package org.dcache.nfs;

import com.google.protobuf.ByteString;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

import org.dcache.nfs.bep.DataServerBepServiceGrpc;
import org.dcache.nfs.bep.ReadDataRequest;
import org.dcache.nfs.bep.ReadDataResponse;
import org.dcache.nfs.bep.RemoveFileRequest;
import org.dcache.nfs.bep.RemoveFileResponse;
import org.dcache.nfs.bep.SetFileSizeRequest;
import org.dcache.nfs.bep.SetFileSizeResponse;
import org.dcache.nfs.bep.WriteDataRequest;
import org.dcache.nfs.bep.WriteDataResponse;
import org.dcache.nfs.v4.xdr.stable_how4;
import org.dcache.nfs.vfs.Inode;
import org.dcache.nfs.vfs.VirtualFileSystem;

/** */
public class BackendServer {

  private final IoChannelCache fs;
  private final Server server;
  private final long bootVerifier = System.currentTimeMillis();

  public BackendServer(SocketAddress socketAddress, IoChannelCache fs) throws IOException {
    this.fs = fs;
    server = NettyServerBuilder.forAddress(socketAddress).addService(new DataServerBepService()).build();
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

    @Override
    public void writeData(WriteDataRequest request, StreamObserver<WriteDataResponse> responseObserver) {

      byte[] fh = request.getFh().toByteArray();
      int status = nfsstat.NFS_OK;

      try{
        fs.get(new Inode(fh)).getChannel().write(request.getData().asReadOnlyByteBuffer(), request.getOffset());
      } catch (IOException e) {
       status = nfsstat.NFSERR_IO;
      }
      WriteDataResponse reply = WriteDataResponse.newBuilder()
              .setHow(stable_how4.DATA_SYNC4)
              .setVerifier(bootVerifier)
              .setStatus(status)
              .build();
      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }

    @Override
    public void readData(ReadDataRequest request, StreamObserver<ReadDataResponse> responseObserver) {

      byte[] fh = request.getFh().toByteArray();
      int status = nfsstat.NFS_OK;

      ReadDataResponse.Builder responseBuilder = ReadDataResponse.newBuilder();

      try {
        ByteBuffer bb = ByteBuffer.allocate(request.getLength());
        fs.get(new Inode(fh)).getChannel().read(bb, request.getOffset());
        responseBuilder.setData(ByteString.copyFrom(bb.flip()));
      }catch (IOException e) {
        status = nfsstat.NFSERR_IO;
      }

      responseBuilder.setStatus(status);

      responseObserver.onNext(responseBuilder.build());
      responseObserver.onCompleted();
    }
  }
}

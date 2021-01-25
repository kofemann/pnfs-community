package org.dcache.nfs;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.net.HostAndPort;
import com.google.common.net.InetAddresses;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.utils.ZKPaths;
import org.apache.zookeeper.CreateMode;
import org.dcache.nfs.zk.Paths;
import org.dcache.nfs.zk.ZkDataServer;
import org.dcache.oncrpc4j.rpc.net.InetSocketAddresses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.dcache.nfs.Utils.getLocalAddresses;

public class DataServer {

  private static final Logger LOGGER = LoggerFactory.getLogger(DataServer.class);
  private static final String PNFS_DS_ADDRESS = "PNFS_DS_ADDRESS";

  private CuratorFramework zkCurator;
  private int port;
  private String bepSocket;
  private InetSocketAddress[] localInetAddresses;
  private String zkNode;

  private IoChannelCache fsc;
  private String idFile;
  private BackendServer bepSrv;

  public void setBepSocket(String socket) {
    this.bepSocket = socket;
  }

  public void setCuratorFramework(CuratorFramework curatorFramework) {
    zkCurator = curatorFramework;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public void init() throws Exception {
    localInetAddresses = getLocalOrConfiguredAddresses(port);

    InetSocketAddress bepSocketAddress;
    int i = bepSocket.lastIndexOf(':');
    if (i==-1) {
      bepSocketAddress = new InetSocketAddress(Integer.parseInt(bepSocket));
    } else {
      HostAndPort hp = HostAndPort.fromString(bepSocket);
      bepSocketAddress = new InetSocketAddress(hp.getHost(), hp.getPort());
    }

    bepSrv = new BackendServer(bepSocketAddress, fsc);

    UUID myId = getOrAllocateId(idFile);
    Mirror mirror = new Mirror(myId, localInetAddresses, bepSocketAddress);
    zkNode =
        zkCurator
            .create()
            .creatingParentContainersIfNeeded()
            .withMode(CreateMode.EPHEMERAL)
            .forPath(
                ZKPaths.makePath(Paths.ZK_PATH, Paths.ZK_PATH_NODE + myId),
                ZkDataServer.toBytes(mirror));
  }

  public void setIoChannelCache(IoChannelCache fsCache) {
    this.fsc = fsCache;
  }

  public void setIdFile(String path) {
    idFile = path;
  }

  public void destroy() throws Exception {
    bepSrv.shutdown();
    zkCurator.delete().forPath(zkNode);
  }

  private InetSocketAddress[] getLocalOrConfiguredAddresses(int port) throws SocketException {

    // check for explicit address before discovery
    String suppliedAddress = System.getProperty(PNFS_DS_ADDRESS);

    if (Strings.isNullOrEmpty(suppliedAddress)) {
      return getLocalAddresses(port);
    } else {
      return Splitter.on(',').trimResults().omitEmptyStrings().splitToList(suppliedAddress).stream()
          .map(InetAddresses::forUriString)
          .map(a -> new InetSocketAddress(a, port))
          .toArray(InetSocketAddress[]::new);
    }
  }

  public static UUID getOrAllocateId(String idFile) throws IOException {

    Path p = new File(idFile).toPath();

    if (Files.isRegularFile(p)) {
      return UUID.fromString(Files.readString(p));
    } else if (Files.exists(p)) {
      throw new FileAlreadyExistsException("Path exists and not a regular file");
    }

    UUID id = UUID.randomUUID();
    Files.writeString(
        p,
        id.toString(),
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
        StandardOpenOption.WRITE,
        StandardOpenOption.DSYNC);
    return id;
  }
}

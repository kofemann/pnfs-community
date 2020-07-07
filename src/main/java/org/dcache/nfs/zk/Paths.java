package org.dcache.nfs.zk;

public class Paths {

  private Paths() {}

  /** Path to data servers tree in ZooKeeper. */
  public static final String ZK_PATH = "/nfs/pnfs";

  /** Node name per data server. */
  public static final String ZK_PATH_NODE = "ds-";
}

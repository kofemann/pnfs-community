package org.dcache.nfs;

import java.net.InetSocketAddress;
import java.util.UUID;

/** */
public class Mirror {

  private final UUID id;
  private final InetSocketAddress[] multipath;
  private final InetSocketAddress bepMultipath;

  public Mirror(UUID id, InetSocketAddress[] multipath, InetSocketAddress bep) {
    this.id = id;
    this.multipath = multipath;
    this.bepMultipath = bep;
  }

  public UUID getId() {
    return id;
  }

  public InetSocketAddress getBepAddress() {
    return bepMultipath;
  }

  public InetSocketAddress[] getMultipath() {
    return multipath;
  }
}

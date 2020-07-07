package org.dcache.nfs.zk;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessSemaphoreMutex;
import org.apache.curator.utils.ZKPaths;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.data.Stat;
import org.dcache.nfs.v4.NFSv4StateHandler;

import java.nio.charset.StandardCharsets;

public class ZkNfsStateHaldler extends NFSv4StateHandler {

  String base;
  String identifier;
  String storeName;

  public void setIdentifier(String identifier) {
    this.identifier = identifier;
  }

  public void setStoreName(String storeName) {
    this.storeName = storeName;
  }

  CuratorFramework curatorFramework;

  public void setBase(String base) {
    this.base = base;
  }

  public void setCuratorFramework(CuratorFramework curatorFramework) {
    this.curatorFramework = curatorFramework;
  }

  /**
   * Get from zookeeper a instance wide unique ID associated with this door. If id doesn't exist,
   * then a global counter is used to assign one and store for the next time.
   *
   * @return newly created or stored id for this door.
   * @throws Exception
   *     <p>REVISIT: this logic can be used by other components as well.
   */
  public int getOrCreateId() throws Exception {
    String doorNode = ZKPaths.makePath(base, identifier);

    int stateMgrId;
    InterProcessSemaphoreMutex lock = new InterProcessSemaphoreMutex(curatorFramework, base);
    lock.acquire();
    try {
      String idNode = ZKPaths.makePath(doorNode, storeName);
      Stat zkStat = curatorFramework.checkExists().forPath(idNode);
      if (zkStat == null || zkStat.getDataLength() == 0) {

        // use parent's change version as desired counter
        Stat parentStat = curatorFramework.setData().forPath(base);
        stateMgrId = parentStat.getVersion();

        curatorFramework
            .create()
            .orSetData()
            .creatingParentContainersIfNeeded()
            .withMode(CreateMode.PERSISTENT)
            .forPath(idNode, Integer.toString(stateMgrId).getBytes(StandardCharsets.US_ASCII));
      } else {
        byte[] data = curatorFramework.getData().forPath(idNode);
        stateMgrId = Integer.parseInt(new String(data, StandardCharsets.US_ASCII));
      }

    } finally {
      lock.release();
    }
    return stateMgrId;
  }
}

/*
 * Copyright (c) 2009 - 2021 Deutsches Elektronen-Synchroton,
 * Member of the Helmholtz Association, (DESY), HAMBURG, GERMANY
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Library General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this program (see the file COPYING.LIB for more
 * details); if not, write to the Free Software Foundation, Inc.,
 * 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package org.dcache.nfs;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import static org.dcache.nfs.Utils.deviceidOf;
import org.dcache.nfs.bep.DataServerBepServiceGrpc;
import org.dcache.nfs.bep.ReadDataRequest;
import org.dcache.nfs.bep.ReadDataResponse;
import org.dcache.nfs.bep.RemoveFileRequest;
import org.dcache.nfs.bep.RemoveFileResponse;
import org.dcache.nfs.bep.SetFileSizeRequest;
import org.dcache.nfs.bep.SetFileSizeResponse;
import org.dcache.nfs.bep.WriteDataRequest;
import org.dcache.nfs.bep.WriteDataResponse;
import org.dcache.nfs.status.DelayException;
import org.dcache.nfs.status.ExistException;
import org.dcache.nfs.status.LayoutTryLaterException;
import org.dcache.nfs.status.NoEntException;
import org.dcache.nfs.status.NoXattrException;
import org.dcache.nfs.status.UnknownLayoutTypeException;
import org.dcache.nfs.v4.CompoundContext;
import org.dcache.nfs.v4.FlexFileLayoutDriver;
import org.dcache.nfs.v4.Layout;
import org.dcache.nfs.v4.LayoutDriver;
import org.dcache.nfs.v4.NFS4Client;
import org.dcache.nfs.v4.NFS4State;
import org.dcache.nfs.v4.NFSv41DeviceManager;
import org.dcache.nfs.v4.NFSv4Defaults;
import org.dcache.nfs.v4.NfsV41FileLayoutDriver;
import org.dcache.nfs.v4.Stateids;
import org.dcache.nfs.v4.ff.ff_layoutreturn4;
import org.dcache.nfs.v4.ff.flex_files_prot;
import org.dcache.nfs.v4.xdr.GETDEVICEINFO4args;
import org.dcache.nfs.v4.xdr.GETDEVICELIST4args;
import org.dcache.nfs.v4.xdr.LAYOUTCOMMIT4args;
import org.dcache.nfs.v4.xdr.LAYOUTERROR4args;
import org.dcache.nfs.v4.xdr.LAYOUTGET4args;
import org.dcache.nfs.v4.xdr.LAYOUTRETURN4args;
import org.dcache.nfs.v4.xdr.LAYOUTSTATS4args;
import org.dcache.nfs.v4.xdr.device_addr4;
import org.dcache.nfs.v4.xdr.deviceid4;
import org.dcache.nfs.v4.xdr.layout4;
import org.dcache.nfs.v4.xdr.layoutiomode4;
import org.dcache.nfs.v4.xdr.layoutreturn_type4;
import org.dcache.nfs.v4.xdr.layouttype4;
import org.dcache.nfs.v4.xdr.length4;
import org.dcache.nfs.v4.xdr.nfs4_prot;
import org.dcache.nfs.v4.xdr.nfs_fh4;
import org.dcache.nfs.v4.xdr.offset4;
import org.dcache.nfs.v4.xdr.stateid4;
import org.dcache.nfs.v4.xdr.utf8str_mixed;
import org.dcache.nfs.vfs.ForwardingFileSystem;
import org.dcache.nfs.vfs.Inode;
import org.dcache.nfs.vfs.Stat;
import org.dcache.nfs.vfs.VfsCache;
import org.dcache.nfs.vfs.VfsCacheConfig;
import org.dcache.nfs.vfs.VirtualFileSystem;
import org.dcache.nfs.zk.Paths;
import org.dcache.nfs.zk.ZkDataServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.protobuf.ByteString;
import com.hazelcast.map.IMap;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

/** the instance of this class have to ask Pool Manager for a pool and return it to the client. */
public class DeviceManager extends ForwardingFileSystem implements NFSv41DeviceManager {

  private static final Logger _log = LoggerFactory.getLogger(DeviceManager.class);

  /**
   * extended attribute used to store file's location. The location stored as a raw byte arrays ,
   * where each nfs4_prot.NFS4_DEVICEID4_SIZE bytes represent a single deviceid.
   */
  private static final String PNFS_LOCATION_XATTR = "pnfs.location";

  private final Map<deviceid4, DS> _deviceMap = new ConcurrentHashMap<>();

  // we need to return same layout stateid, as long as it's not returned
  private final Map<stateid4, NFS4State> _openToLayoutStateid = new ConcurrentHashMap<>();

  /** Zookeeper client. */
  private CuratorFramework zkCurator;

  /** Path cache to node with all on-line DSes. */
  private CuratorCache dsNodeCache;

  /** Layout type specific driver. */
  private final Map<layouttype4, LayoutDriver> _supportedDrivers;

  // we use 'other' part of stateid as sequence number can change
  private IMap<byte[], byte[]> mdsStateIdCache;

  @Autowired(required = false)
  private BiConsumer<CompoundContext, ff_layoutreturn4> layoutStats = (c, s) -> {};

  private VirtualFileSystem fs;

  private VfsCacheConfig cacheConfig;

  private VirtualFileSystem innter;

  public DeviceManager() {
    _supportedDrivers = new EnumMap<>(layouttype4.class);
  }

  public void setVfs(VirtualFileSystem fs) {
    this.innter = fs;
  }

  public void setCacheConfig(VfsCacheConfig cacheConfig) {
    this.cacheConfig = cacheConfig;
  }

  public void setCuratorFramework(CuratorFramework curatorFramework) {
    zkCurator = curatorFramework;
  }

  public void setLayoutReturnConsumer(BiConsumer<CompoundContext, ff_layoutreturn4> layoutStats) {
    this.layoutStats = layoutStats;
  }

  public void setOpenStateidCache(IMap<byte[], byte[]> openStateIdCache) {
    mdsStateIdCache = openStateIdCache;
  }

  @PostConstruct
  public void init() throws Exception {

    fs = new VfsCache(innter, cacheConfig);

    _supportedDrivers.put(
        layouttype4.LAYOUT4_FLEX_FILES,
        new FlexFileLayoutDriver(
            4,
            1,
            flex_files_prot.FF_FLAGS_NO_IO_THRU_MDS,
            1048576, // 1MB
            new utf8str_mixed("17"),
            new utf8str_mixed("17"),
            layoutStats));

    _supportedDrivers.put(layouttype4.LAYOUT4_NFSV4_1_FILES, new NfsV41FileLayoutDriver());

    dsNodeCache = CuratorCache.build(zkCurator, Paths.ZK_PATH);
    dsNodeCache
        .listenable()
        .addListener(
            (t, o, n) -> {
              switch (t) {
                case NODE_CREATED:
                case NODE_CHANGED:
                  _log.info("Adding DS: {}", n.getPath());
                  addDS(n);
                  break;
                case NODE_DELETED:
                  _log.info("Removing DS: {}", o.getPath());
                  removeDS(o);
                  break;
              }
            });
    dsNodeCache.start();
  }

  @PreDestroy
  public void shutdown() throws IOException {
    dsNodeCache.close();
  }
  /*
   * (non-Javadoc)
   *
   * @see org.dcache.nfsv4.NFSv41DeviceManager#layoutGet(CompoundContext context,
   *              LAYOUTGET4args args)
   */
  @Override
  public Layout layoutGet(CompoundContext context, LAYOUTGET4args args) throws IOException {

    final NFS4Client client = context.getSession().getClient();
    final stateid4 stateid = Stateids.getCurrentStateidIfNeeded(context, args.loga_stateid);
    final NFS4State nfsState = client.state(stateid);

    Inode inode = context.currentInode();
    layouttype4 layoutType = layouttype4.valueOf(args.loga_layout_type);
    LayoutDriver layoutDriver = getLayoutDriver(layoutType);

    deviceid4[] deviceId = getOrBindDeviceId(inode, args.loga_iomode, layoutType);

    NFS4State openState = nfsState.getOpenState();
    final stateid4 rawOpenState = openState.stateid();

    NFS4State layoutStateId = _openToLayoutStateid.get(rawOpenState);
    if (layoutStateId == null) {
      layoutStateId = client.createState(openState.getStateOwner(), openState);
      _openToLayoutStateid.put(stateid, layoutStateId);
      mdsStateIdCache.put(rawOpenState.other, context.currentInode().toNfsHandle());
      nfsState.addDisposeListener(
          state -> {
            _openToLayoutStateid.remove(rawOpenState);
            mdsStateIdCache.remove(rawOpenState.other);
          });
    }
    layoutStateId.bumpSeqid();

    nfs_fh4 fh = new nfs_fh4(context.currentInode().toNfsHandle());

    //  -1 is special value, which means entire file
    layout4 layout = new layout4();
    layout.lo_iomode = args.loga_iomode;
    layout.lo_offset = new offset4(0);
    layout.lo_length = new length4(nfs4_prot.NFS4_UINT64_MAX);
    layout.lo_content =
        layoutDriver.getLayoutContent(rawOpenState, NFSv4Defaults.NFS4_STRIPE_SIZE, fh, deviceId);

    return new Layout(true, layoutStateId.stateid(), new layout4[] {layout});
  }

  /*
   * (non-Javadoc)
   *
   * @see org.dcache.nfsv4.NFSv41DeviceManager#getDeviceInfo(CompoundContext context,
   *             GETDEVICEINFO4args args)
   */
  @Override
  public device_addr4 getDeviceInfo(CompoundContext context, GETDEVICEINFO4args args)
      throws ChimeraNFSException {

    deviceid4 deviceId = args.gdia_device_id;
    layouttype4 layoutType = layouttype4.valueOf(args.gdia_layout_type);

    _log.debug("lookup for device: {}, type: {}", deviceId, layoutType);

    DS ds = _deviceMap.get(deviceId);
    if (ds == null) {
      throw new NoEntException("Unknown device id: " + deviceId);
    }

    InetSocketAddress[] addrs = ds.getMultipathAddresses();

    // limit addresses returned to client to the same 'type' as clients own address
    // NOTICE: according to rfc1918 we allow access to private networks from public ip address
    // Site must take care that private IP space is not visible to site external clients.
    InetAddress clientAddress = context.getRemoteSocketAddress().getAddress();
    InetSocketAddress[] effectiveAddresses =
        Stream.of(addrs)
            .filter(a -> !a.getAddress().isLoopbackAddress() || clientAddress.isLoopbackAddress())
            .filter(a -> !a.getAddress().isLinkLocalAddress() || clientAddress.isLinkLocalAddress())
            .toArray(InetSocketAddress[]::new);

    LayoutDriver layoutDriver = getLayoutDriver(layoutType);
    return layoutDriver.getDeviceAddress(effectiveAddresses);
  }

  /*
   * (non-Javadoc)
   *
   * @see org.dcache.nfsv4.NFSv41DeviceManager#getDeviceList(CompoundContext context,
   *             GETDEVICELIST4args args)
   */
  @Override
  public List<deviceid4> getDeviceList(CompoundContext context, GETDEVICELIST4args args) {
    return new ArrayList<>(_deviceMap.keySet());
  }

  /*
   * (non-Javadoc)
   *
   * @see org.dcache.nfsv4.NFSv41DeviceManager#layoutReturn(CompoundContext context,
   *             LAYOUTRETURN4args args)
   */
  @Override
  public void layoutReturn(CompoundContext context, LAYOUTRETURN4args args)
      throws ChimeraNFSException {

    if (args.lora_layoutreturn.lr_returntype == layoutreturn_type4.LAYOUTRETURN4_FILE) {
      final stateid4 stateid =
          Stateids.getCurrentStateidIfNeeded(context, args.lora_layoutreturn.lr_layout.lrf_stateid);
      layouttype4 layoutType = layouttype4.valueOf(args.lora_layout_type);
      _log.debug("release device for stateid {}", stateid);
      final NFS4Client client = context.getSession().getClient();
      final NFS4State layoutState = client.state(stateid);
      _openToLayoutStateid.remove(layoutState.getOpenState().stateid());
      getLayoutDriver(layoutType)
          .acceptLayoutReturnData(context, args.lora_layoutreturn.lr_layout.lrf_body);
    }
  }

  /*
   * (non-Javadoc)
   *
   * @see org.dcache.nfsv4.NFSv41DeviceManager#layoutCommit(CompoundContext context,
   *             LAYOUTCOMMIT4args args)
   */
  @Override
  public OptionalLong layoutCommit(CompoundContext context, LAYOUTCOMMIT4args args)
      throws IOException {

    Inode inode = context.currentInode();
    if (args.loca_last_write_offset.no_newoffset) {
      Stat stat = fs.getattr(inode);
      long currentSize = stat.getSize();
      long newSize = args.loca_last_write_offset.no_offset.value + 1;
      if (newSize > currentSize) {
        Stat newStat = new Stat();
        newStat.setSize(newSize);
        fs.setattr(inode, newStat);
        return OptionalLong.of(newSize);
      }
    }
    return OptionalLong.empty();
  }

  /*
   * (non-Javadoc)
   *
   * @see org.dcache.nfsv4.NFSv41DeviceManager#layoutStats(CompoundContext context,
   *             LAYOUTSTATS4args args)
   */

  @Override
  public void layoutStats(CompoundContext contex, LAYOUTSTATS4args args) throws IOException {
    // NOP
  }

  /*
   * (non-Javadoc)
   *
   * @see org.dcache.nfsv4.NFSv41DeviceManager#layoutError(CompoundContext context,
   *             LAYOUTERROR4args args)
   */
  @Override
  public void layoutError(CompoundContext contex, LAYOUTERROR4args args) throws IOException {
    // NOP
  }

  private LayoutDriver getLayoutDriver(layouttype4 layoutType) throws UnknownLayoutTypeException {
    LayoutDriver layoutDriver = _supportedDrivers.get(layoutType);
    if (layoutDriver == null) {
      throw new UnknownLayoutTypeException("Unsupported Layout type: " + layoutType);
    }
    return layoutDriver;
  }

  /*
   * (non-Javadoc)
   *
   * @see org.dcache.nfsv4.NFSv41DeviceManager#getLayoutTypes()
   */
  @Override
  public Set<layouttype4> getLayoutTypes() {
    return _supportedDrivers.keySet();
  }

  private void addDS(ChildData childData) {
    if (childData.getData().length > 0) {
      Mirror mirror = ZkDataServer.stringToString(childData.getData());
      _deviceMap.put(deviceidOf(mirror.getId()), new DS(mirror));
    }
  }

  private void removeDS(ChildData childData) {
    String id = childData.getPath();
    if (!id.isEmpty()) {
      id = id.substring(Paths.ZK_PATH_NODE.length() + Paths.ZK_PATH.length() + 1);
    }
    UUID deviceId = UUID.fromString(id);
    DS ds = _deviceMap.remove(deviceidOf(deviceId));
    if (ds != null) {
      ds.shutdown();
    }
  }

  /**
   * Returns an array of device ids associated with the file. If binding between {@code inode} and
   * devices doesn't exists, then such binding is established.
   *
   * @param inode inode of the file
   * @return array of device ids.
   */
  private deviceid4[] getBoundDeviceId(Inode inode) throws IOException {

    try {
      byte[] combinedLocation = fs.getXattr(inode, PNFS_LOCATION_XATTR);

      deviceid4[] deviceid4s =
          new deviceid4[combinedLocation.length / nfs4_prot.NFS4_DEVICEID4_SIZE];

      for (int i = 0; i < deviceid4s.length; i++) {
        byte[] id =
            Arrays.copyOfRange(
                combinedLocation,
                i * nfs4_prot.NFS4_DEVICEID4_SIZE,
                (i + 1) * nfs4_prot.NFS4_DEVICEID4_SIZE);
        deviceid4s[i] = new deviceid4(id);
      }

      return deviceid4s;
    } catch (NoXattrException e) {
      return new deviceid4[0];
    }
  }

  /**
   * Returns an array of device ids associated with the file. If binding between {@code inode} and
   * devices doesn't exists, then such binding is established.
   *
   * @param inode inode of the file
   * @param iomode layout's IO mode
   * @param layoutType layout type for which device id is required
   * @return array of device ids.
   */
  private deviceid4[] getOrBindDeviceId(Inode inode, int iomode, layouttype4 layoutType)
      throws IOException {

    // independent from read or write, check existing location first
    deviceid4[] deviceId = getBoundDeviceId(inode);
    if (deviceId.length == 0) {

      // on read, we always expect a location
      if (iomode == layoutiomode4.LAYOUTIOMODE4_READ) {
        throw new LayoutTryLaterException("No location");
      }

      int mirrors = layoutType == layouttype4.LAYOUT4_FLEX_FILES ? 2 : 1;
      deviceId = _deviceMap.keySet().stream().unordered().limit(mirrors).toArray(deviceid4[]::new);

      if (deviceId.length == 0) {
        throw new LayoutTryLaterException("No dataservers available");
      }

      byte[] id = new byte[deviceId.length * nfs4_prot.NFS4_DEVICEID4_SIZE];
      for (int i = 0; i < deviceId.length; i++) {
        System.arraycopy(
            deviceId[i].value,
            0,
            id,
            i * nfs4_prot.NFS4_DEVICEID4_SIZE,
            nfs4_prot.NFS4_DEVICEID4_SIZE);
      }

      try {
        fs.setXattr(inode, PNFS_LOCATION_XATTR, id, SetXattrMode.CREATE);
      } catch (ExistException e) {
        // we fail, as other location is set. try again
        return getOrBindDeviceId(inode, iomode, layoutType);
      }
    }

    if (iomode == layoutiomode4.LAYOUTIOMODE4_READ && layoutType == layouttype4.LAYOUT4_NFSV4_1_FILES) {
      return new deviceid4[] {deviceId[0]};
    }
    return deviceId;
  }

  @Override
  public void setattr(Inode inode, Stat stat) throws IOException {
    if (stat.isDefined(Stat.StatAttribute.SIZE)) {
      deviceid4[] ids =
          getOrBindDeviceId(
              inode, layoutiomode4.LAYOUTIOMODE4_RW, layouttype4.LAYOUT4_NFSV4_1_FILES);

      for (deviceid4 id : ids) {
        DS ds = _deviceMap.get(id);
        if (ds == null) {
          _log.warn("No such DS: {}", id);
          throw new DelayException("Not all data servers online");
        }

        ds.setFileSize(inode, stat.getSize());
      }
    }
    delegate().setattr(inode, stat);
  }

  @Override
  public void remove(Inode parent, String nanme) throws IOException {

    Inode inode = lookup(parent, nanme);
    for (deviceid4 id : getBoundDeviceId(inode)) {
      DS ds = _deviceMap.get(id);
      if (ds == null) {
        _log.warn("No such DS: {}", id);
        throw new DelayException("Not all data servers online");
      }

      ds.removeFile(inode);
    }
    super.remove(parent, nanme);
  }

  @Override
  public int read(Inode inode, ByteBuffer data, long offset) throws IOException {
    deviceid4[] mirrors = getBoundDeviceId(inode);
    DS ds = _deviceMap.get(mirrors[0]);
    ByteBuffer d = ds.readData(inode, offset, data.remaining());
    int n = d.remaining();
    data.put(d);
    return n;
  }

  @Override
  public WriteResult write(Inode inode, ByteBuffer data, long offset, StabilityLevel stabilityLevel) throws IOException {

    deviceid4[] mirrors = getOrBindDeviceId(inode, layoutiomode4.LAYOUTIOMODE4_RW, layouttype4.LAYOUT4_FLEX_FILES);

    int n = data.remaining();
    for(deviceid4 mirror: mirrors) {
      DS ds = _deviceMap.get(mirror);
      ds.writeData(inode, offset, data);
    }

    return new WriteResult(StabilityLevel.DATA_SYNC, n);
  }

  @Override
  protected VirtualFileSystem delegate() {
    return fs;
  }

  private static class DS {

    DS(Mirror mirror) {
      addr = mirror.getMultipath();
      channel =
          ManagedChannelBuilder.forAddress(
                  mirror.getBepAddress().getAddress().getHostAddress(),
                  mirror.getBepAddress().getPort())
              .usePlaintext() // disable SSL
              .build();

      blockingStub = DataServerBepServiceGrpc.newBlockingStub(channel);
    }

    // gRPC channel and co.
    private final ManagedChannel channel;
    private final DataServerBepServiceGrpc.DataServerBepServiceBlockingStub blockingStub;
    private final InetSocketAddress[] addr;

    void setFileSize(Inode inode, long size) throws ChimeraNFSException {

      SetFileSizeRequest request =
          SetFileSizeRequest.newBuilder()
              .setFh(ByteString.copyFrom(inode.toNfsHandle()))
              .setSize(size)
              .build();

      SetFileSizeResponse response = blockingStub.setFileSize(request);
      nfsstat.throwIfNeeded(response.getStatus());
    }


    ByteBuffer readData(Inode inode, long offset, int length) throws ChimeraNFSException {
      ReadDataRequest request = ReadDataRequest.newBuilder()
              .setFh(ByteString.copyFrom(inode.toNfsHandle()))
              .setOffset(offset)
              .setLength(length)
              .build();

      ReadDataResponse response = blockingStub.readData(request);
      nfsstat.throwIfNeeded(response.getStatus());
      return response.getData().asReadOnlyByteBuffer();
    }

    int writeData(Inode inode, long offset, ByteBuffer data) throws ChimeraNFSException {

      WriteDataRequest request = WriteDataRequest.newBuilder()
              .setFh(ByteString.copyFrom(inode.toNfsHandle()))
              .setOffset(offset)
              .setData(ByteString.copyFrom(data))
              .build();

      WriteDataResponse response = blockingStub.writeData(request);
      nfsstat.throwIfNeeded(response.getStatus());
      return response.getHow();
    }

    void removeFile(Inode inode) throws ChimeraNFSException {

      RemoveFileRequest request =
          RemoveFileRequest.newBuilder().setFh(ByteString.copyFrom(inode.toNfsHandle())).build();

      RemoveFileResponse response = blockingStub.removeFile(request);
      nfsstat.throwIfNeeded(response.getStatus());
    }

    InetSocketAddress[] getMultipathAddresses() {
      return addr;
    }

    public void shutdown() {
      channel.shutdown();
    }
  }
}

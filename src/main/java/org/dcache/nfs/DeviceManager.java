/*
 * Copyright (c) 2009 - 2018 Deutsches Elektronen-Synchroton,
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

import com.google.common.io.BaseEncoding;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.dcache.nfs.v4.xdr.layout4;
import org.dcache.nfs.v4.xdr.stateid4;
import org.dcache.nfs.v4.xdr.nfs_fh4;
import org.dcache.nfs.v4.xdr.deviceid4;
import org.dcache.nfs.v4.xdr.nfs4_prot;
import org.dcache.nfs.v4.xdr.device_addr4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumMap;
import org.dcache.nfs.status.NoEntException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.cache.Cache;
import javax.cache.Caching;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.dcache.nfs.bep.FileAttributeServiceGrpc;
import org.dcache.nfs.bep.GetFileSizeRequest;
import org.dcache.nfs.bep.GetFileSizeResponse;
import org.dcache.nfs.chimera.ChimeraVfs;
import org.dcache.nfs.status.LayoutTryLaterException;
import org.dcache.nfs.status.NfsIoException;
import org.dcache.nfs.status.ServerFaultException;
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
import org.dcache.nfs.v4.ff.ff_ioerr4;
import org.dcache.nfs.v4.ff.ff_iostats4;
import org.dcache.nfs.v4.ff.ff_layoutreturn4;
import org.dcache.nfs.v4.ff.flex_files_prot;
import org.dcache.nfs.v4.xdr.layoutiomode4;
import org.dcache.nfs.v4.xdr.layouttype4;
import org.dcache.nfs.v4.xdr.length4;
import org.dcache.nfs.v4.xdr.offset4;
import org.dcache.nfs.v4.xdr.utf8str_mixed;
import org.dcache.nfs.vfs.Inode;
import org.dcache.nfs.vfs.Stat;
import org.dcache.nfs.zk.Paths;
import org.dcache.nfs.zk.ZkDataServer;
import org.dcache.oncrpc4j.util.Bytes;

/**
 *
 * the instance of this class have to ask Pool Manager for a pool and return it
 * to the client.
 *
 */

public class DeviceManager implements NFSv41DeviceManager {

    private static final Logger _log = LoggerFactory.getLogger(DeviceManager.class);

    private final Map<deviceid4, DS> _deviceMap =
            new ConcurrentHashMap<>();

    // we need to return same layout stateid, as long as it's not returned
    private final Map<stateid4, NFS4State> _openToLayoutStateid = new ConcurrentHashMap<>();


        // we need to return same layout stateid, as long as it's not returned
    private final Map<stateid4, deviceid4[]> _layoutStateidTodevice = new ConcurrentHashMap<>();

    /**
     * Zookeeper client.
     */
    private CuratorFramework zkCurator;

    /**
     * Path cache to node with all on-line DSes.
     */
    private PathChildrenCache dsNodeCache;

    /**
     * Layout type specific driver.
     */
    private final Map<layouttype4, LayoutDriver> _supportedDrivers;

    // we use 'other' part of stateid as sequence number can change
    private Cache<byte[], byte[]> mdsStateIdCache;

    private KafkaTemplate<Object, ff_iostats4> iostatKafkaTemplate;
    private KafkaTemplate<Object, ff_ioerr4> ioerrKafkaTemplate;

    private ChimeraVfs fs;

    public DeviceManager() {
        _supportedDrivers = new EnumMap<>(layouttype4.class);
    }

    public void setChimeraVfs(ChimeraVfs fs) {
        this.fs = fs;
    }

    public void setCuratorFramework(CuratorFramework curatorFramework) {
        zkCurator = curatorFramework;
    }

    public void init() throws Exception {
        final Consumer<ff_layoutreturn4> flexfilesStat = lr -> {

            for (ff_iostats4 iostat : lr.fflr_iostats_report) {
                iostatKafkaTemplate.sendDefault(iostat);
            }

            for (ff_ioerr4 ioerr : lr.fflr_ioerr_report) {
                ioerrKafkaTemplate.sendDefault(ioerr);
            }
        };

        _supportedDrivers.put(layouttype4.LAYOUT4_FLEX_FILES, new FlexFileLayoutDriver(4, 1,
                flex_files_prot.FF_FLAGS_NO_IO_THRU_MDS,
                new utf8str_mixed("17"), new utf8str_mixed("17"), flexfilesStat)
        );

        _supportedDrivers.put(layouttype4.LAYOUT4_NFSV4_1_FILES, new NfsV41FileLayoutDriver());

        mdsStateIdCache = Caching
                .getCachingProvider()
                .getCacheManager()
                .getCache("open-stateid", byte[].class, byte[].class);

        dsNodeCache = new PathChildrenCache(zkCurator, Paths.ZK_PATH, true);
        dsNodeCache.getListenable().addListener((c, e) -> {

            switch(e.getType()) {
                case CHILD_ADDED:
                case CHILD_UPDATED:
                    _log.info("Adding DS: {}", e.getData().getPath());
                    addDS(e.getData().getPath());
                    break;
                case CHILD_REMOVED:
                    _log.info("Removing DS: {}", e.getData().getPath());
                    removeDS(e.getData().getPath());
                    break;
            }
        });
        dsNodeCache.start();

    }
    /*
     * (non-Javadoc)
     *
     * @see org.dcache.nfsv4.NFSv41DeviceManager#layoutGet(CompoundContext context,
     *              Inode inode, int layoutType, int ioMode, stateid4 stateid)
     */
    @Override
    public Layout layoutGet(CompoundContext context, Inode inode, layouttype4 layoutType, int ioMode, stateid4 stateid)
            throws IOException {

        final NFS4Client client = context.getSession().getClient();
        final NFS4State nfsState = client.state(stateid);

        LayoutDriver layoutDriver = getLayoutDriver(layoutType);

        deviceid4[] deviceId = getOrBindDeviceId(inode, ioMode, layoutType);

        NFS4State openState = nfsState.getOpenState();
        final stateid4 rawOpenState = openState.stateid();

        NFS4State layoutStateId = _openToLayoutStateid.get(rawOpenState);
        if(layoutStateId == null) {
            layoutStateId = client.createState(openState.getStateOwner(), openState);
            _openToLayoutStateid.put(stateid, layoutStateId);
            _layoutStateidTodevice.put(layoutStateId.stateid(), deviceId);

            mdsStateIdCache.put(rawOpenState.other, context.currentInode().toNfsHandle());
            nfsState.addDisposeListener(
                    state -> {
                        _openToLayoutStateid.remove(rawOpenState);
                        mdsStateIdCache.remove(rawOpenState.other);
                    }
            );
        } else {
            layoutStateId.bumpSeqid();
        }

        nfs_fh4 fh = new nfs_fh4(context.currentInode().toNfsHandle());

        //  -1 is special value, which means entire file
        layout4 layout = new layout4();
        layout.lo_iomode = ioMode;
        layout.lo_offset = new offset4(0);
        layout.lo_length = new length4(nfs4_prot.NFS4_UINT64_MAX);
        layout.lo_content = layoutDriver.getLayoutContent(stateid,  NFSv4Defaults.NFS4_STRIPE_SIZE, fh, deviceId);

        return  new Layout(true, layoutStateId.stateid(), new layout4[]{layout});
    }

    /*
     * (non-Javadoc)
     *
     * @see org.dcache.nfsv4.NFSv41DeviceManager#getDeviceInfo(CompoundContext context, deviceid4 deviceId)
     */
    @Override
    public device_addr4 getDeviceInfo(CompoundContext context, deviceid4 deviceId, layouttype4 layoutType) throws ChimeraNFSException {

        _log.debug("lookup for device: {}, type: {}", deviceId, layoutType);

        InetSocketAddress[] addrs = _deviceMap.get(deviceId).addr;
        if (addrs == null) {
            throw new NoEntException("Unknown device id: " + deviceId);
        }

        // limit addresses returned to client to the same 'type' as clients own address
        InetAddress clientAddress = context.getRemoteSocketAddress().getAddress();
        InetSocketAddress[] effectiveAddresses = Stream.of(addrs)
                .filter(a -> !a.getAddress().isLoopbackAddress() || clientAddress.isLoopbackAddress())
                .filter(a -> !a.getAddress().isLinkLocalAddress() || clientAddress.isLinkLocalAddress())
                .filter(a -> !a.getAddress().isSiteLocalAddress() || clientAddress.isSiteLocalAddress())
                .toArray(InetSocketAddress[]::new);

        LayoutDriver layoutDriver = getLayoutDriver(layoutType);
        return layoutDriver.getDeviceAddress(effectiveAddresses);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.dcache.nfsv4.NFSv41DeviceManager#getDeviceList()
     */
    @Override
    public List<deviceid4> getDeviceList(CompoundContext context) {
        return new ArrayList<>(_deviceMap.keySet());
    }

    /*
     * (non-Javadoc)
     *
     * @see org.dcache.nfsv4.NFSv41DeviceManager#layoutReturn()
     */
    @Override
    public void layoutReturn(CompoundContext context, stateid4 stateid, layouttype4 layoutType, byte[] body) throws ChimeraNFSException {
        _log.debug("release device for stateid {}", stateid);
        final NFS4Client client = context.getSession().getClient();
        final NFS4State layoutState = client.state(stateid);
        _openToLayoutStateid.remove(layoutState.getOpenState().stateid());

        Inode inode = context.currentInode();
        GetFileSizeRequest request = GetFileSizeRequest
                .newBuilder()
                .setFh(ByteString.copyFrom(inode.toNfsHandle()))
                .build();

        deviceid4[] devices = _layoutStateidTodevice.get(stateid);
        DS ds = _deviceMap.get(devices[0]);
        try {
            GetFileSizeResponse response = ds.blockingStub.getFileSize(request);
            if (response.getStatus() == nfsstat.NFS_OK) {
                long newSize = response.getSize();
                Stat stat = fs.getattr(inode);
                if (newSize > stat.getSize()) {
                    Stat newStat = new Stat();
                    newStat.setSize(newSize);
                    fs.setattr(inode, newStat);
                }
            }
        } catch (IOException e) {
            throw new NfsIoException("failed to update filesize", e);
        }

        getLayoutDriver(layoutType).acceptLayoutReturnData(body);
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

    private static deviceid4 deviceidOf(long id) {
        byte[] deviceidBytes = new byte[nfs4_prot.NFS4_DEVICEID4_SIZE];
        Bytes.putLong(deviceidBytes, 0, id);

        return new deviceid4(deviceidBytes);
    }

    private void addDS(String node) throws Exception {
        byte[] data = zkCurator.getData().forPath(node);
        Mirror mirror = ZkDataServer.stringToString(data);

        DS ds = new DS();
        ds.addr = mirror.getMultipath();

        System.out.println("bep addr: " + mirror.getBepAddress()[0]);
        ds.channel = ManagedChannelBuilder
                .forAddress(mirror.getBepAddress()[0].getAddress().getHostAddress(), mirror.getBepAddress()[0].getPort())
                .usePlaintext() // disable SSL
                .build();

        ds.blockingStub = FileAttributeServiceGrpc
                .newBlockingStub(ds.channel);


        _deviceMap.put(deviceidOf(mirror.getId()), ds);
    }

    private void removeDS(String node) throws Exception {
        String id = node.substring(Paths.ZK_PATH_NODE.length() + Paths.ZK_PATH.length() + 1);
        int deviceId = Integer.parseInt(id);
        _deviceMap.remove(deviceidOf(deviceId));
    }

    public void setIoStatKafkaTemplate(KafkaTemplate<Object, ff_iostats4> template) {
        iostatKafkaTemplate = template;
    }

    public void setIoErrKafkaTemplate(KafkaTemplate<Object, ff_ioerr4> template) {
        ioerrKafkaTemplate = template;
    }


    /**
     * Returns an array of device ids associated with the file. If
     * binding between {@code inode} and devices doesn't exists, then such binding
     * is established.
     * @param inode inode of the file
     * @param iomode layout's IO mode
     * @param layoutType layout type for which device id is required
     * @return array of device ids.
     */
    private deviceid4[] getOrBindDeviceId(Inode inode, int iomode, layouttype4 layoutType) throws ChimeraNFSException, IOException {

        deviceid4[] deviceId;
        // independent from read or write, check existing location first
        String combinedLocation = fs.getInodeLayout(inode);
        if (combinedLocation != null) {

            byte[] raw = BaseEncoding.base16().lowerCase().decode(combinedLocation);
            if ((raw.length == 0) || (raw.length % Short.BYTES != 0)) {
                throw new ServerFaultException("invalid location size");
            }

            ByteBuffer b = ByteBuffer.wrap(raw);

            deviceId = new deviceid4[raw.length / Short.BYTES];
            for (int i = 0; i < deviceId.length; i++) {
                deviceId[i] = deviceidOf(b.getShort(i));
            }

        } else {

            // on read, we always expect a location
            if (iomode == layoutiomode4.LAYOUTIOMODE4_READ) {
                throw new LayoutTryLaterException("No location");
            }

            int mirrors = layoutType == layouttype4.LAYOUT4_FLEX_FILES ? 2 : 1;

            ByteBuffer b = ByteBuffer.allocate(Short.BYTES * mirrors);
            deviceId = _deviceMap.keySet().stream()
                    .unordered()
                    .limit(mirrors)
                    .peek(d -> b.putShort((short) Bytes.getInt(d.value, 0)))
                    .toArray(deviceid4[]::new);

            if (deviceId.length == 0) {
                throw new LayoutTryLaterException("No dataservers available");
            }

            if (!fs.setInodeLayout(inode, BaseEncoding.base16().lowerCase().encode(b.array()))) {
                // we fail, as other location is set. try again
                return getOrBindDeviceId(inode, iomode, layoutType);
            }
        }

        return deviceId;
    }


    private static class DS {
        // gRPC channel and co.

        private ManagedChannel channel;
        private FileAttributeServiceGrpc.FileAttributeServiceBlockingStub blockingStub;
        private InetSocketAddress[] addr;

    }

}

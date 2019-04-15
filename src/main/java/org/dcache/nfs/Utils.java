package org.dcache.nfs;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;
import org.dcache.nfs.v4.xdr.deviceid4;
import org.dcache.nfs.v4.xdr.nfs4_prot;
import org.dcache.oncrpc4j.util.Bytes;

/**
 *
 */
public class Utils {

    private Utils() {}

    public static InetSocketAddress[] getLocalAddresses(int port) throws SocketException {
        List<InetSocketAddress> localaddresses = new ArrayList<>();

        Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
        while (ifaces.hasMoreElements()) {
            NetworkInterface iface = ifaces.nextElement();
            if (!iface.isUp() || iface.getName().startsWith("br-") || iface.isLoopback()) {
                continue;
            }

            Enumeration<InetAddress> addrs = iface.getInetAddresses();
            while (addrs.hasMoreElements()) {
                InetAddress addr = addrs.nextElement();
                localaddresses.add(new InetSocketAddress(addr, port));
            }
        }
        return localaddresses.toArray(new InetSocketAddress[0]);
    }

    public static deviceid4 deviceidOf(UUID id) {
        byte[] deviceidBytes = new byte[nfs4_prot.NFS4_DEVICEID4_SIZE];
        Bytes.putLong(deviceidBytes, 0, id.getMostSignificantBits());
        Bytes.putLong(deviceidBytes, 8, id.getLeastSignificantBits());

        return new deviceid4(deviceidBytes);
    }

    public static UUID uuidOf(deviceid4 id) {
        long high = Bytes.getLong(id.value, 0);
        long low = Bytes.getLong(id.value, 8);

        return new UUID(high, low);
    }
}

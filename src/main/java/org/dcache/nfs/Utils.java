package org.dcache.nfs;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

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

}

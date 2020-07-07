package org.dcache.nfs;

import org.dcache.nfs.v4.xdr.deviceid4;
import org.dcache.nfs.v4.xdr.nfs4_prot;
import org.dcache.oncrpc4j.util.Bytes;

import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.UUID;

/** */
public class Utils {

  private Utils() {}

  public static InetSocketAddress[] getLocalAddresses(int port) throws SocketException {

    return NetworkInterface.networkInterfaces()
        .filter(Utils::isUp)
        .flatMap(NetworkInterface::inetAddresses)
        .map(a -> new InetSocketAddress(a, port))
        .toArray(InetSocketAddress[]::new);
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

  private static boolean isUp(NetworkInterface iface) {
    try {
      return iface.isUp() && !iface.isLoopback() && !iface.getName().startsWith("br-");
    } catch (SocketException e) {
      return false;
    }
  }
}

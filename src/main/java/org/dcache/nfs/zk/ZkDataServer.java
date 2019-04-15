package org.dcache.nfs.zk;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.util.UUID;
import org.dcache.nfs.Mirror;
import org.dcache.oncrpc4j.rpc.net.InetSocketAddresses;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ZkDataServer {

    public static byte[] toBytes(Mirror mirror) {
        JSONObject o = new JSONObject();
        o.put("version", "1.0");
        JSONArray a = new JSONArray();
        for (InetSocketAddress addr : mirror.getMultipath()) {
            a.put(InetSocketAddresses.uaddrOf(addr));
        }
        o.put("address", a);
        JSONArray b = new JSONArray();
        for (InetSocketAddress addr : mirror.getBepAddress()) {
            b.put(InetSocketAddresses.uaddrOf(addr));
        }
        o.put("bep", b);
        o.put("deviceid", mirror.getId().toString());

        return o.toString().getBytes(UTF_8);
    }

    public static Mirror stringToString(byte[] bytes) {

        JSONObject o = new JSONObject(new String(bytes, UTF_8));
        JSONArray a = o.getJSONArray("address");
        JSONArray b = o.getJSONArray("bep");
        UUID id = UUID.fromString(o.getString("deviceid"));
        InetSocketAddress[] addrs = new InetSocketAddress[a.length()];
        for (int i = 0; i < addrs.length; i++) {
            addrs[i] = InetSocketAddresses.forUaddrString(a.getString(i));
        }
        InetSocketAddress[] bep = new InetSocketAddress[b.length()];
        for (int i = 0; i < bep.length; i++) {
            bep[i] = InetSocketAddresses.forUaddrString(b.getString(i));
        }

        return new Mirror(id, addrs, bep);
    }
}

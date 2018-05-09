package org.dcache.nfs.zk;

import com.google.common.base.Throwables;
import java.io.File;
import java.io.IOException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.atomic.AtomicValue;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicLong;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.dcache.oncrpc4j.util.Bytes;
import org.dcache.oncrpc4j.rpc.net.InetSocketAddresses;

public class ZkDataServer {

    private static final String zkSequencePath = "/nfs/next-ds-id";

    public static byte[] toBytes(InetSocketAddress... addrs) {
        JSONObject o = new JSONObject();
        o.put("version", "1.0");
        JSONArray a = new JSONArray();
        for (InetSocketAddress addr : addrs) {
            a.put(InetSocketAddresses.uaddrOf(addr));
        }
        o.put("address", a);
        return o.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static InetSocketAddress[] stringToString(byte[] bytes) {

        JSONObject o = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        JSONArray a = o.getJSONArray("address");
        InetSocketAddress[] addrs = new InetSocketAddress[a.length()];
        for (int i = 0; i < addrs.length; i++) {
            addrs[i] = InetSocketAddresses.forUaddrString(a.getString(i));
        }
        return addrs;
    }

    public static long getOrAllocateId(CuratorFramework curator, String idFile) throws IOException {

        Path p = new File(idFile).toPath();

        if (Files.isRegularFile(p)) {
            byte[] b = Files.readAllBytes(p);
            if (b.length > Long.BYTES) {
                return Bytes.getLong(b, 0);
            }
        } else if (Files.exists(p)) {
            throw new FileAlreadyExistsException("Path existis and not a regular file");
        }

        try {
            DistributedAtomicLong dal = new DistributedAtomicLong(curator, zkSequencePath, new ExponentialBackoffRetry(1000, Integer.MAX_VALUE));
            AtomicValue<Long> v;
            do {
                v = dal.increment();
            } while (!v.succeeded());

            byte[] b = new byte[Long.BYTES];
            long id = v.postValue();
            Bytes.putLong(b, 0, id);

            Files.write(p, b, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, StandardOpenOption.DSYNC);
            return id;
        } catch (Exception e) {
            Throwables.throwIfInstanceOf(e, IOException.class);
            throw new IOException(e);
        }

    }
}

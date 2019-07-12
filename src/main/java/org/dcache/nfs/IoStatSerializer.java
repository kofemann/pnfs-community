package org.dcache.nfs;

import com.google.common.io.BaseEncoding;
import org.apache.kafka.common.serialization.Serializer;
import org.json.JSONObject;

import java.util.Map;

import org.dcache.nfs.v4.ff.ff_io_latency4;
import org.dcache.nfs.v4.ff.ff_iostats4;
import org.dcache.nfs.v4.xdr.io_info4;
import org.dcache.nfs.v4.xdr.nfstime4;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 *
 */
public class IoStatSerializer implements Serializer<ff_iostats4> {

    @Override
    public void configure(Map<String, ?> map, boolean bln) {
        // NOP
    }

    @Override
    public byte[] serialize(String string, ff_iostats4 stat) {

        JSONObject iostat = new JSONObject();

        iostat.put("deviceId", stat.ffis_deviceid.toString());
        iostat.put("fh", BaseEncoding.base16().lowerCase().encode(stat.ffis_layoutupdate.ffl_fhandle.value));
        iostat.put("open-stateid", BaseEncoding.base16().lowerCase().encode(stat.ffis_stateid.other));
        iostat.put("deviceAddr", stat.ffis_layoutupdate.ffl_addr.toString());
        iostat.put("duration", getNfsTime(stat.ffis_layoutupdate.ffl_duration));
        iostat.put("offset", stat.ffis_offset.value);
        iostat.put("length", stat.ffis_length.value);

        iostat.put("readInfo", getIoInfo(stat.ffis_read));
        iostat.put("writeInfo", getIoInfo(stat.ffis_write));

        iostat.put("readLatency", getIoLatency(stat.ffis_layoutupdate.ffl_read));
        iostat.put("writeLatency", getIoLatency(stat.ffis_layoutupdate.ffl_write));

        return iostat.toString().getBytes(UTF_8);

    }

    @Override
    public void close() {
        // NOP
    }

    private JSONObject getIoLatency(ff_io_latency4 latency) {
        JSONObject o = new JSONObject();

        o.put("opsRequested", latency.ffil_ops_requested.value);
        o.put("bytesRequested", latency.ffil_bytes_requested.value);
        o.put("opsCompleted", latency.ffil_ops_completed.value);
        o.put("bytesCompleted", latency.ffil_bytes_completed.value);
        o.put("bytesNotDelivered", latency.ffil_bytes_not_delivered.value);
        o.put("busyTime", getNfsTime(latency.ffil_total_busy_time));
        o.put("completitionTime", getNfsTime(latency.ffil_aggregate_completion_time));
        return o;
    }

    private JSONObject getIoInfo(io_info4 info) {
        JSONObject o = new JSONObject();

        o.put("count", info.ii_count);
        o.put("bytes", info.ii_bytes);
        return o;
    }

    private JSONObject getNfsTime(nfstime4 time) {
        JSONObject o = new JSONObject();

        o.put("seconds", time.seconds);
        o.put("nseconds", time.nseconds);
        return o;
    }

}

package org.dcache.nfs;

import com.google.common.io.BaseEncoding;
import org.apache.kafka.common.serialization.Serializer;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;

import org.dcache.nfs.v4.ff.ff_ioerr4;
import org.dcache.nfs.v4.xdr.device_error4;

import static java.nio.charset.StandardCharsets.UTF_8;

/** */
public class IoErrorSerializer implements Serializer<ff_ioerr4> {

  @Override
  public byte[] serialize(String string, ff_ioerr4 err) {
    JSONObject ioerr = new JSONObject();

    ioerr.put("offset", err.ffie_offset.value);
    ioerr.put("length", err.ffie_length.value);
    ioerr.put("open-stateid", BaseEncoding.base16().lowerCase().encode(err.ffie_stateid.other));
    JSONArray errors = new JSONArray();
    for (device_error4 de : err.ffie_errors) {
      JSONObject e = new JSONObject();
      e.put("deviceId", de.de_deviceid.toString());
      e.put("status", de.de_status);
      e.put("opnum", de.de_opnum);
      errors.put(e);
    }
    ioerr.put("device_error", errors);
    return ioerr.toString().getBytes(UTF_8);
  }

  @Override
  public void configure(Map<String, ?> map, boolean bln) {
    // NOP
  }

  @Override
  public void close() {
    // NOP
  }
}

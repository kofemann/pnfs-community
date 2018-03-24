package org.dcache.nfs.bep;

import java.io.IOException;
import org.dcache.nfs.v4.xdr.nfs_fh4;
import org.dcache.nfs.v4.xdr.stateid4;
import org.dcache.xdr.OncRpcException;
import org.dcache.xdr.XdrAble;
import org.dcache.xdr.XdrDecodingStream;
import org.dcache.xdr.XdrEncodingStream;

/**
 *
 */
public class SetSize implements XdrAble {

    public nfs_fh4 getFh() {
        return fh;
    }

    public long getSize() {
        return size;
    }


    private nfs_fh4 fh;
    private long size;

    public SetSize(nfs_fh4 fh, long size) {
        this.fh = fh;
        this.size = size;
    }

    public SetSize(XdrDecodingStream stream) throws IOException {
        xdrDecode(stream);
    }

    public SetSize() {
    }

    @Override
    public void xdrDecode(XdrDecodingStream stream) throws OncRpcException, IOException {
        fh = new nfs_fh4(stream);
        size = stream.xdrDecodeLong();
    }

    @Override
    public void xdrEncode(XdrEncodingStream stream) throws OncRpcException, IOException {
        fh.xdrEncode(stream);
        stream.xdrEncodeLong(size);
    }

}

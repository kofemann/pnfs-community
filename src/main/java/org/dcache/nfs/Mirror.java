package org.dcache.nfs;

import java.net.InetSocketAddress;

/**
 *
 */
public class Mirror {

    private final long id;
    private final InetSocketAddress[] multipath;
    private final InetSocketAddress[] bepMultipath;

    public Mirror(long id, InetSocketAddress[] multipath, InetSocketAddress[] bep) {
        this.id = id;
        this.multipath = multipath;
        this.bepMultipath = bep;
    }

    public long getId() {
        return id;
    }

    public InetSocketAddress[] getBepAddress() {
        return bepMultipath;
    }
    public InetSocketAddress[] getMultipath() {
        return multipath;
    }

}

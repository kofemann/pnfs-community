package org.dcache.nfs;

import java.net.InetSocketAddress;

/**
 *
 */
public class Mirror {

    private final long id;
    private final InetSocketAddress[] multipath;

    public Mirror(long id, InetSocketAddress[] multipath) {
        this.id = id;
        this.multipath = multipath;
    }

    public long getId() {
        return id;
    }

    public InetSocketAddress[] getMultipath() {
        return multipath;
    }

}

package org.dcache.nfs.chimera;

import java.io.IOException;
import org.dcache.nfs.vfs.ForwardingFileSystem;
import org.dcache.nfs.vfs.FsCache;
import org.dcache.nfs.vfs.Inode;
import org.dcache.nfs.vfs.Stat;
import org.dcache.nfs.vfs.VirtualFileSystem;

public class FsBackendedVfs extends ForwardingFileSystem {

    private  VirtualFileSystem vfs;
    private  FsCache fsCache;

    public void setVfs(VirtualFileSystem vfs) {
        this.vfs = vfs;
    }

    public void setFsCache(FsCache fsCache) {
        this.fsCache = fsCache;
    }

    @Override
    protected VirtualFileSystem delegate() {
        return vfs;
    }

    @Override
    public void setattr(Inode inode, Stat stat) throws IOException {
        super.setattr(inode, stat);
        if (stat.isDefined(Stat.StatAttribute.SIZE)) {
            fsCache.get(inode).truncate(stat.getSize());
        }
    }

}

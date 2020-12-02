/*
 * Copyright (c) 2009 - 2020 Deutsches Elektronen-Synchroton,
 * Member of the Helmholtz Association, (DESY), HAMBURG, GERMANY
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Library General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this program (see the file COPYING.LIB for more
 * details); if not, write to the Free Software Foundation, Inc.,
 * 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package org.dcache.nfs;

import com.google.common.base.Splitter;
import java.io.IOException;
import org.dcache.nfs.status.NoEntException;
import org.dcache.nfs.util.UnixSubjects;
import org.dcache.nfs.vfs.Inode;
import org.dcache.nfs.vfs.VirtualFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// use jdk.internal.misc.Signal for jdk-9
import sun.misc.Signal;

/** Class to scan export file and create missing directories */
public class ExportPathCreator {

  private static final Logger LOGGER = LoggerFactory.getLogger(ExportPathCreator.class);

  private ExportFile exportFile;
  private VirtualFileSystem vfs;

  public void setVfs(VirtualFileSystem vfs) {
    this.vfs = vfs;
  }

  public void setExportFile(ExportFile exportFile) {
    this.exportFile = exportFile;
  }

  private static Inode tryToCreateIfMissing(VirtualFileSystem vfs, Inode inode, String name)
      throws IOException {
    try {
      return vfs.lookup(inode, name);
    } catch (NoEntException e) {
      return vfs.mkdir(inode, name, UnixSubjects.toSubject(0, 0), 0777);
    }
  }

  public void init() throws IOException {

    registerSignalHandler(exportFile);

    Inode root = vfs.getRootInode();
    exportFile
        .exports()
        .map(FsExport::getPath)
        .forEach(
            path -> {
              Splitter splitter = Splitter.on('/').omitEmptyStrings();
              Inode inode = root;
              for (String s : splitter.split(path)) {
                try {
                  inode = tryToCreateIfMissing(vfs, inode, s);
                } catch (IOException e) {
                  return;
                }
              }
            });
  }

  @SuppressWarnings("restriction")
  private static void registerSignalHandler(final ExportFile exports) {
    Signal.handle(
        new Signal("HUP"),
        (Signal signal) -> {
          try {
            LOGGER.info("HUP signal received, rescanning exports file.");
            exports.rescan();
          } catch (IOException e) {
            LOGGER.error("Failed to re-read export file: {}", e.getMessage());
          }
        });
  }
}

/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.db;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;

/**
 * @author Daniel Bernstein
 *         Date: Feb 19, 2014
 */
public class ContentDirUtils {
    
    public static String getDestinationPath(String snapshotId, File rootDir) {
        return rootDir.getAbsolutePath()
            + File.separator + "snapshots" + File.separator
            + snapshotId;
    }

    public static String getSourcePath(Long restorationId, File rootDir) {
        return rootDir.getAbsolutePath()
            + File.separator + "restorations" + File.separator
            + restorationId;
    }
    
    /**
     * @param contentDir
     * @param filename
     * @return
     */
    public static Path getPath(File dir, String filename) {
        Path path =
            FileSystems.getDefault().getPath(dir.getAbsolutePath(),
                                             filename);
        return path;
    }

}

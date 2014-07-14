/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.db;

import java.io.File;

import org.duracloud.snapshot.db.model.Snapshot;

/**
 * @author Daniel Bernstein
 *         Date: Feb 19, 2014
 */
public class ContentDirUtils {
    
    public static String getDestinationPath(Snapshot snapshot, File rootDir) {
        return rootDir.getAbsolutePath()
            + File.separator + "snapshots" + File.separator
            + snapshot.getName();
    }

    public static String getSourcePath(Long restorationId, File rootDir) {
        return rootDir.getAbsolutePath()
            + File.separator + "restorations" + File.separator
            + restorationId;
    }

}

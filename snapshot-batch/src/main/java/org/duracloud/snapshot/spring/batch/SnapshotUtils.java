/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.spring.batch;

import java.io.File;

import org.duracloud.snapshot.spring.batch.config.SnapshotConfig;
import org.duracloud.snapshot.spring.batch.config.SnapshotJobManagerConfig;

/**
 * @author Daniel Bernstein
 *         Date: Feb 19, 2014
 */
public class SnapshotUtils {
    public static File resolveContentDir(SnapshotConfig snapshotConfig,
                                   SnapshotJobManagerConfig jobConfig) {
        File contentDir = snapshotConfig.getContentDir();
        if (contentDir == null) {
            File root = jobConfig.getContentRootDir();
            if (root != null) {
                contentDir =
                    new File(root,
                             snapshotConfig.getSnapshotId());
            }
        }

        if (contentDir != null) {
            contentDir.mkdirs();
            return contentDir;
        }

        throw new IllegalArgumentException("both contentDir and contentDirRoot settings are null. This case should never occur!");

    }

}

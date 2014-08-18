/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service;

import java.util.Map;

import org.duracloud.snapshot.SnapshotException;
import org.duracloud.snapshot.db.model.Snapshot;

/**
 * @author Daniel Bernstein
 *         Date: Jul 30, 2014
 */
public interface SnapshotManager {

    /**
     * @param snapshot
     * @param contentId
     * @param props
     */
    public void addContentItem(Snapshot snapshot,
                               String contentId,
                               Map<String, String> props) throws SnapshotException; 
 
    /**
     * Notifies the bridge that the snapshot's transfer to DPN node is complete.  This call is initiated
     * by the DPN node via the bridge REST API.
     * @param snapshotId
     * @throws SnapshotManagerException
     */
    public Snapshot transferToDpnNodeComplete(String snapshotId) throws SnapshotException;

    
    /**
     * Notifies the bridge that the clean up of the snapshot is complete.
     * @param snapshotId
     * @throws SnapshotManagerException
     */
    public Snapshot cleanupComplete(String snapshotId)  throws SnapshotException;
}

/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service;

import java.util.List;
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
     * Sets a snapshot's alternate Id's. To map DPN Bag Id's to Duracloud Snapshot Id's
     * @param snapshot
     * @param alternateId
     * @throws SnapshotException
     */
    public void setAlternateSnapshotIds(Snapshot snapshot, List<String> alternateIds);

    /**
     * Notifies the bridge that the snapshot's transfer to DPN node is complete.  This call is initiated
     * by the DPN node via the bridge REST API.
     * @param snapshotId
     * @throws SnapshotManagerException
     */
    public Snapshot transferToDpnNodeComplete(String snapshotId) throws SnapshotException;

    
    /**
     * This method runs through any snapshots with a status of CLEANING_UP and checks if the 
     * corresponding space is empty. If so, the state is updated to complete and notification is 
     * sent out to the user.
     */
    public void finalizeSnapshots();

    /**
     * Updates a snapshot's DPN metadata
     * @param snapshot
     * @param metadata
     * @return the altered snapshot
     */
	public Snapshot updateMetadata(Snapshot snapshot, String metadata);
}

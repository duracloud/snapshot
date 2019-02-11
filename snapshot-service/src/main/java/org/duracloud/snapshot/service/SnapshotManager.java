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
 * Date: Jul 30, 2014
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
     * Adds a list of snapshot alternate Id's to a snapshot. To map Bag Id's
     * to Duracloud Snapshot Id's
     *
     * @param snapshot
     * @param alternateIds
     * @return
     * @throws AlternateIdAlreadyExistsException
     */
    public Snapshot addAlternateSnapshotIds(Snapshot snapshot, List<String> alternateIds)
        throws AlternateIdAlreadyExistsException;

    /**
     * Notifies the bridge that the snapshot's transfer to storage is complete.
     * This call is initiated via the bridge REST API by the entity retrieving the
     * snapshot from bridge storage.
     *
     * @param snapshotId
     * @throws SnapshotException
     */
    public Snapshot transferToStorageComplete(String snapshotId) throws SnapshotException;

    /**
     * Notifies the bridge that the snapshot transfer action failed to complete due
     * to an error condition. This call is initiated via the bridge REST API by the
     * entity retrieving the snapshot from bridge storage.
     *
     * @param snapshotId
     * @param errorDetails
     * @return
     * @throws SnapshotException
     */
    public Snapshot transferError(String snapshotId, String errorDetails)
        throws SnapshotException;

    /**
     * This method runs through any snapshots with a status of CLEANING_UP and checks if the
     * corresponding space is empty. If so, the state is updated to complete and notification is
     * sent out to the user.
     */
    public void finalizeSnapshots();

    /**
     * Updates a snapshot's history
     *
     * @param snapshot
     * @param history
     * @return the altered snapshot
     */
    public Snapshot updateHistory(Snapshot snapshot, String history);

    public void deleteSnapshot(String snapshotId);
}

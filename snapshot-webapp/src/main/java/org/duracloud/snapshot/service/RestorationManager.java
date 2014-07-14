/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service;

import org.duracloud.snapshot.db.model.DuracloudEndPointConfig;
import org.duracloud.snapshot.db.model.Restoration;
import org.duracloud.snapshot.manager.SnapshotException;
import org.duracloud.snapshot.manager.SnapshotInProcessException;
import org.duracloud.snapshot.manager.SnapshotNotFoundException;

/**
 * @author Daniel Bernstein
 *         Date: Jul 15, 2014
 */
public interface RestorationManager {
    
    /**
     * 
     */
    void init(RestorationManagerConfig config);
    
    /**
     * Initiates the restoration of a snapshot.
     * @param snapshotId
     * @param destination
     * @return
     * @throws SnapshotNotFoundException
     * @throws SnapshotInProcessException
     * @throws SnapshotException
     */
    Restoration restoreSnapshot(String snapshotId, DuracloudEndPointConfig destination)
        throws SnapshotNotFoundException, 
            SnapshotInProcessException,
            SnapshotException;

    /**
     * Called by the process responsible for performing the restoration from 
     * DPN to Bridge Storage upon completion of the transfer.
     * 
     * @param restorationId
     * @return
     * @throws SnapshotNotFoundException
     * @throws SnapshotInProcessException
     * @throws NoRestorationInProcessException
     * @throws SnapshotException
     */
    Restoration restorationCompleted(Long restorationId)
        throws SnapshotNotFoundException,
            SnapshotInProcessException,
            NoRestorationInProcessException,
            SnapshotException;
}

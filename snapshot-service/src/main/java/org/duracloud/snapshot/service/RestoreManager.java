/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service;

import org.duracloud.snapshot.SnapshotException;
import org.duracloud.snapshot.SnapshotInProcessException;
import org.duracloud.snapshot.SnapshotNotFoundException;
import org.duracloud.snapshot.db.model.DuracloudEndPointConfig;
import org.duracloud.snapshot.db.model.Restoration;
import org.duracloud.snapshot.dto.RestoreStatus;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Daniel Bernstein
 *         Date: Jul 15, 2014
 */
public interface RestoreManager {
    
    /**
     * 
     * @param config
     * @param jobManager
     */
    void init(RestoreManagerConfig config, SnapshotJobManager jobManager);
    
    /**
     * Initiates the restoration of a snapshot.
     * @param snapshotId
     * @param destination
     * @param userEmail
     * @return
     * @throws SnapshotNotFoundException
     * @throws SnapshotInProcessException
     * @throws SnapshotException
     */
    Restoration restoreSnapshot(String snapshotId,
                                DuracloudEndPointConfig destination,
                                String userEmail)
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

    /**
     * @param restorationId
     * @return
     */
    Restoration get(Long restorationId) throws RestorationNotFoundException;

    /**
     * 
     * @param restorationId
     * @param status
     * @param message
     * @return
     */
    public Restoration transitionRestoreStatus(Long restorationId,
                                               RestoreStatus status,
                                               String message)
        throws InvalidStateTransitionException, RestorationNotFoundException;

    /**
     * @param snapshotId
     * @return
     */
    Restoration getBySnapshotId(String snapshotId)  throws RestorationNotFoundException ;
}

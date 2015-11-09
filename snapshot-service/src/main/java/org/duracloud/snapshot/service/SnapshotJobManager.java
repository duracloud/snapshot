/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service;

import org.duracloud.snapshot.SnapshotException;
import org.duracloud.snapshot.SnapshotNotFoundException;
import org.duracloud.snapshot.db.model.Restoration;
import org.springframework.batch.core.BatchStatus;


/**
 * An interface defining the interaction between the REST
 * api and job engine.  Primary function is to hide the details 
 * of the underlying job processing subsystem.
 * @author Daniel Bernstein
 *         Date: Feb 11, 2014
 */
public interface SnapshotJobManager  {

    
    public final static String JOB_REPOSITORY_KEY = "jobRepository";
    public final static String JOB_LAUNCHER_KEY = "jobLauncher";
    
    /**
     * Lazily initializes the component.  The data source must be fully configured
     * when this method is called; otherwise it will fail.
     * @param duracloudCredential
     * @throws AlreadyInitializedException if the service is already initialized.
     */
    void init(SnapshotJobManagerConfig duracloudCredential) throws AlreadyInitializedException;


    /**
     * 
     * @return true if the service is already initialized. 
     */
    boolean isInitialized();
    /**
     * This method creates an underlying job and executes it. 
     * @param snapshotId
     * @return
     * @throws SnapshotException
     */
    BatchStatus executeSnapshot(String snapshotId) throws SnapshotException;

    /**
     * 
     * @param snapshotId
     * @return
     * @throws SnapshotNotFoundException
     * @throws SnapshotException
     */
    BatchStatus getStatus(String snapshotId)
        throws SnapshotNotFoundException,
            SnapshotException;


    /**
     * 
     * @param restorationId
     * @return
     * @throws SnapshotException
     */
    public BatchStatus executeRestoration(String restorationId)
        throws SnapshotException;

    
    /**
     * Cancels a snapshot
     * @param snapshotId
     * @throws SnapshotException
     */
    public void cancelSnapshot(String snapshotId) throws SnapshotException;

    /**
     * Stops the restore process and removes source content and destination space.
     * Cancels a restore
     * @param restoreId
     * @throws SnapshotException
     */
    public void cancelRestore(String restoreId) throws SnapshotException;


    /**
     * Stops the restore process but leaves underlying file system and destination space
     * intact.
     * @param restoreId
     * @return
     * @throws SnapshotException
     */
    public void stopRestore(String restoreId) throws SnapshotException;

}

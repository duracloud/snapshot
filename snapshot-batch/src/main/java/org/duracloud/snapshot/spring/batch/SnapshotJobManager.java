/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.spring.batch;

import org.duracloud.snapshot.spring.batch.config.SnapshotConfig;
import org.duracloud.snapshot.spring.batch.config.SnapshotJobManagerConfig;

/**
 * An interface defining the interaction between the REST
 * api and job engine.  Primary function is to hide the details 
 * of the underlying job processing subsystem.
 * @author Daniel Bernstein
 *         Date: Feb 11, 2014
 */
public interface SnapshotJobManager {

    /**
     * Lazily initializes the component.  The data source must be fully configured
     * when this method is called; otherwise it will fail.
     * @param duracloudCredential
     */
    void init(SnapshotJobManagerConfig duracloudCredential);

    /**
     * This method of executing a snapshot is guaranteed to be an asynchronous version of executeSnapshot().
     * That is, the method will return after creating the job and queuing it up, rather than waiting until
     * it has been executed.
     * @param config
     * @return
     */
    SnapshotStatus executeSnapshotAsync(SnapshotConfig config)
        throws SnapshotException;

    /**
     * This method creates an underlying job and executes it.  Due to the fact that the underlying
     * framework does not guarantee that this will be a synchronous call, one cannot assume that 
     * the returned status will reflect a completed state of the operation. Ack!
     * @param config
     * @return
     * @throws SnapshotException
     */
    SnapshotStatus executeSnapshot(SnapshotConfig config) throws SnapshotException;

    /**
     * 
     * @param snapshotId
     * @return
     * @throws SnapshotNotFoundException
     * @throws SnapshotException
     */
    SnapshotStatus getStatus(String snapshotId)
        throws SnapshotNotFoundException,
            SnapshotException;

    /**
     * @return
     */
  /*  List<SnapshotSummary> getSnapshotList();*/

}

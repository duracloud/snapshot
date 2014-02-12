/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.rest;

import org.duracloud.snapshot.spring.batch.driver.SnapshotConfig;

/**
 * An interface defining the interaction between the REST
 * api and job engine.  Primary function is to hide the details 
 * of the underlying job processing subsystem.
 * @author Daniel Bernstein
 *         Date: Feb 11, 2014
 */
public interface SnapshotJobManager {

    /**
     * @param config
     * @return
     */
    SnapshotStatus executeSnapshot(SnapshotConfig config)
        throws SnapshotException;

    /**
     * @param snapshotId
     * @return
     */
    SnapshotStatus getStatus(String snapshotId) throws SnapshotNotFoundException;

    /**
     * @return
     */
  /*  List<SnapshotSummary> getSnapshotList();*/

}

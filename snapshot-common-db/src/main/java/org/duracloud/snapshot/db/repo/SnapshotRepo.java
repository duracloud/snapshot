/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.db.repo;

import java.util.List;

import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.dto.SnapshotStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * @author Daniel Bernstein
 *         Date: Jul 21, 2014
 */
@Repository(value="snapshotRepo")
public interface SnapshotRepo extends JpaRepository<Snapshot, Long> {

    /**
     * 
     * @param host
     * @return
     */
    public List<Snapshot> findBySourceHost(String host);

    /**
     * @param snapshotId
     * @return
     */
    public Snapshot findByName(String snapshotId);
    
    /**
     * 
     * @param status
     * @return
     */
    public List<Snapshot> findByStatus(SnapshotStatus status);
    

    /**
     * @param alternateId
     * @return
     */
    public Snapshot findBySnapshotAlternateIds(String alternateId);
}

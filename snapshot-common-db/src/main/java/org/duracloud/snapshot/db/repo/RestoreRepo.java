/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.db.repo;

import java.util.List;

import org.duracloud.snapshot.db.model.Restoration;
import org.duracloud.snapshot.dto.RestoreStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * @author Daniel Bernstein
 * Date: Jul 21, 2014
 */
@Repository(value = "restoreRepo")
public interface RestoreRepo extends JpaRepository<Restoration, Long> {

    /**
     * Returns a list of restorations for a given destinantion host.
     *
     * @param host
     * @return
     */
    public List<Restoration> findByDestinationHost(String host);

    /**
     * Returns a list of restorations based on the restoration's snapshot name property.
     *
     * @param name
     * @return
     */
    public List<Restoration> findBySnapshotNameOrderByModifiedDesc(String name);

    /**
     * @param restorationId
     * @return
     */
    public Restoration findByRestorationId(String restorationId);

    /**
     * Returns a list of restorations based on the restoration's status
     *
     * @param status
     * @return
     */
    public List<Restoration> findByStatus(RestoreStatus status);

    @Query("select r from Restoration r where r.status not in " +
           "('WAITING_FOR_DPN','RESTORATION_COMPLETE','RESTORATION_EXPIRED','ERROR')")
    public List<Restoration> findRunning();

    /**
     * Deletes the restore entity
     *
     * @param restoreId
     */
    public void deleteByRestorationId(String restoreId);
}

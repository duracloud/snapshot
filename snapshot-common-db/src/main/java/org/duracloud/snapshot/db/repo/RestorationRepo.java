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
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Daniel Bernstein
 *         Date: Jul 21, 2014
 */
@Repository(value="restorationRepo")
public interface RestorationRepo extends JpaRepository<Restoration, Long> {

    /**
     * Returns a list of restorations for a given destinantion host.
     * @param host
     * @return
     */
    public List<Restoration> findByDestinationHost(String host);
    
    /**
     * Returns a list of restorations based on the restoration's snapshot name property.
     * @param name
     * @return
     */
    public List<Restoration> findBySnapshotName(String name);
}

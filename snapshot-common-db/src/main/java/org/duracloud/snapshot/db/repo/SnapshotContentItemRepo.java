/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.db.repo;

import org.duracloud.snapshot.db.model.SnapshotContentItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author Daniel Bernstein
 *         Date: Jul 31, 2014
 */
@Repository(value="snapshotContentItemRepo")
public interface SnapshotContentItemRepo extends JpaRepository<SnapshotContentItem,Long> {

    public List<SnapshotContentItem>
        findBySnapshotNameAndContentIdStartingWithOrderByContentIdAsc(
             @Param("snapshotName") String snapshotName,
             @Param("contentId") String contentId,
             Pageable pageable);

    public long countBySnapshotName(@Param("snapshotName") String snapshotName);

    /**
     * @param snapshotName
     * @param pageable
     * @return
     */
    public Page<SnapshotContentItem> 
        findBySnapshotName(@Param("snapshotName") String snapshotName, 
                           Pageable pageable);

}

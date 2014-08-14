/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.db.repo;

import java.util.List;

import org.duracloud.snapshot.db.model.SnapshotContentItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * @author Daniel Bernstein
 *         Date: Jul 31, 2014
 */
@Repository(value="snapshotContentItemRepo")
public interface SnapshotContentItemRepo extends JpaRepository<SnapshotContentItem,Long> {

    public List<SnapshotContentItem>
        findBySnapshotNameAndContentIdStartingWithOrderByContentIdAsc(@Param("snapshotName") String snapshotName,
             @Param("contentId") String contentId,
             Pageable pageable);

}

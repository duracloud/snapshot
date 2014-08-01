/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service;

import java.util.Map;

import org.duracloud.snapshot.db.model.Snapshot;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Daniel Bernstein
 *         Date: Jul 30, 2014
 */
public interface SnapshotManager {

    /**
     * @param snapshot
     * @param contentId
     * @param props
     */
    public void addContentItem(Snapshot snapshot,
                               String contentId,
                               Map<String, String> props) throws SnapshotManagerException; 

}

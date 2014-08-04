/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import java.util.Map;

import org.duracloud.common.util.ChecksumUtil;
import org.duracloud.common.util.ChecksumUtil.Algorithm;
import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.db.model.SnapshotContentItem;
import org.duracloud.snapshot.db.repo.SnapshotContentItemRepo;
import org.duracloud.snapshot.service.SnapshotManager;
import org.duracloud.snapshot.service.SnapshotManagerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Daniel Bernstein Date: Jul 31, 2014
 */
@Component
public class SnapshotManagerImpl implements SnapshotManager {

    @Autowired
    private SnapshotContentItemRepo snapshotContentItemRepo;
    
    private ChecksumUtil checksumUtil;
    /**
     * @param snapshotContentItemRepo
     */
    public SnapshotManagerImpl() {
        this.checksumUtil = new ChecksumUtil(Algorithm.MD5);
    }

    /**
     * @param snapshotContentItemRepo the snapshotContentItemRepo to set
     */
    public void
        setSnapshotContentItemRepo(SnapshotContentItemRepo snapshotContentItemRepo) {
        this.snapshotContentItemRepo = snapshotContentItemRepo;
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see
     * org.duracloud.snapshot.service.SnapshotManager#addContentItem(org.duracloud
     * .snapshot.db.model.Snapshot, java.lang.String, java.util.Map)
     */
    @Override
    @Transactional
    public void addContentItem(Snapshot snapshot,
                               String contentId,
                               Map<String, String> props)
        throws SnapshotManagerException {
        SnapshotContentItem item = new SnapshotContentItem();
        item.setContentId(contentId);
        item.setSnapshot(snapshot);
        item.setContentIdHash(checksumUtil.generateChecksum(contentId));

        String propString = PropertiesSerializer.serialize(props);
        item.setMetadata(propString);
        this.snapshotContentItemRepo.save(item);
    }

}

/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.duracloud.client.task.SnapshotTaskClient;
import org.duracloud.client.task.SnapshotTaskClientManager;
import org.duracloud.common.notification.NotificationManager;
import org.duracloud.common.notification.NotificationType;
import org.duracloud.common.util.ChecksumUtil;
import org.duracloud.common.util.ChecksumUtil.Algorithm;
import org.duracloud.error.ContentStoreException;
import org.duracloud.snapshot.SnapshotException;
import org.duracloud.snapshot.SnapshotNotFoundException;
import org.duracloud.snapshot.db.model.DuracloudEndPointConfig;
import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.db.model.SnapshotContentItem;
import org.duracloud.snapshot.db.repo.SnapshotContentItemRepo;
import org.duracloud.snapshot.db.repo.SnapshotRepo;
import org.duracloud.snapshot.dto.SnapshotStatus;
import org.duracloud.snapshot.service.BridgeConfiguration;
import org.duracloud.snapshot.service.SnapshotManager;
import org.duracloud.snapshot.service.SnapshotManagerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Daniel Bernstein Date: Jul 31, 2014
 */
@Component
public class SnapshotManagerImpl implements SnapshotManager {
    private static Logger log = LoggerFactory.getLogger(SnapshotManagerImpl.class);

    //NOTE: auto wiring at the field level rather than in the constructor seems to be necessary
    //      when annotating methods with @Transactional.
    @Autowired
    private SnapshotContentItemRepo snapshotContentItemRepo;
    @Autowired
    private SnapshotRepo snapshotRepo;
    @Autowired
    private NotificationManager notificationManager;
    @Autowired
    private SnapshotTaskClientHelper snapshotTaskClientHelper;
    
    private ChecksumUtil checksumUtil;
    @Autowired
    private BridgeConfiguration bridgeConfig;

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

    /**
     * @param snapshotRepo the snapshotRepo to set
     */
    public void setSnapshotRepo(SnapshotRepo snapshotRepo) {
        this.snapshotRepo = snapshotRepo;
    }

    /**
     * @param notificationManager the notificationManager to set
     */
    public void setNotificationManager(NotificationManager notificationManager) {
        this.notificationManager = notificationManager;
    }

    /**
     * @param snapshotTaskClientHelper the snapshotTaskClientHelper to set
     */
    public void
        setSnapshotTaskClientHelper(SnapshotTaskClientHelper snapshotTaskClientHelper) {
        this.snapshotTaskClientHelper = snapshotTaskClientHelper;
    }

    /**
     * @param bridgeConfig the bridgeConfig to set
     */
    public void setBridgeConfig(BridgeConfiguration bridgeConfig) {
        this.bridgeConfig = bridgeConfig;
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
        throws SnapshotException {
        SnapshotContentItem item = new SnapshotContentItem();
        item.setContentId(contentId);
        item.setSnapshot(snapshot);
        item.setContentIdHash(checksumUtil.generateChecksum(contentId));

        String propString = PropertiesSerializer.serialize(props);
        item.setMetadata(propString);
        this.snapshotContentItemRepo.save(item);
    }
    
    
    /* (non-Javadoc)
     * @see org.duracloud.snapshot.service.SnapshotManager#transferToDpnNodeComplete(org.duracloud.snapshot.db.model.Snapshot)
     */
    @Override
    @Transactional
    public Snapshot transferToDpnNodeComplete(String snapshotId)
        throws SnapshotException {
        
        
        try {
            Snapshot snapshot = getSnapshot(snapshotId);

            snapshot.setStatus(SnapshotStatus.CLEANING_UP);
            snapshot = this.snapshotRepo.saveAndFlush(snapshot);
            
            DuracloudEndPointConfig source = snapshot.getSource();

            SnapshotTaskClient client =
                this.snapshotTaskClientHelper.create(source,
                                                     bridgeConfig.getDuracloudUsername(),
                                                     bridgeConfig.getDuracloudPassword());
            client.cleanupSnapshot(snapshotId);
            log.info("successfully initiated snapshot cleanup on DuraCloud for snapshotId = "
                + snapshotId);
            
            return snapshot;
        } catch (ContentStoreException e) {
            String message = "failed to initiate snapshot clean up: " + e.getMessage();
            log.error(message, e);
            throw new SnapshotManagerException(e.getMessage());
        }
    }


    /**
     * @param snapshotId
     * @return
     */
    private Snapshot getSnapshot(String snapshotId) throws SnapshotException {
        Snapshot snapshot = this.snapshotRepo.findByName(snapshotId);
        if (snapshot == null) {
            throw new SnapshotNotFoundException("A snapshot with id "
                + snapshotId + " does not exist.");
        }
        return snapshot;
    }
    
    
    /* (non-Javadoc)
     * @see org.duracloud.snapshot.service.SnapshotManager#cleanupComplete(java.lang.String)
     */
    @Override
    @Transactional
    public Snapshot cleanupComplete(String snapshotId)
        throws SnapshotException {
        Snapshot snapshot = getSnapshot(snapshotId);
        snapshot.setEndDate(new Date());
        snapshot.setStatus(SnapshotStatus.SNAPSHOT_COMPLETE);
        snapshot = snapshotRepo.saveAndFlush(snapshot);
        String message = "Snapshot complete: " + snapshotId;
        List<String> recipients =
            new ArrayList<>(Arrays.asList(this.bridgeConfig.getDuracloudEmailAddresses()));
        String userEmail = snapshot.getUserEmail();
        if (userEmail != null) {
            recipients.add(userEmail);
        }

        if (recipients.size() > 0) {
            this.notificationManager.sendNotification(NotificationType.EMAIL,
                                                      message,
                                                      message,
                                                      recipients.toArray(new String[0]));
        }
        
        return snapshot;
    }

}

/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import org.duracloud.client.ContentStore;
import org.duracloud.common.notification.NotificationManager;
import org.duracloud.common.notification.NotificationType;
import org.duracloud.common.util.DateUtil;
import org.duracloud.snapshot.SnapshotException;
import org.duracloud.snapshot.SnapshotInProcessException;
import org.duracloud.snapshot.SnapshotNotFoundException;
import org.duracloud.snapshot.db.ContentDirUtils;
import org.duracloud.snapshot.db.model.DuracloudEndPointConfig;
import org.duracloud.snapshot.db.model.Restoration;
import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.db.repo.RestoreRepo;
import org.duracloud.snapshot.db.repo.SnapshotRepo;
import org.duracloud.snapshot.dto.RestoreStatus;
import org.duracloud.snapshot.dto.SnapshotStatus;
import org.duracloud.snapshot.service.BridgeConfiguration;
import org.duracloud.snapshot.service.InvalidStateTransitionException;
import org.duracloud.snapshot.service.NoRestorationInProcessException;
import org.duracloud.snapshot.service.RestorationNotFoundException;
import org.duracloud.snapshot.service.RestorationStateTransitionValidator;
import org.duracloud.snapshot.service.RestoreManager;
import org.duracloud.snapshot.service.RestoreManagerConfig;
import org.duracloud.snapshot.service.SnapshotJobManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

/**
 * @author Daniel Bernstein
 *         Date: Jul 15, 2014
 */
@Component
public class RestoreManagerImpl  implements RestoreManager{
    private static Logger log =
        LoggerFactory.getLogger(RestoreManagerImpl.class);
    private RestoreManagerConfig config;
    private SnapshotJobManager jobManager;

    @Autowired
    private NotificationManager notificationManager;

    @Autowired
    private RestoreRepo restoreRepo;

    @Autowired
    private SnapshotRepo snapshotRepo;

    @Autowired
    private StoreClientHelper storeClientHelper;

    @Autowired
    private BridgeConfiguration bridgeConfig;

    public RestoreManagerImpl() {}    
    
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
     * @param restoreRepo the restoreRepo to set
     */
    public void setRestoreRepo(RestoreRepo restoreRepo) {
        this.restoreRepo = restoreRepo;
    }

    /**
     * @param storeClientHelper the storeClientHelper to set
     */
    public void setStoreClientHelper(StoreClientHelper storeClientHelper) {
        this.storeClientHelper = storeClientHelper;
    }

    /**
     * @param bridgeConfig the bridgeConfig to set
     */
    public void setBridgeConfig(BridgeConfiguration bridgeConfig) {
        this.bridgeConfig = bridgeConfig;
    }

    /* (non-Javadoc)
     * @see org.duracloud.snapshot.service.RestorationManager#restoreSnapshot(java.lang.String, org.duracloud.snapshot.db.model.DuracloudEndPointConfig)
     */
    @Override
    public Restoration restoreSnapshot(String snapshotId,
                                       DuracloudEndPointConfig destination,
                                       String userEmail)
        throws SnapshotNotFoundException, SnapshotInProcessException, SnapshotException {
        
        checkInitialized();
        
        Snapshot snapshot = getSnapshot(snapshotId);
        
        if(!snapshot.getStatus().equals(SnapshotStatus.SNAPSHOT_COMPLETE)){
            throw new SnapshotInProcessException("Snapshot is not complete. " +
                                                 "Restoration can only occur on a " +
                                                 "completed snapshot.");
        }

        Restoration restoration =
            createRestoration(snapshot, destination, userEmail);

        transitionRestoreStatus(restoration,
                                RestoreStatus.WAITING_FOR_DPN,
                                "Restoration request issued");

        restoration =  save(restoration);

        String restorationId = restoration.getRestorationId();
        File restoreDir = getRestoreDir(restorationId);
        restoreDir.mkdirs();

        //send email to DPN
        String subject = "Snapshot Restoration Request for Snapshot ID = " +
                         snapshotId;
        String body = "Please perform a snapshot restore.\n" +
                      "\nSnapshot ID: " + snapshotId +
                      "\nRestore ID: " + restorationId +
                      "\nRestore Location: " + restoreDir.getAbsolutePath();
        notificationManager.sendNotification(NotificationType.EMAIL,
                                             subject,
                                             body,
                                             getAllEMailAddresses(this.config));
        return restoration;
    }

    /**
     * @param restoration
     */
    private Restoration save(Restoration restoration) {
        Restoration saved =  restoreRepo.saveAndFlush(restoration);
        log.debug("saved {}", saved);
        return saved;
    }

    /**
     * @param snapshotId
     * @return
     */
    private Snapshot getSnapshot(String snapshotId) throws SnapshotNotFoundException{
        Snapshot snapshot = this.snapshotRepo.findByName(snapshotId);
        if(snapshot == null){
            throw new SnapshotNotFoundException(snapshotId);
        }
        
        return snapshot;
    }

    /**
     * @param snapshot
     * @param userEmail 
     * @return
     */
    private Restoration createRestoration(Snapshot snapshot,
                                          DuracloudEndPointConfig destination,
                                          String userEmail)
        throws SnapshotException {
        Restoration restoration = new Restoration();
        restoration.setDestination(destination);
        restoration.setSnapshot(snapshot);
        restoration.setUserEmail(userEmail);
        restoration.setStartDate(new Date());
        
        String restoreStartDate =
            DateUtil.convertToStringPlain(restoration.getStartDate().getTime());
        DuracloudEndPointConfig source = snapshot.getSource();
        
        String accountId = extractAccountId(source.getHost());
        
        String restorationId =  accountId + "_" + 
                                source.getStoreId() + "_" + 
                                source.getSpaceId() + "_" +
                                restoreStartDate;
        restoration.setRestorationId(restorationId);
        return restoration;
    }

    /**
     * @param host
     * @return
     */
    protected String extractAccountId(String host) {
        String accountId = host.split("[.]")[0];
        return accountId;
    }
    
    /**
     * @param config
     * @return
     */
    private String[] getAllEMailAddresses(RestoreManagerConfig config) {
        List<String> allAddresses = new ArrayList<String>();
        allAddresses.addAll(Arrays.asList(config.getDuracloudEmailAddresses()));
        allAddresses.addAll(Arrays.asList(config.getDpnEmailAddresses()));
        return allAddresses.toArray(new String[allAddresses.size()]);
    }

    /**
     * @param restorationId
     * @return
     */

    public Restoration getRestoration(String restorationId)
        throws RestorationNotFoundException {
        Restoration restoration =  this.restoreRepo.findByRestorationId(restorationId);
        if(restoration == null){
            throw new RestorationNotFoundException(restorationId);
        }
        
        return restoration;
    }

    /**
     * @param restorationId
     * @return
     */
    private File getRestoreDir(String restorationId) {
        File restoreDir = new File(getRestorationContentDir(restorationId));
        return restoreDir;
    }

    /* (non-Javadoc)
     * @see org.duracloud.snapshot.restoration.SnapshotRestorationManager#restoreCompleted(java.lang.String)
     */
    @Override
    public Restoration restoreCompleted(String restorationId)
        throws SnapshotNotFoundException,
            SnapshotInProcessException,
            NoRestorationInProcessException,
            SnapshotException {
        
        Restoration restoration = getRestoration(restorationId);
        
        RestoreStatus status = restoration.getStatus();
        
        if(status.equals(RestoreStatus.DPN_TRANSFER_COMPLETE)){
            log.warn("restoration {} already completed. Ignoring...", restoration);
            return restoration;
        } else if(status.equals(RestoreStatus.WAITING_FOR_DPN)){
            log.info("caller has indicated that restoration request {} is complete.",
                     restoration);
            Restoration updatedRestoration =
                transitionRestoreStatus(restorationId,
                                        RestoreStatus.DPN_TRANSFER_COMPLETE,
                                        "Completed restore to bridge storage");
            this.jobManager.executeRestoration(restorationId);
            
            return updatedRestoration;
        } else{
            String message =
                "restore status type "
                    + status + " not recognized. (restorationId = "
                    + restorationId + ")";
            log.error(message);
            throw new SnapshotException(message,null);
        }
        
    }
    
    private void checkInitialized() throws SnapshotException {
        if(this.config == null){
            throw new SnapshotException("The snapshot restoration manager has not " +
                                        "been initialized.", null);
        }
    }

    /* (non-Javadoc)
     * @see org.duracloud.snapshot.restoration.SnapshotRestorationManager#init(org.duracloud.snapshot.restoration.RestorationConfig)
     */
    @Override
    public void init(RestoreManagerConfig config, SnapshotJobManager jobManager) {
        this.config = config;
        this.jobManager = jobManager;
    }
    
    private String getRestorationContentDir(String restorationId) {
        return ContentDirUtils.getSourcePath(restorationId,
                                             new File(this.config.getRestorationRootDir()));
    }
    
    /* (non-Javadoc)
     * @see org.duracloud.snapshot.bridge.service.RestorationManager#getStatus(java.lang.String)
     */
    @Override
    public Restoration get(String restorationId)
        throws RestorationNotFoundException {
        Restoration restoration =  this.restoreRepo.findByRestorationId(restorationId);
        if(restoration == null){
            log.debug("Restoration returned null for {}. Throwing exception...",
                      restorationId);
            throw new RestorationNotFoundException(restorationId);
        }
        
        log.debug("got restoration {}", restoration);
        return restoration;
    }

    /* (non-Javadoc)
     * @see org.duracloud.snapshot.service.RestoreManager#getBySnapshotId(java.lang.String)
     */
    @Override
    public Restoration getBySnapshotId(String snapshotId)
        throws RestorationNotFoundException {
        List<Restoration> restorations =  this.restoreRepo.findBySnapshotNameOrderByModifiedDesc(snapshotId);
        if(CollectionUtils.isEmpty(restorations)){
            log.debug("Restoration returned null for snapshot id {}. Throwing exception...",
                      snapshotId);
            throw new RestorationNotFoundException(
                "No restorations associated with snapshot " + snapshotId);
        }
        
        return restorations.get(0);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.duracloud.snapshot.service.restore.RestoreManager#transitionRestoreStatus
     * (java.lang.Long, org.duracloud.snapshot.dto.RestoreStatus,
     * java.lang.String)
     */
    @Override
    @Transactional
    public Restoration transitionRestoreStatus(String restorationId,
                                               RestoreStatus status,
                                               String message)
        throws  InvalidStateTransitionException, RestorationNotFoundException {
        
        Restoration restoration = getRestoration(restorationId);
        transitionRestoreStatus(restoration, status, message);
        restoration = save(restoration);
        
        log.debug("transitioned restore status to {} for {}", status, restoration);
        return restoration;
    }

    /**
     * @param restoration
     * @param status
     * @param message
     * @throws InvalidStateTransitionException
     */
    private void transitionRestoreStatus(Restoration restoration, RestoreStatus status,
                                         String message)
        throws InvalidStateTransitionException {
        RestorationStateTransitionValidator.validate(restoration.getStatus(), status);
        restoration.setStatus(status);
        restoration.setStatusText(message + " on: " + new Date());
    }

    /* (non-Javadoc)
     * @see org.duracloud.snapshot.service.RestoreManager#finalizeRestores()
     */
    @Override
    @Transactional
    public void finalizeRestores() {
        log.debug("Running finalize restores...");
        List<Restoration> completedRestores =
            restoreRepo.findByStatus(RestoreStatus.RESTORATION_COMPLETE);

        for(Restoration restoration : completedRestores){
            Date expirationDate = restoration.getExpirationDate();
            if(expirationDate.before(new Date())) { // Only continue if expired
                DuracloudEndPointConfig destination = restoration.getDestination();
                ContentStore store =
                    storeClientHelper.create(destination,
                                             bridgeConfig.getDuracloudUsername(),
                                             bridgeConfig.getDuracloudPassword());
                try {
                    String spaceId = destination.getSpaceId();
                    Iterator<String> it  = store.getSpaceContents(spaceId);
                    if(!it.hasNext()) { // if space is empty
                        // Call DuraCloud to remove space
                        log.info("Deleting expired restoration space: " + spaceId +
                                 " at host: " + destination.getHost());
                        store.deleteSpace(spaceId);

                        // Update restore status
                        transitionRestoreStatus(restoration,
                                                RestoreStatus.RESTORATION_EXPIRED,
                                                "Restoration expired");
                        restoration =  save(restoration);
                        log.info("Transition of restore " +
                                 restoration.getRestorationId() +
                                 " to expired state completed successfully");
                    }
                } catch (Exception e) {
                    log.error("Failed to transition restore " +
                              restoration.getRestorationId() +
                              " to expired state due to: " + e.getMessage());
                }
            }
        }
    }

}

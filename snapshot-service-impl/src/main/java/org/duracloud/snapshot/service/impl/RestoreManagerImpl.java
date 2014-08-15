/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import org.duracloud.common.notification.NotificationManager;
import org.duracloud.common.notification.NotificationType;
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
 

    /* (non-Javadoc)
     * @see org.duracloud.snapshot.service.RestorationManager#restoreSnapshot(java.lang.String, org.duracloud.snapshot.db.model.DuracloudEndPointConfig)
     */
    @Override
    public Restoration restoreSnapshot(String snapshotId,
                                       DuracloudEndPointConfig destination,
                                       String userEmail)
        throws SnapshotNotFoundException,
            SnapshotInProcessException,
            SnapshotException {
        
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
                                "restoration request issued");

        restoration =  save(restoration);

        File restoreDir = getRestoreDir(restoration.getId());
        restoreDir.mkdirs();

        //send email to DPN
        String subject = "Snapshot Restoration Request for Snapshot ID = " +
                         snapshotId;
        String body = "Please perform a snapshot restore.\n\nSnapshot ID: " +
                      snapshotId + "\nRestore Location: " +
                      restoreDir.getAbsolutePath();
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
        return restoreRepo.save(restoration);
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
                                          DuracloudEndPointConfig destination, String userEmail)
        throws SnapshotException {
        Restoration restoration = new Restoration();
        restoration.setDestination(destination);
        restoration.setSnapshot(snapshot);
        restoration.setUserEmail(userEmail);
        restoration.setStartDate(new Date());
        return restoration;
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

    public Restoration getRestoration(Long restorationId)
        throws RestorationNotFoundException {
        Restoration restoration =  this.restoreRepo.findOne(restorationId);
        if(restoration == null){
            throw new RestorationNotFoundException(restorationId);
        }
        
        return restoration;
    }


    /**
     * @param restorationId
     * @return
     */
    private File getRestoreDir(Long restorationId) {
        File restoreDir = new File(getRestorationContentDir(restorationId));
        return restoreDir;
    }



    /* (non-Javadoc)
     * @see org.duracloud.snapshot.restoration.SnapshotRestorationManager#restorationCompleted(java.lang.String)
     */
    @Override
    public Restoration restorationCompleted(Long restorationId)
        throws SnapshotNotFoundException,
            SnapshotInProcessException,
            NoRestorationInProcessException,
            SnapshotException {
        
        Restoration restoration = getRestoration(restorationId);
        
        RestoreStatus status = restoration.getStatus();
        
        if(status.equals(RestoreStatus.DPN_TRANSFER_COMPLETE)){
            log.warn("restoration " + restorationId + " already completed. Ignoring...");
            return restoration;
        } else if(status.equals(RestoreStatus.WAITING_FOR_DPN)){
            log.info("caller has indicated that restoration request " + restorationId + " is complete.");
            transitionRestoreStatus(restorationId, RestoreStatus.DPN_TRANSFER_COMPLETE, "completed");
            this.jobManager.executeRestoration(restorationId);
            
            return restoration;
        } else{
            String message =
                "restore status type "
                    + status + " not recognized. (restorationId = "
                    + restorationId + ")";
            log.error(message);
            throw new SnapshotException(message,null);
        }
        
    }
    
    
    /**
     * 
     */
    private void checkInitialized() throws SnapshotException {   
        if(this.config == null){
            throw new SnapshotException("The snapshot restoration manager has not been initialized.", null);
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
    
    private String getRestorationContentDir(Long restorationId) {
        return ContentDirUtils.getSourcePath(restorationId, new File(this.config.getRestorationRootDir()));
    }
    
    /* (non-Javadoc)
     * @see org.duracloud.snapshot.bridge.service.RestorationManager#getStatus(java.lang.String)
     */
    @Override
    public Restoration get(Long restorationId)
        throws RestorationNotFoundException {
        Restoration restoration =  this.restoreRepo.findOne(restorationId);
        if(restoration == null){
            throw new RestorationNotFoundException(restorationId);
        }
        
        return restoration;
    }
    
    
    /* (non-Javadoc)
     * @see org.duracloud.snapshot.service.RestoreManager#getBySnapshotId(java.lang.String)
     */
    @Override
    public Restoration getBySnapshotId(String snapshotId) throws RestorationNotFoundException {
        List<Restoration> restorations =  this.restoreRepo.findBySnapshotNameOrderByModifiedDesc(snapshotId);
        if(CollectionUtils.isEmpty(restorations)){
            throw new RestorationNotFoundException("No restorations associated with snapshot " + snapshotId);
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
    public Restoration transitionRestoreStatus(Long restorationId,
                                               RestoreStatus status,
                                               String message)
        throws  InvalidStateTransitionException, RestorationNotFoundException {
        
        Restoration restoration = getRestoration(restorationId);
        transitionRestoreStatus(restoration, status, message);
        restoration = save(restoration);
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
        restoration.setStatusText(new Date() + ": " + message);
    }

}

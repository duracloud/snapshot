/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.duracloud.common.notification.NotificationManager;
import org.duracloud.common.notification.NotificationType;
import org.duracloud.snapshot.db.model.DuracloudEndPointConfig;
import org.duracloud.snapshot.db.model.Restoration;
import org.duracloud.snapshot.db.model.RestorationStatus;
import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.db.model.SnapshotStatus;
import org.duracloud.snapshot.db.repo.RestorationRepo;
import org.duracloud.snapshot.db.repo.SnapshotRepo;
import org.duracloud.snapshot.manager.SnapshotException;
import org.duracloud.snapshot.manager.SnapshotInProcessException;
import org.duracloud.snapshot.manager.SnapshotJobManager;
import org.duracloud.snapshot.manager.SnapshotNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Daniel Bernstein
 *         Date: Jul 15, 2014
 */
@Component
public class RestorationManagerImpl  implements RestorationManager{
    private static Logger log =
        LoggerFactory.getLogger(RestorationManagerImpl.class);
    private RestorationManagerConfig config;
    private SnapshotJobManager jobManager;
    private NotificationManager notificationManager;
    private RestorationRepo restorationRepo;
    private SnapshotRepo snapshotRepo;

    @Autowired
    public RestorationManagerImpl(  SnapshotJobManager jobManager, 
                                    NotificationManager notificationManager,
                                    RestorationRepo restorationRepo, 
                                    SnapshotRepo snapshotRepo) {
        this.jobManager = jobManager;
        this.notificationManager = notificationManager;
        this.restorationRepo = restorationRepo;
        this.snapshotRepo = snapshotRepo;
    }    


    /* (non-Javadoc)
     * @see org.duracloud.snapshot.service.RestorationManager#restoreSnapshot(java.lang.String, org.duracloud.snapshot.db.model.DuracloudEndPointConfig)
     */
    @Override
    public Restoration restoreSnapshot(String snapshotId,
                                       DuracloudEndPointConfig destination)
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

        Restoration restoration = createRestoration(snapshot, destination);
        File restoreDir = getRestoreDir(restoration.getId());
        restoreDir.mkdirs();
            
        //send email to DPN 
        notificationManager.sendNotification(NotificationType.EMAIL,
                                 "Snapshot Restoration Request for Snapshot ID = " + snapshotId,
                                 "Please restore the following snapshot to the following location: " + restoreDir.getAbsolutePath(),
                                 getAllEMailAddresses(this.config));

        restoration.setMemo("request issued at " + new Date());
        restoration.setStatus(RestorationStatus.WAITING_FOR_DPN);

        save(restoration);
        
        return restoration;
    }


    /**
     * @param restoration
     */
    private void save(Restoration restoration) {
        restorationRepo.save(restoration);
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
     * @return
     */
    private Restoration createRestoration(Snapshot snapshot,
                                          DuracloudEndPointConfig destination)
        throws SnapshotException {
        Restoration restoration = new Restoration();
        restoration.setDestination(destination);
        restoration.setStatus(RestorationStatus.INITIALIZED);
        restoration.setSnapshot(snapshot);
        save(restoration);
        return restoration;
    }

    
    /**
     * @param config2
     * @return
     */
    private String[] getAllEMailAddresses(RestorationManagerConfig config) {
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
        throws RestorationNotFoundException,
            SnapshotException {
        Restoration restoration =  this.restorationRepo.getOne(restorationId);
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
        
        RestorationStatus status = restoration.getStatus();
        
        if(status.equals(RestorationStatus.DPN_TRANSFER_COMPLETE)){
            log.warn("restoration " + restorationId + " already completed. Ignoring...");
            return restoration;
        } else if(status.equals(RestorationStatus.WAITING_FOR_DPN)){
            log.info("caller has indicated that restoration request " + restorationId + " is complete.");
            restoration.setMemo("completed on " + new Date());
            restoration.setStatus(RestorationStatus.DPN_TRANSFER_COMPLETE);
            save(restoration);
            
            this.jobManager.executeRestoration(restoration.getId());
            
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
    public void init(RestorationManagerConfig config) {
        this.config = config;
    }
    
    private String getRestorationContentDir(Long restorationId) {
        String contentDir =
            getRestorationRootDir(restorationId)
                + File.separator + "data";
        return contentDir;
    }
    
    private String getRestorationRootDir(Long restorationId) {
        String contentDir =
            config.getRestorationRootDir()+ File.separator + restorationId;
        return contentDir;
    }

}

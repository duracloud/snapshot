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

import org.duracloud.common.notification.NotificationManager;
import org.duracloud.common.notification.NotificationType;
import org.duracloud.snapshot.SnapshotConstants;
import org.duracloud.snapshot.db.ContentDirUtils;
import org.duracloud.snapshot.db.model.DuracloudEndPointConfig;
import org.duracloud.snapshot.db.model.Restoration;
import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.db.repo.RestoreRepo;
import org.duracloud.snapshot.db.repo.SnapshotRepo;
import org.duracloud.snapshot.dto.RestoreStatus;
import org.duracloud.snapshot.dto.SnapshotStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.JobParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Erik Paulsson
 *         Date: 2/10/14
 */
@Component("jobListener")
public class SnapshotExecutionListener implements JobExecutionListener {

    private static final Logger log =
        LoggerFactory.getLogger(SnapshotExecutionListener.class);

    @Autowired
    private NotificationManager notificationManager;
    
    @Autowired
    private SnapshotRepo snapshotRepo;
    
    @Autowired
    private RestoreRepo restoreRepo;
    
    private ExecutionListenerConfig config;
    
    
    /**
     * @param notificationManager the notificationManager to set
     */
    public void setNotificationManager(NotificationManager notificationManager) {
        this.notificationManager = notificationManager;
    }
    
    
    /**
     * @param restoreRepo the restoreRepo to set
     */
    public void setRestorationRepo(RestoreRepo restoreRepo) {
        this.restoreRepo = restoreRepo;
    }
    
    /**
     * @param snapshotRepo the snapshotRepo to set
     */
    public void setSnapshotRepo(SnapshotRepo snapshotRepo) {
        this.snapshotRepo = snapshotRepo;
    }
    

    public void init(ExecutionListenerConfig config) {
        this.config = config;
    }

    @Transactional
    public void beforeJob(JobExecution jobExecution) {
        JobParameters jobParams = jobExecution.getJobParameters();
        BatchStatus status = jobExecution.getStatus();

        Long objectId = jobParams.getLong(SnapshotConstants.OBJECT_ID);
        String jobName = jobExecution.getJobInstance().getJobName();
       
        if(jobName.equals(SnapshotConstants.SNAPSHOT_JOB_NAME)){
            //For some reason, when using the jpa repos here, the underlying session was
            //always null - no matter what I tried.  It seems to have something to do with 
            //the fact that this code is executed asynchronously in the spring batch generated
            //thread rather than being executed in the web request life cycle. (dbernstein)
            //As a workaround I'm injecting the entitymanager and using it directly.
            
            Snapshot snapshot =  snapshotRepo.getOne(objectId);
            snapshot.setStatus(SnapshotStatus.TRANSFERRING_FROM_DURACLOUD);
            snapshotRepo.save(snapshot);
        }
    }

    @Transactional
    public void afterJob(JobExecution jobExecution) {
        JobParameters jobParams = jobExecution.getJobParameters();
        BatchStatus status = jobExecution.getStatus();

        Long objectId = jobParams.getLong(SnapshotConstants.OBJECT_ID);
        String jobName = jobExecution.getJobInstance().getJobName();
       
        if(jobName.equals(SnapshotConstants.SNAPSHOT_JOB_NAME)){
            //For some reason, when using the jpa repos here, the underlying session was
            //always null - no matter what I tried.  It seems to have something to do with 
            //the fact that this code is executed asynchronously in the spring batch generated
            //thread rather than being executed in the web request life cycle. (dbernstein)
            //As a workaround I'm injecting the entitymanager and using it directly.
            
            Snapshot snapshot = snapshotRepo.getOne(objectId);
            String snapshotName = snapshot.getName();
            String snapshotPath = ContentDirUtils.getDestinationPath(snapshot, config.getContentRoot());
            log.debug("Completed snapshot: {} with status: {}", snapshotName, status);
            handleAfterSnapshotJob(status, snapshot, snapshotPath);
        }else if(jobName.equals(SnapshotConstants.RESTORE_JOB_NAME)){
            Restoration restoration = restoreRepo.findOne(objectId);
            String restorationPath = ContentDirUtils.getSourcePath(restoration.getId(), config.getContentRoot());
            log.debug("Completed restoration: {} with status: {}", restoration.getId(), status);
            handleAfterRestorationJob(status, restoration, restorationPath);
        }
    }

    /**
     * @param status
     * @param snapshotId
     * @param snapshotPath
     */
    private void handleAfterRestorationJob(BatchStatus status,
                                           Restoration restoration,
                                           String snapshotPath) {
        log.debug("Completed restoration: {} with status: {}", restoration.getId(), status);

        Snapshot snapshot = restoration.getSnapshot();
        String snapshotId = snapshot.getName();
        Long restoreId = restoration.getId();

        if(BatchStatus.COMPLETED.equals(status)) {
            // Job success. Email duracloud team as well as restoration requestor
            
            changeRestoreStatus(restoration,
                                RestoreStatus.RESTORATION_COMPLETE,
                                "Completed transfer to duracloud: "
                                    + new Date());
            
            String subject =
                "DuraCloud snapshot " + snapshotId + " has been restored! Restore ID = " + restoreId;
            String message =
                "A DuraCloud snapshot restore  has completed successfully:\n\n";
            
            DuracloudEndPointConfig destination = restoration.getDestination();
            message += "SnapshotId: " + snapshotId + "\n";
            message += "Restore Id: " + restoreId + "\n";
            message += "Destination Host: " + destination.getHost() + "\n";
            message += "Destination Port: " + destination.getPort() + "\n";
            message += "Destination StoreId: " + destination.getStoreId() + "\n";
            message += "Destination SpaceId: " + destination.getSpaceId() + "\n";
            
            List<String> emailAddresses =
                new ArrayList<>(Arrays.asList(config.getDuracloudEmailAddresses()));
            emailAddresses.add(restoration.getUserEmail());
            sendEmail(subject, message, emailAddresses.toArray(new String[0]));
            

        } else {
            // Job failed.  Email DuraSpace team about failed snapshot attempt.
            String subject =
                "DuraCloud snapshot "+ snapshotId + " restoration failed to complete";
            String message =
                "A DuraCloud snapshot restoration has failed to complete.\n" +
                "\nrrestore-id=" + restoreId +
                "\nsnapshot-id=" + snapshotId +
                "\nsnapshot-path=" + snapshotPath;
                // TODO: Add details of failure in message
            sendEmail(subject, message,
                      config.getDuracloudEmailAddresses());

        }
        
    }

    /**
     * @param status
     * @param snapshot
     * @param snapshotPath
     */
    private void handleAfterSnapshotJob(BatchStatus status,
                                        Snapshot snapshot,
                                        String snapshotPath) {
        if(BatchStatus.COMPLETED.equals(status)) {
            // Job success. Email Chronopolis/DPN AND DuraSpace teams about
            // snapshot ready for transfer into preservation storage.
            String subject =
                "DuraCloud content snapshot ready for preservation";
            String message =
                "A DuraCloud content snapshot has been transferred from " +
                "DuraCloud to bridge storage and ready to move into " +
                "preservation storage.\n" +
                "\nsnapshot-id=" + snapshot.getName() +
                "\nsnapshot-path=" + snapshotPath;
            sendEmail(subject, message,
                      config.getAllEmailAddresses());
            
            changeSnapshotStatus(snapshot,SnapshotStatus.WAITING_FOR_DPN,"");
        } else {
            // Job failed.  Email DuraSpace team about failed snapshot attempt.
            String subject =
                "DuraCloud content snapshot failed to complete";
            String message =
                "A DuraCloud content snapshot has failed to complete.\n" +
                "\nsnapshot-id=" + snapshot.getName() +
                "\nsnapshot-path=" + snapshotPath;
                // TODO: Add details of failure in message
            sendEmail(subject, message,
                      config.getDuracloudEmailAddresses());
            changeSnapshotStatus(snapshot,
                                 SnapshotStatus.FAILED_TO_TRANSFER_FROM_DURACLOUD,
                                 "batch job did not complete: batch status = "
                                     + status);            
            
        }
    }

    /**
     * @param snapshot
     * @param status 
     * @param msg
     */
    private void changeSnapshotStatus(Snapshot snapshot,
                                      SnapshotStatus status, String msg) {
        snapshot.setStatus(status);
        snapshot.setStatusText(msg);
        snapshotRepo.save(snapshot);
    }

    /**
     * @param restoration
     * @param status 
     * @param msg
     */
    private void changeRestoreStatus(Restoration restoration,
                                      RestoreStatus status, String msg) {
        restoration.setStatus(status);
        restoration.setStatusText(msg);
        restoreRepo.save(restoration);
    }

    private void sendEmail(String subject, String msg, String... destinations) {
        notificationManager.sendNotification(NotificationType.EMAIL,
                                             subject,
                                             msg.toString(),
                                             destinations);
    }
}

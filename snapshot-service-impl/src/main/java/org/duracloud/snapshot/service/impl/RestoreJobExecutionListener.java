/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.duracloud.common.notification.NotificationManager;
import org.duracloud.common.notification.NotificationType;
import org.duracloud.common.util.DateUtil;
import org.duracloud.snapshot.common.SnapshotServiceConstants;
import org.duracloud.snapshot.db.ContentDirUtils;
import org.duracloud.snapshot.db.model.DuracloudEndPointConfig;
import org.duracloud.snapshot.db.model.Restoration;
import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.db.repo.RestoreRepo;
import org.duracloud.snapshot.dto.RestoreStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.JobParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * @author Erik Paulsson
 *         Date: 2/10/14
 */
@Component("restoreJobListener")
public class RestoreJobExecutionListener implements JobExecutionListener {

    private static final Logger log =
        LoggerFactory.getLogger(SnapshotJobExecutionListener.class);

    @Autowired
    private NotificationManager notificationManager;
    
    @Autowired
    private RestoreRepo restoreRepo;
    
    private ExecutionListenerConfig config;

    private Integer daysToExpire;
    
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

    public void init(ExecutionListenerConfig config, int daysToExpire) {
        this.config = config;
        this.daysToExpire = daysToExpire;
    }

    @Transactional
    public void beforeJob(JobExecution jobExecution) {
    }

    @Transactional
    public void afterJob(JobExecution jobExecution) {
        JobParameters jobParams = jobExecution.getJobParameters();
        BatchStatus status = jobExecution.getStatus();
        String restorationId = RestoreJobParameterMarshaller.unmarshal(jobParams);
        Restoration restoration = restoreRepo.findByRestorationId(restorationId);
        String restorationPath = ContentDirUtils.getSourcePath(restoration.getRestorationId(), config.getContentRoot());
        log.debug("Completed restoration: {} with status: {}", restoration.getRestorationId(), status);

        Snapshot snapshot = restoration.getSnapshot();
        String snapshotId = snapshot.getName();
        String restoreId = restoration.getRestorationId();

        Date currentDate = new Date();

        if(BatchStatus.COMPLETED.equals(status)) {
            try {
                // Job success. Email duracloud team as well as restoration requestor
                Date expirationDate =
                    changeRestoreStatus(restoration,
                                        RestoreStatus.RESTORATION_COMPLETE,
                                        "Completed transfer to duracloud on: " +
                                        currentDate,
                                        currentDate);
                String subject =
                    "DuraCloud snapshot " + snapshotId +
                    " has been restored! Restore ID = " + restoreId;
                String message =
                    "A DuraCloud snapshot restore has completed successfully:\n\n";

                DuracloudEndPointConfig destination = restoration.getDestination();
                message += "SnapshotId: " + snapshotId + "\n";
                message += "Restore Id: " + restoreId + "\n";
                message += "Destination Host: " + destination.getHost() + "\n";
                message += "Destination Port: " + destination.getPort() + "\n";
                message += "Destination StoreId: " + destination.getStoreId() + "\n";
                message += "Destination SpaceId: " + destination.getSpaceId() + "\n";
                message += "\nThe restored content WILL EXPIRE IN " + daysToExpire +
                           " days, on " +
                           DateUtil.convertToStringShort(expirationDate.getTime()) +
                           ". At that time, the contents of the space '" +
                           destination.getSpaceId() +
                           "' will be removed, and the space will be deleted." + "\n";

                log.info("deleting restoration path " + restorationPath);

                try {
                    FileUtils.deleteDirectory(new File(restorationPath));
                } catch (IOException e) {
                    log.error("failed to delete restoration path = "
                              + restorationPath + ": " + e.getMessage(), e);
                }

                List<String> emailAddresses =
                    new ArrayList<>(Arrays.asList(config.getDuracloudEmailAddresses()));
                emailAddresses.add(restoration.getUserEmail());
                sendEmail(subject, message, emailAddresses.toArray(new String[0]));
            } catch(Exception e) {
                handleError(restoration, currentDate, snapshotId, restoreId,
                            restorationPath, e.getMessage());
            }
        } else {
            String errorMessage = "Expected status of (spring batch) restore job to be " +
                                  BatchStatus.COMPLETED + ", but it was " + status +
                                  ". Unable to complete restoration.";
            handleError(restoration, currentDate, snapshotId, restoreId,
                        restorationPath, errorMessage);
        }
    }

    private void handleError(Restoration restoration,
                             Date currentDate,
                             String snapshotId,
                             String restoreId,
                             String restorationPath,
                             String errorMessage) {
        changeRestoreStatus(restoration,
                            RestoreStatus.ERROR,
                            "Failed to transfer to duracloud on: " + currentDate,
                            currentDate);

        // Job failed.  Email DuraSpace team about failed snapshot attempt.
        String subject =
            "DuraCloud snapshot "+ snapshotId + " restoration failed to complete";
        String message =
            "A DuraCloud snapshot restoration has failed to complete.\n" +
            "\nrestore-id=" + restoreId +
            "\nsnapshot-id=" + snapshotId +
            "\nrestore-path=" + restorationPath +
            "\nerror-message=" + errorMessage;
        sendEmail(subject, message,
                  config.getDuracloudEmailAddresses());
    }

    /**
     * Updates the restore details in the database
     *
     * @param restoration the restoration being worked
     * @param status the new status of the restoration
     * @param msg status text
     */
    private Date changeRestoreStatus(Restoration restoration,
                                     RestoreStatus status,
                                     String msg,
                                     Date currentDate) {
        log.info("Changing restore status to: " + status + " with message: " + msg);
        restoration.setStatus(status);
        restoration.setStatusText(msg);
        Date expirationDate = getExpirationDate(currentDate, daysToExpire);
        if(status.equals(RestoreStatus.RESTORATION_COMPLETE)) {
            restoration.setEndDate(currentDate);
            restoration.setExpirationDate(expirationDate);
        }
        restoreRepo.save(restoration);
        return expirationDate;
    }

    /**
     * Calculates the restore expiration date based on the restoration
     * end date and the number of days before retirement
     *
     * @param endDate date on which the restoration completed
     * @param daysToExpire number of days the restored content should stay in place
     *                     before it is retired
     * @return expiration date of restored content
     */
    protected Date getExpirationDate(Date endDate, int daysToExpire) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(endDate);
        calendar.add(Calendar.DATE, daysToExpire);
        return calendar.getTime();
    }

    private void sendEmail(String subject, String msg, String... destinations) {
        notificationManager.sendNotification(NotificationType.EMAIL,
                                             subject,
                                             msg.toString(),
                                             destinations);
        
        log.info("sent email with subject=\""
            + subject + "\" to " + StringUtils.join(destinations, ","));
    }
}

/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import static org.duracloud.snapshot.common.SnapshotServiceConstants.SNAPSHOT_ACTION_STAGED;
import static org.duracloud.snapshot.common.SnapshotServiceConstants.SNAPSHOT_ACTION_TITLE;
import static org.duracloud.snapshot.common.SnapshotServiceConstants.SNAPSHOT_ID_TITLE;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.duracloud.common.notification.NotificationManager;
import org.duracloud.common.notification.NotificationType;
import org.duracloud.snapshot.common.SnapshotServiceConstants;
import org.duracloud.snapshot.db.ContentDirUtils;
import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.db.repo.SnapshotRepo;
import org.duracloud.snapshot.dto.SnapshotStatus;
import org.duracloud.snapshot.service.EventLog;
import org.duracloud.snapshot.service.SnapshotManager;
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
 * Date: 2/10/14
 */
@Component("jobListener")
public class SnapshotJobExecutionListener implements JobExecutionListener {

    private static final Logger log =
        LoggerFactory.getLogger(SnapshotJobExecutionListener.class);

    @Autowired
    private NotificationManager notificationManager;

    @Autowired
    private SnapshotRepo snapshotRepo;

    @Autowired
    private SnapshotManager snapshotManager;

    @Autowired
    private EventLog eventLog;

    private ExecutionListenerConfig config;

    /**
     * @param notificationManager the notificationManager to set
     */
    public void setNotificationManager(NotificationManager notificationManager) {
        this.notificationManager = notificationManager;
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
        log.debug("entering beforeJob()");

        JobParameters jobParams = jobExecution.getJobParameters();

        String snapshotId = SnapshotJobParameterMarshaller.unmarshal(jobParams);
        String jobName = jobExecution.getJobInstance().getJobName();

        if (jobName.equals(SnapshotServiceConstants.SNAPSHOT_JOB_NAME)) {
            SnapshotStatus status = SnapshotStatus.TRANSFERRING_FROM_DURACLOUD;
            log.debug("updating snapshot status to "
                      + status + " for snapshot.name = " + snapshotId
                      + "; jobParameters = " + jobParams);
            Snapshot snapshot = snapshotRepo.findByName(snapshotId);
            changeSnapshotStatus(snapshot, status, "");
        }
    }

    @Transactional
    public void afterJob(JobExecution jobExecution) {
        log.info("entering afterJob()...");
        JobParameters jobParams = jobExecution.getJobParameters();
        BatchStatus status = jobExecution.getStatus();
        String snapshotId = SnapshotJobParameterMarshaller.unmarshal(jobParams);

        Snapshot snapshot = snapshotRepo.findByName(snapshotId);
        String snapshotPath =
            ContentDirUtils.getDestinationPath(snapshot.getName(),
                                               config.getContentRoot());
        log.info("Completed snapshot: {} with status: {}", snapshotId, status);

        if (BatchStatus.COMPLETED.equals(status)) {
            File snapshotDir = new File(snapshotPath);
            snapshot.setTotalSizeInBytes(FileUtils.sizeOfDirectory(snapshotDir));
            // Job success. Email Chronopolis/DPN AND DuraSpace teams about
            // snapshot ready for transfer into preservation storage.
            String subject =
                "DuraCloud content snapshot ready for preservation";
            String message =
                "A DuraCloud content snapshot has been transferred from " +
                "DuraCloud to bridge storage and ready to move into " +
                "preservation storage.\n" +
                "\nsnapshot-id=" + snapshotId +
                "\nsnapshot-path=" + snapshotPath;
            sendEmail(subject, message,
                      config.getAllEmailAddresses());

            changeSnapshotStatus(snapshot, SnapshotStatus.WAITING_FOR_DPN, "");

            // Add history event
            String history =
                "[{'" + SNAPSHOT_ACTION_TITLE + "':'" + SNAPSHOT_ACTION_STAGED + "'}," +
                "{'" + SNAPSHOT_ID_TITLE + "':'" + snapshotId + "'}]";
            snapshotManager.updateHistory(snapshot, history);
        } else {
            // Job failed.  Email DuraSpace team about failed snapshot attempt.
            String subject =
                "DuraCloud content snapshot failed to complete";

            String errorDescription = jobExecution.getExitStatus().getExitDescription();
            String message =
                "A DuraCloud content snapshot has failed to complete.\n" +
                "\nsnapshot-id=" + snapshot.getName() +
                "\nsnapshot-path=" + snapshotPath +
                "\nerror-description=" + errorDescription;

            sendEmail(subject, message,
                      config.getDuracloudEmailAddresses());
            changeSnapshotStatus(snapshot,
                                 SnapshotStatus.FAILED_TO_TRANSFER_FROM_DURACLOUD,
                                 "batch job did not complete: batch status = "
                                 + status);
            log.error("transfer from duracloud failed: " + errorDescription);
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
        eventLog.logSnapshotUpdate(snapshot);
        log.info("Updated status of " + snapshot + " to " + status);
    }

    private void sendEmail(String subject, String msg, String... destinations) {
        try {
            notificationManager.sendNotification(NotificationType.EMAIL,
                                                 subject,
                                                 msg.toString(),
                                                 destinations);
            log.info("sent email with subject=\""
                     + subject + "\" to " + StringUtils.join(destinations, ","));
        } catch (Exception ex) {
            log.error("failed sent email with subject=\""
                      + subject + "\"  and body=\"" + msg + "\"to " + StringUtils.join(destinations, ","));

        }

    }
}

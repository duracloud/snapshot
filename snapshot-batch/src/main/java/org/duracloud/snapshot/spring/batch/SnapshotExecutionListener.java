/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.spring.batch;

import org.duracloud.appconfig.domain.NotificationConfig;
import org.duracloud.common.notification.NotificationManager;
import org.duracloud.common.notification.NotificationType;
import org.duracloud.snapshot.spring.batch.driver.SnapshotConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Erik Paulsson
 *         Date: 2/10/14
 */
public class SnapshotExecutionListener implements JobExecutionListener {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(SnapshotExecutionListener.class);

    private NotificationManager notificationManager;
    private SnapshotConfig snapshotConfig;

    public SnapshotExecutionListener(NotificationManager notificationManager) {
        this.notificationManager = notificationManager;
    }

    public void initialize(SnapshotConfig snapshotConfig) {
        this.snapshotConfig = snapshotConfig;

        // Information needed
//           sesUsername
//           sesPassword
//           originatorEmail
//           duracloudEmails
//           dpnEmails
        // Maybe take in a config set for notification (rather than SnapshotConfig
        NotificationConfig notifyConfig = new NotificationConfig();
        notifyConfig.setType(NotificationType.EMAIL.name());
        notifyConfig.setUsername(snapshotConfig.get);
        notifyConfig.setPassword(snapshotConfig.get);
        notifyConfig.setOriginator(snapshotConfig.get);

        List<NotificationConfig> notifyConfigs = new ArrayList<>();
        notifyConfigs.add(notifyConfig);
        notificationManager.initializeNotifiers(notifyConfigs);
    }

    public void beforeJob(JobExecution jobExecution) {

    }

    public void afterJob(JobExecution jobExecution) {
        String snapshotId = jobExecution.getJobParameters().getString("id");
        // TODO: Is the path value available from job params?
        String snapshotPath = jobExecution.getJobParameters().getString("path");
        LOGGER.debug("Completed snapshot: {} with status: {}",
                     snapshotId, jobExecution.getStatus());
        if(jobExecution.getStatus() == BatchStatus.COMPLETED) {
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
            String[] toAddresses = {""}; // TODO
            sendEmail(subject, message, toAddresses);
        } else {
            // Job failed.  Email DuraSpace team about failed snapshot attempt.
            String subject =
                "DuraCloud content snapshot failed to complete";
            String message =
                "A DuraCloud content snapshot has failed to complete.\n" +
                "\nsnapshot-id=" + snapshotId +
                "\nsnapshot-path=" + snapshotPath +
                "\nerror=" + failureMessage; // TODO
            String[] toAddresses = {""}; // TODO
            sendEmail(subject, message, toAddresses);
        }
    }

    private void sendEmail(String subject, String msg, String... destinations) {
        notificationManager.sendNotification(NotificationType.EMAIL,
                                             subject,
                                             msg.toString(),
                                             destinations);
    }
}

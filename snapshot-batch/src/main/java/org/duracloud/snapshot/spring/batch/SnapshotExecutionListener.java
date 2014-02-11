/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.spring.batch;

import org.duracloud.common.notification.NotificationManager;
import org.duracloud.common.notification.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

/**
 * @author Erik Paulsson
 *         Date: 2/10/14
 */
public class SnapshotExecutionListener implements JobExecutionListener {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(SnapshotExecutionListener.class);

    private NotificationManager notificationManager;

    public SnapshotExecutionListener(NotificationManager notificationManager) {
        this.notificationManager = notificationManager;
    }

    public void beforeJob(JobExecution jobExecution) {

    }

    public void afterJob(JobExecution jobExecution) {
        String snapshotId = jobExecution.getJobParameters().getString("id");
        LOGGER.debug("Completed snapshot: {} with status: {}",
                     snapshotId, jobExecution.getStatus());
        if(jobExecution.getStatus() == BatchStatus.COMPLETED) {
            // Email Chronopolis/DPN AND DuraSpace team about successful snapshot

        } else {
            // Email DuraSpace team about failed snapshot

        }
    }

    private void sendEmail(String subject, String msg, String... destinations) {
        notificationManager.sendNotification(NotificationType.EMAIL,
                                             subject,
                                             msg.toString(),
                                             destinations);
    }
}

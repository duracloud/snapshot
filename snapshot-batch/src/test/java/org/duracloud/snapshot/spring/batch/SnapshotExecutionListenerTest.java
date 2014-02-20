/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.spring.batch;

import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.duracloud.common.notification.NotificationManager;
import org.duracloud.common.notification.NotificationType;
import org.duracloud.snapshot.common.test.SnapshotTestBase;
import org.duracloud.snapshot.spring.batch.config.SnapshotNotifyConfig;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.Mock;
import org.easymock.TestSubject;
import org.junit.Before;
import org.junit.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;

/**
 * @author Bill Branan
 *         Date: 2/18/14
 */
public class SnapshotExecutionListenerTest extends SnapshotTestBase {

    private String snapshotID = "snapshot-id";
    private String contentDir = "content-dir";
    private JobParameters jobParams;

    @Mock
    private NotificationManager notificationManager;
    @Mock
    private SnapshotNotifyConfig notifyConfig;
    @Mock
    private JobExecution jobExecution;

    @TestSubject
    private SnapshotExecutionListener executionListener;

    @Before
    public void setup() {
        super.setup();
        executionListener =
            new SnapshotExecutionListener(notificationManager);

        Map<String, JobParameter> jobParamMap = new HashMap<>();
        jobParamMap.put(SnapshotConstants.SNAPSHOT_ID,
                        new JobParameter(snapshotID));
        jobParamMap.put(SnapshotConstants.CONTENT_DIR,
                        new JobParameter(contentDir));
        jobParams = new JobParameters(jobParamMap);
    }

    @Test
    public void testAfterJobSuccess() {
        String dpnEmail = "dpn-email";
        String duracloudEmail = "duracloud-email";

        EasyMock.expect(jobExecution.getJobParameters())
                .andReturn(jobParams);

        EasyMock.expect(jobExecution.getStatus())
                .andReturn(BatchStatus.COMPLETED);

        Capture<String> messageCapture = new Capture<>();
        notificationManager.sendNotification(
            EasyMock.eq(NotificationType.EMAIL),
            EasyMock.<String>anyObject(),
            EasyMock.capture(messageCapture),
            EasyMock.eq(dpnEmail),
            EasyMock.eq(duracloudEmail));
        EasyMock.expectLastCall();

        EasyMock.expect(notifyConfig.getAllEmailAddresses())
                .andReturn(new String[]{dpnEmail, duracloudEmail});

        replayAll();

        executionListener.afterJob(jobExecution);
        String message = messageCapture.getValue();
        assertTrue(message.contains(snapshotID));
        assertTrue(message.contains(contentDir));
        assertTrue(message.contains("preservation"));
    }

    @Test
    public void testAfterJobFailure() {
        String duracloudEmail = "duracloud-email";

        EasyMock.expect(jobExecution.getJobParameters())
                .andReturn(jobParams);

        EasyMock.expect(jobExecution.getStatus())
                .andReturn(BatchStatus.FAILED);

        Capture<String> messageCapture = new Capture<>();
        notificationManager.sendNotification(
            EasyMock.eq(NotificationType.EMAIL),
            EasyMock.<String>anyObject(),
            EasyMock.capture(messageCapture),
            EasyMock.eq(duracloudEmail));
        EasyMock.expectLastCall();

        EasyMock.expect(notifyConfig.getDuracloudEmailAddresses())
                .andReturn(new String[]{duracloudEmail});

        replayAll();

        executionListener.afterJob(jobExecution);
        String message = messageCapture.getValue();
        assertTrue(message.contains(snapshotID));
        assertTrue(message.contains(contentDir));
        assertTrue(message.contains("failed"));
    }

}

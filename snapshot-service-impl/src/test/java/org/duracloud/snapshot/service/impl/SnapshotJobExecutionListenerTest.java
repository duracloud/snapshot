/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.isA;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.duracloud.common.notification.NotificationManager;
import org.duracloud.common.notification.NotificationType;
import org.duracloud.snapshot.common.SnapshotServiceConstants;
import org.duracloud.snapshot.common.test.SnapshotTestBase;
import org.duracloud.snapshot.db.ContentDirUtils;
import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.db.repo.SnapshotRepo;
import org.duracloud.snapshot.dto.SnapshotStatus;
import org.duracloud.snapshot.service.EventLog;
import org.duracloud.snapshot.service.SnapshotManager;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.easymock.Mock;
import org.easymock.TestSubject;
import org.junit.Before;
import org.junit.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;

/**
 * @author Bill Branan
 * Date: 2/18/14
 */
public class SnapshotJobExecutionListenerTest extends SnapshotTestBase {

    @Mock
    private NotificationManager notificationManager;

    @Mock
    private ExecutionListenerConfig executionConfig;

    @Mock
    private JobExecution jobExecution;

    @Mock
    private SnapshotRepo snapshotRepo;

    @Mock
    private JobInstance job;

    @Mock
    private Snapshot snapshot;

    @Mock
    private SnapshotManager snapshotManager;

    @Mock
    private EventLog eventLog;

    @TestSubject
    private SnapshotJobExecutionListener executionListener = new SnapshotJobExecutionListener();

    private String snapshotId = "snapshot-id";
    private String contentDir = "content-dir";
    private JobParameters jobParams;

    @Before
    public void setup() throws Exception {
        super.setup();

        Map<String, JobParameter> jobParamMap = new HashMap<>();
        jobParamMap.put(SnapshotServiceConstants.SPRING_BATCH_UNIQUE_ID,
                        new JobParameter(snapshotId));
        jobParams = new JobParameters(jobParamMap);
    }

    @Test
    public void testBeforeJob() throws Exception {
        expect(jobExecution.getJobParameters())
            .andReturn(jobParams);
        expect(jobExecution.getJobInstance())
            .andReturn(job);
        expect(job.getJobName())
            .andReturn(SnapshotServiceConstants.SNAPSHOT_JOB_NAME);

        expect(snapshotRepo.findByName(snapshotId))
            .andReturn(snapshot);
        snapshot.setStatus(SnapshotStatus.TRANSFERRING_FROM_DURACLOUD);
        snapshot.setStatusText("");
        expectLastCall();
        expect(snapshotRepo.save(snapshot))
            .andReturn(snapshot);
        ;
        eventLog.logSnapshotUpdate(snapshot);
        expectLastCall();

        replayAll();

        executionListener.beforeJob(jobExecution);
    }

    @Test
    public void testAfterJobSuccess() {
        setupCommon();

        String targetStoreEmail = "target-email";
        String duracloudEmail = "duracloud-email";

        expect(jobExecution.getStatus())
            .andReturn(BatchStatus.COMPLETED);

        Capture<String> messageCapture = Capture.newInstance(CaptureType.FIRST);
        notificationManager.sendNotification(
            EasyMock.eq(NotificationType.EMAIL),
            EasyMock.<String>anyObject(),
            EasyMock.capture(messageCapture),
            EasyMock.eq(targetStoreEmail),
            EasyMock.eq(duracloudEmail));
        expectLastCall();

        expect(executionConfig.getAllEmailAddresses())
            .andReturn(new String[] {targetStoreEmail, duracloudEmail});

        snapshot.setStatus(SnapshotStatus.REPLICATING_TO_STORAGE);
        snapshot.setTotalSizeInBytes(0l);
        expectLastCall();

        Capture<String> historyCapture = Capture.newInstance(CaptureType.FIRST);
        expect(snapshotManager.updateHistory(EasyMock.eq(snapshot),
                                             EasyMock.capture(historyCapture)))
            .andReturn(snapshot);

        replayAll();

        File contentDirFile = new File(contentDir);

        new File(ContentDirUtils.getDestinationPath(snapshotId,
                                                    contentDirFile)).mkdirs();
        executionListener.afterJob(jobExecution);
        String message = messageCapture.getValue();
        assertTrue(message.contains(snapshotId));
        assertTrue(message.contains(contentDir));
        assertTrue(message.contains("preservation"));

        String history = historyCapture.getValue();
        String expectedHistory =
            "[{'snapshot-action':'SNAPSHOT_STAGED'}," +
            "{'snapshot-id':'" + snapshotId + "'}]";
        assertEquals(expectedHistory, history.replaceAll("\\s", ""));

        try {
            FileUtils.deleteDirectory(contentDirFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setupCommon() {
        executionListener.init(executionConfig);

        expect(snapshotRepo.findByName(snapshotId))
            .andReturn(snapshot);

        expect(jobExecution.getJobParameters())
            .andReturn(jobParams);

        expect(executionConfig.getContentRoot())
            .andReturn(new File(contentDir));

        expect(snapshot.getName())
            .andReturn(snapshotId).atLeastOnce();
        snapshot.setStatusText(isA(String.class));
        expectLastCall();

        expect(snapshotRepo.save(snapshot))
            .andReturn(snapshot);
        eventLog.logSnapshotUpdate(snapshot);
        expectLastCall();
    }

    @Test
    public void testAfterJobFailure() {
        setupCommon();

        String duracloudEmail = "duracloud-email";

        expect(jobExecution.getStatus())
            .andReturn(BatchStatus.FAILED);
        expect(jobExecution.getExitStatus())
            .andReturn(ExitStatus.FAILED);

        Capture<String> messageCapture = Capture.newInstance(CaptureType.FIRST);
        notificationManager.sendNotification(
            EasyMock.eq(NotificationType.EMAIL),
            EasyMock.<String>anyObject(),
            EasyMock.capture(messageCapture),
            EasyMock.eq(duracloudEmail));
        expectLastCall();

        expect(executionConfig.getDuracloudEmailAddresses())
            .andReturn(new String[] {duracloudEmail});

        snapshot.setStatus(SnapshotStatus.FAILED_TO_TRANSFER_FROM_DURACLOUD);
        expectLastCall();

        replayAll();

        executionListener.afterJob(jobExecution);
        String message = messageCapture.getValue();
        assertTrue(message.contains(snapshotId));
        assertTrue(message.contains(contentDir));
        assertTrue(message.contains("failed"));
    }

}

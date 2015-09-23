/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import static org.easymock.EasyMock.*;

import java.io.File;
import java.util.ArrayList;

import org.apache.commons.io.FileUtils;
import org.duracloud.client.ContentStore;
import org.duracloud.snapshot.SnapshotConstants;
import org.duracloud.snapshot.SnapshotException;
import org.duracloud.snapshot.SnapshotNotFoundException;
import org.duracloud.snapshot.common.test.SnapshotTestBase;
import org.duracloud.snapshot.db.model.DuracloudEndPointConfig;
import org.duracloud.snapshot.db.model.Restoration;
import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.db.repo.RestoreRepo;
import org.duracloud.snapshot.db.repo.SnapshotRepo;
import org.duracloud.snapshot.dto.task.CompleteCancelSnapshotTaskParameters;
import org.duracloud.snapshot.service.SnapshotJobManagerConfig;
import org.easymock.EasyMock;
import org.easymock.Mock;
import org.easymock.TestSubject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.ApplicationContext;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;


/**
 * @author Daniel Bernstein Date: Feb 19, 2014
 */
public class SnapshotJobManagerImplTest extends SnapshotTestBase {

    private String snapshotName = "test-id";

    @TestSubject
    private SnapshotJobManagerImpl manager;

    @Mock
    private JobExecutionListener snapshotJobListener;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TaskExecutor taskExecutor;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobExecution jobExecution;

    @Mock
    private JobLauncher jobLauncher;

    @Mock
    private Job job;

    @Mock
    private Snapshot snapshot;

    @Mock
    private Restoration restoration;

    @Mock
    private ApplicationContext context;

    @Mock
    private SnapshotJobManagerConfig config;

    @Mock
    private RestoreRepo restoreRepo;

    @Mock
    private SnapshotRepo snapshotRepo;

    @Mock
    private SnapshotJobBuilder snapshotJobBuilder;

    @Mock
    private BatchJobBuilderManager builderManager;

    @Mock
    private StoreClientHelper storeHelper;
    
    @Before
    public void setup() {
        manager =
            new SnapshotJobManagerImpl(snapshotRepo,
                                       restoreRepo,
                                       jobLauncher,
                                       jobRepository,
                                       builderManager,
                                       storeHelper);
        manager.init(config);
    }

    @After
    public void tearDown() {
        verifyAll();
    }

    @Test
    public void testExecuteSnapshot() throws Exception {

        EasyMock.expect(snapshotJobBuilder.buildJob(snapshot, config))
                .andReturn(job);

        EasyMock.expect(snapshotJobBuilder.buildJobParameters(snapshot))
                .andReturn(new JobParameters());

        EasyMock.expect(jobLauncher.run(EasyMock.isA(Job.class),
                                        EasyMock.isA(JobParameters.class)))
                .andReturn(jobExecution);

        EasyMock.expect(jobExecution.getStatus())
                .andReturn(BatchStatus.COMPLETED);

        setupSnapshotRepo();

        setupBuilderManager();

        replayAll();

        manager.executeSnapshot(snapshotName);

    }

    /**
     * 
     */
    private void setupSnapshotRepo() {
        EasyMock.expect(snapshotRepo.findByName(snapshotName))
                .andReturn(snapshot);
    }

    /**
     * 
     */
    private void setupBuilderManager() {
        EasyMock.expect(builderManager.getBuilder(EasyMock.isA(Snapshot.class)))
                .andReturn(snapshotJobBuilder).atLeastOnce();
    }

    @Test
    public void testGetSnapshotStatus()
        throws SnapshotNotFoundException,
            SnapshotException {

        EasyMock.expect(snapshotJobBuilder.buildIdentifyingJobParameters(snapshot))
                .andReturn(new JobParameters());
        
        setupBuilderManager();

        EasyMock.expect(jobRepository.getLastJobExecution(EasyMock.isA(String.class),
                                                          EasyMock.isA(JobParameters.class)))
                .andReturn(jobExecution);

        EasyMock.expect(jobExecution.getStatus())
                .andReturn(BatchStatus.COMPLETED);

        setupSnapshotRepo();

        replayAll();
        Assert.assertNotNull(this.manager.getStatus(snapshotName));
    }

    @Test
    public void testCancelSnapshot() throws Exception {
        setupBuilderManager();
        EasyMock.expect(snapshotJobBuilder.buildIdentifyingJobParameters(snapshot))
        .andReturn(new JobParameters());

        Job job = createMock(Job.class);
        EasyMock.expect(snapshotJobBuilder.buildJob(snapshot, config))
        .andReturn(job);
        

        expect(this.snapshotRepo.findByName(isA(String.class))).andReturn(snapshot).atLeastOnce();
        expect(this.jobRepository.getLastJobExecution(isA(String.class),
                                                      isA(JobParameters.class))).andReturn(jobExecution);

        File rootDir = getTempDir();
        
        FileUtils.forceDeleteOnExit(rootDir);

        expect(config.getContentRootDir()).andReturn(rootDir);
        expect(config.getDuracloudUsername()).andReturn("username");
        expect(config.getDuracloudPassword()).andReturn("password");

        expect(jobExecution.getStatus()).andReturn(BatchStatus.STARTED);
        expect(jobExecution.getStepExecutions()).andReturn(new ArrayList<StepExecution>());
        jobExecution.setStatus(BatchStatus.STOPPING);
        expectLastCall();
        
        jobRepository.update(jobExecution);
        expectLastCall();

        String spaceId = "space-id";
        ContentStore contentStore = createMock(ContentStore.class);
        DuracloudEndPointConfig source = createMock(DuracloudEndPointConfig.class);
        expect(snapshot.getSource()).andReturn(source);
        expect(source.getSpaceId()).andReturn(spaceId);
        expect(this.storeHelper.create(isA(DuracloudEndPointConfig.class),
                                       isA(String.class),
                                       isA(String.class))).andReturn(contentStore);

        CompleteCancelSnapshotTaskParameters params = new CompleteCancelSnapshotTaskParameters();
        params.setSpaceId(spaceId);
        expect(contentStore.performTask(SnapshotConstants.COMPLETE_SNAPSHOT_CANCEL_TASK_NAME, params.serialize())).andReturn("test");

        replayAll();

        this.manager.cancelSnapshot(snapshotName);
        
        Thread.sleep(1000);
    }
}

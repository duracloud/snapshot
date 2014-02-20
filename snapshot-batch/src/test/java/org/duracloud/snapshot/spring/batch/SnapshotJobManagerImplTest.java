/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.spring.batch;

import org.duracloud.snapshot.common.test.SnapshotTestBase;
import org.duracloud.snapshot.spring.batch.config.SnapshotConfig;
import org.duracloud.snapshot.spring.batch.config.SnapshotJobManagerConfig;
import org.easymock.Capture;
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
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.ApplicationContext;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * @author Daniel Bernstein Date: Feb 19, 2014
 */
public class SnapshotJobManagerImplTest extends SnapshotTestBase {

    private final String SNAPSHOT_ID = "test-id";

    @TestSubject
    private SnapshotJobManagerImpl manager;

    @Mock
    private JobExecutionListener jobListener;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TaskExecutor taskExecutor;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobLauncher jobLauncher;

    @Mock
    private JobExecution jobExecution;

    @Mock
    private SnapshotJobBuilder jobBuilder;

    @Mock
    private Job job;

    @Mock
    private SnapshotConfig snapshotConfig;

    @Mock
    private ApplicationContext context;

    @Mock
    private SnapshotJobManagerConfig config;

    @Before
    public void setup() {
        manager =
            new SnapshotJobManagerImpl(jobListener,
                                       transactionManager,
                                       taskExecutor);
        manager.setApplicationContext(context);
    }

    @After
    public void tearDown() {
        verifyAll();
    }

    /*
     * @Test public void testInit() {
     * EasyMock.expect(context.getBean(SnapshotJobManager
     * .JOB_REPOSITORY_KEY)).andReturn(jobRepository);
     * EasyMock.expect(context.getBean
     * (SnapshotJobManager.JOB_LAUNCHER_KEY)).andReturn(jobLauncher);
     * replayAll();
     * 
     * //somehow the private fields (jobRepository and jobManager) are getting
     * //set by the test framework - so initialize can't be tested.
     * 
     * manager.init(config);
     * 
     * }
     */

    @Test
    public void testExecuteSnapshot() throws Exception {
        EasyMock.expect(snapshotConfig.getSnapshotId())
                .andReturn(SNAPSHOT_ID)
                .times(2);
        EasyMock.expect(snapshotConfig.getContentDir()).andReturn(null);
        setupContentRootDir();

        EasyMock.expect(jobBuilder.build(snapshotConfig,
                                         config,
                                         jobListener,
                                         jobRepository,
                                         jobLauncher,
                                         transactionManager,
                                         taskExecutor)).andReturn(job);

        Capture<JobParameters> jobParams = new Capture<>();

        EasyMock.expect(jobLauncher.run(EasyMock.isA(Job.class),
                                        EasyMock.capture(jobParams)))
                .andReturn(jobExecution);

        EasyMock.expect(jobExecution.getStatus())
                .andReturn(BatchStatus.COMPLETED);
        replayAll();

        manager.executeSnapshot(snapshotConfig);

        JobParameters jobParameters = jobParams.getValue();

        Assert.assertEquals(SNAPSHOT_ID,
                            jobParameters.getString(SnapshotConstants.SNAPSHOT_ID));
        Assert.assertTrue(jobParameters.getString(SnapshotConstants.CONTENT_DIR)
                                       .endsWith(SNAPSHOT_ID));

    }

    private void setupContentRootDir() {
        EasyMock.expect(config.getContentRootDir()).andReturn(getTempDir());
    }
    
    @Test
    public void testGetSnapshotStatus() throws SnapshotNotFoundException, SnapshotException{
        setupContentRootDir();
        EasyMock.expect(jobExecution.getStatus()).andReturn(BatchStatus.COMPLETED);
        EasyMock.expect(jobRepository.getLastJobExecution(EasyMock.isA(String.class),
                                                          EasyMock.isA(JobParameters.class)))
                .andReturn(jobExecution);
        replayAll();
        Assert.assertNotNull(this.manager.getStatus(SNAPSHOT_ID));
    }
}

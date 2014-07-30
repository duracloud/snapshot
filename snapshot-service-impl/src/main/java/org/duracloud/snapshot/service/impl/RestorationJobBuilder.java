/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.duracloud.client.ContentStore;
import org.duracloud.retrieval.util.StoreClientUtil;
import org.duracloud.snapshot.SnapshotConstants;
import org.duracloud.snapshot.SnapshotException;
import org.duracloud.snapshot.db.ContentDirUtils;
import org.duracloud.snapshot.db.model.DuracloudEndPointConfig;
import org.duracloud.snapshot.db.model.Restoration;
import org.duracloud.snapshot.db.repo.RestoreRepo;
import org.duracloud.snapshot.dto.RestoreStatus;
import org.duracloud.snapshot.service.SnapshotJobManagerConfig;
import org.duracloud.sync.endpoint.DuraStoreSyncEndpoint;
import org.duracloud.sync.endpoint.SyncEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.builder.SimpleJobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.factory.SimpleStepFactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * @author Daniel Bernstein 
 *         Date: Feb 19, 2014
 */
@Component
public class RestorationJobBuilder implements BatchJobBuilder<Restoration> {
    private static Logger log = LoggerFactory.getLogger(RestorationJobBuilder.class);

    private JobExecutionListener jobListener;
    private JobRepository jobRepository;
    private PlatformTransactionManager transactionManager;
    private TaskExecutor taskExecutor;

    private RestoreRepo restoreRepo;
    
    @Autowired
    public RestorationJobBuilder(JobExecutionListener jobListener, 
                              JobRepository jobRepository,
                              PlatformTransactionManager transactionManager, 
                              TaskExecutor taskExecutor,
                              RestoreRepo restoreRepo) {

        this.jobListener = jobListener;
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.taskExecutor = taskExecutor;
        this.restoreRepo = restoreRepo;
    }
    
    /* (non-Javadoc)
     * @see org.duracloud.snapshot.manager.spring.batch.BatchJobBuilder#buildJob(java.lang.Object, org.duracloud.snapshot.manager.config.SnapshotJobManagerConfig)
     */
    @Override
    public Job buildJob(Restoration restoration, SnapshotJobManagerConfig jobManagerConfig)
        throws SnapshotException {
         Job job;
        
        DuracloudEndPointConfig destination = restoration.getDestination();
        try {
            StoreClientUtil clientUtil = new StoreClientUtil();

            ContentStore contentStore =
                clientUtil.createContentStore(destination.getHost(),
                                              destination.getPort(),
                                              SnapshotConstants.DURASTORE_CONTEXT,
                                              jobManagerConfig.getDuracloudUsername(),
                                              jobManagerConfig.getDuracloudPassword(),
                                              destination.getStoreId());

            SyncEndpoint endpoint =
                new DuraStoreSyncEndpoint(contentStore,
                                          jobManagerConfig.getDuracloudUsername(),
                                          destination.getSpaceId(),
                                          false);
            
            File watchDir =
                new File(ContentDirUtils.getSourcePath(restoration.getId(),
                                                       jobManagerConfig.getContentRootDir()));

            FileSystemReader reader =
                new FileSystemReader(watchDir);

            SyncWriter writer =
                new SyncWriter(watchDir,
                               endpoint,
                               contentStore,
                               destination.getSpaceId());

            SimpleStepFactoryBean<File, File> stepFactory =
                new SimpleStepFactoryBean<>();
            stepFactory.setJobRepository(jobRepository);
            stepFactory.setTransactionManager(transactionManager);
            stepFactory.setBeanName("step1");
            stepFactory.setItemReader(reader);
            stepFactory.setItemWriter(writer);
            stepFactory.setCommitInterval(1);
            stepFactory.setThrottleLimit(20);
            stepFactory.setTaskExecutor(taskExecutor);
            Step step = (Step) stepFactory.getObject();

            JobBuilderFactory jobBuilderFactory =
                new JobBuilderFactory(jobRepository);
            JobBuilder jobBuilder = jobBuilderFactory.get(SnapshotConstants.RESTORE_JOB_NAME);
            SimpleJobBuilder simpleJobBuilder = jobBuilder.start(step);
            simpleJobBuilder.listener(jobListener);
            job = simpleJobBuilder.build();
            
            restoration.setStatus(RestoreStatus.TRANSFERRING_TO_DURACLOUD);
            restoration.setStatusText("Ready to being transferring to duracloud: "
                + new Date());
            this.restoreRepo.save(restoration);
       } catch (Exception e) {
            log.error("Error creating job: {}", e.getMessage(), e);
            throw new SnapshotException(e.getMessage(), e);
        }
        return job;
    }
    
    /* (non-Javadoc)
     * @see org.duracloud.snapshot.manager.spring.batch.BatchJobBuilder#buildIdentifyingJobParameters(java.lang.Object)
     */
    @Override
    public JobParameters buildIdentifyingJobParameters(Restoration restoration) {
            Map<String, JobParameter> map = new HashMap<>();
            map.put(SnapshotConstants.OBJECT_ID, new JobParameter(restoration.getId(), true));
            return new JobParameters(map);
    }
    
    /* (non-Javadoc)
     * @see org.duracloud.snapshot.manager.spring.batch.BatchJobBuilder#buildJobParameters(java.lang.Object)
     */
    @Override
    public JobParameters buildJobParameters(Restoration entity) {
        return buildIdentifyingJobParameters(entity);
    }
}

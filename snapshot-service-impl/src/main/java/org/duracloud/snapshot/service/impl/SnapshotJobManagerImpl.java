/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import java.io.File;
import java.text.MessageFormat;
import java.util.Date;

import org.apache.commons.io.FileUtils;
import org.duracloud.client.ContentStore;
import org.duracloud.common.retry.Retriable;
import org.duracloud.common.retry.Retrier;
import org.duracloud.snapshot.SnapshotConstants;
import org.duracloud.snapshot.SnapshotException;
import org.duracloud.snapshot.SnapshotNotFoundException;
import org.duracloud.snapshot.common.SnapshotServiceConstants;
import org.duracloud.snapshot.db.ContentDirUtils;
import org.duracloud.snapshot.db.model.BaseEntity;
import org.duracloud.snapshot.db.model.DuracloudEndPointConfig;
import org.duracloud.snapshot.db.model.Restoration;
import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.db.repo.RestoreRepo;
import org.duracloud.snapshot.db.repo.SnapshotRepo;
import org.duracloud.snapshot.dto.SnapshotStatus;
import org.duracloud.snapshot.dto.task.CompleteCancelSnapshotTaskParameters;
import org.duracloud.snapshot.service.AlreadyInitializedException;
import org.duracloud.snapshot.service.RestorationNotFoundException;
import org.duracloud.snapshot.service.SnapshotJobManager;
import org.duracloud.snapshot.service.SnapshotJobManagerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.launch.JobExecutionNotRunningException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.NoSuchJobExecutionException;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.core.step.NoSuchStepException;
import org.springframework.batch.core.step.StepLocator;
import org.springframework.batch.core.step.tasklet.StoppableTasklet;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.core.step.tasklet.TaskletStep;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The default implementation of the <code>SnapshotJobManager</code> interface.
 * Uses spring-batch componentry.
 * 
 * @author Daniel Bernstein Date: Feb 11, 2014
 */
@Component
public class SnapshotJobManagerImpl implements SnapshotJobManager {

    private static final Logger log = LoggerFactory.getLogger(SnapshotJobManagerImpl.class);
    private JobLauncher jobLauncher;
    private JobRepository jobRepository;
    private SnapshotRepo snapshotRepo;
    private RestoreRepo restoreRepo;
    private SnapshotJobManagerConfig config;
    private BatchJobBuilderManager builderManager;
    private StoreClientHelper storeClientHelper;

    @Autowired
    public SnapshotJobManagerImpl(
        SnapshotRepo snapshotRepo, RestoreRepo restoreRepo, JobLauncher jobLauncher, JobRepository jobRepository,
        BatchJobBuilderManager manager, StoreClientHelper storeClientHelper) {
        super();
        this.restoreRepo = restoreRepo;
        this.snapshotRepo = snapshotRepo;
        this.builderManager = manager;
        this.jobLauncher = jobLauncher;
        this.jobRepository = jobRepository;
        this.storeClientHelper = storeClientHelper;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.duracloud.snapshot.rest.SnapshotJobManager#initialize(org.duracloud
     * .snapshot.rest.InitParams)
     */
    @Override
    public void init(SnapshotJobManagerConfig config) throws AlreadyInitializedException {
        init(config, true);
    }

    protected void init(SnapshotJobManagerConfig config,
                        boolean attemptRestart) throws AlreadyInitializedException {
        if (isInitialized()) {
            throw new AlreadyInitializedException("Already initialized!");
        }

        this.config = config;

        log.info("initialized successfully.");

        if(attemptRestart) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        restartIncompleteJobs();
                    } catch (SnapshotException e) {
                        log.error(
                            "failed to restart all incomplete jobs:" + e.getMessage(), e);
                    }
                }
            }).start();
        }
    }

    /**
     * 
     */
    private void restartIncompleteJobs() throws SnapshotException {
        log.info("checking for incomplete snapshot jobs.");

        for (Snapshot snapshot : this.snapshotRepo.findByStatus(SnapshotStatus.TRANSFERRING_FROM_DURACLOUD)) {
            resumeJob(SnapshotServiceConstants.SNAPSHOT_JOB_NAME, snapshot);
        }

        log.info("checking for incomplete restore jobs.");
        for (Restoration restore : this.restoreRepo.findRunning()) {
            resumeJob(SnapshotServiceConstants.RESTORE_JOB_NAME, restore);
        }
    }

    private void resumeJob(String jobName, Object entity) throws SnapshotException {

        BatchJobBuilder builder = this.builderManager.getBuilder(entity);
        Job job = builder.buildJob(entity, config);
        JobParameters params = builder.buildIdentifyingJobParameters(entity);
        JobExecution jobExecution = this.jobRepository.getLastJobExecution(jobName, params);


        if (jobExecution != null) {
            if(!jobExecution.getStatus().isRunning()){
                return;
            }else{
                log.debug("found job execution in running state for {} (job execution = {})", entity, jobExecution);
                jobExecution.setStatus(BatchStatus.STOPPED);
                jobExecution.setEndTime(new Date());
                jobRepository.update(jobExecution);
                log.info("updated job execution in running state to stopped: {} (job execution = {})",
                         entity,
                         jobExecution);
            }
        } 

        try {
            JobExecution execution = jobLauncher.run(job, params);
            log.info("restarted job execution = {} for {}:  newly executed job execution id = {}",
                     execution,
                     entity,
                     execution.getId());
        } catch (JobExecutionAlreadyRunningException | JobInstanceAlreadyCompleteException | JobRestartException
            | JobParametersInvalidException e) {
            log.error(MessageFormat.format("failed to resume stopped job: jobExecution={0}, entity={1}: {2}",
                                           jobExecution,
                                           entity,
                                           e.getMessage()),
                      e);
        }

    }

    /* (non-Javadoc)
     * @see org.duracloud.snapshot.service.SnapshotJobManager#isInitialized()
     */
    @Override
    public boolean isInitialized() {
        return this.config != null;
    }

    private Snapshot getSnapshot(String snapshotId) throws SnapshotNotFoundException {
        Snapshot snapshot = this.snapshotRepo.findByName(snapshotId);
        if (snapshot == null) {
            throw new SnapshotNotFoundException(snapshotId);
        }

        return snapshot;
    }

    @SuppressWarnings("unchecked")
    private BatchStatus executeJob(Object entity) throws SnapshotException {

        log.debug("executing job for {}", entity);
        try {
            @SuppressWarnings("rawtypes")
            BatchJobBuilder builder = this.builderManager.getBuilder(entity);
            Job job = builder.buildJob(entity, config);
            JobParameters params = builder.buildJobParameters(entity);
            JobExecution execution = jobLauncher.run(job, params);
            BatchStatus status = execution.getStatus();
            log.info("executed  {} using parameters {}: jobexecution={}, execution status={}",
                     job,
                     params,
                     execution,
                     status);
            return status;
        } catch (Exception e) {
            String message = "Error running job based on " + entity + ": " + e.getMessage();
            log.error(message, e);
            throw new SnapshotException(e.getMessage(), e);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.duracloud.snapshot.manager.SnapshotJobManager#executeRestoration(java
     * .lang.String)
     */
    @Override
    public BatchStatus executeRestoration(String restorationId) throws SnapshotException {
        return executeJob(getRestoration(restorationId));
    }

    /**
     * @param restorationId
     * @return
     */
    private Restoration getRestoration(String restorationId) throws RestorationNotFoundException {
        Restoration restoration = this.restoreRepo.findByRestorationId(restorationId);
        if (restoration == null) {
            throw new RestorationNotFoundException(restorationId);
        }
        return restoration;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.duracloud.snapshot.manager.SnapshotJobManager#executeSnapshot(java.
     * lang.String)
     */
    @Override
    public BatchStatus executeSnapshot(String snapshotId) throws SnapshotException {
        checkInitialized();
        return executeJob(getSnapshot(snapshotId));
    }

    private boolean stop(JobExecution jobExecution, Job job) throws NoSuchJobExecutionException, JobExecutionNotRunningException {

        BatchStatus status = jobExecution.getStatus();
        if (!(status == BatchStatus.STARTED || status == BatchStatus.STARTING)) {
            throw new JobExecutionNotRunningException("JobExecution must be running so that it can be stopped: "+jobExecution);
        }
        jobExecution.setStatus(BatchStatus.STOPPING);
        jobRepository.update(jobExecution);
        //get the current stepExecution
        for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
            if (stepExecution.getStatus().isRunning()) {
                try {
                    //have the step execution that's running -> need to 'stop' it
                    Step step = ((StepLocator)job).getStep(stepExecution.getStepName());
                    if (step instanceof TaskletStep) {
                        Tasklet tasklet = ((TaskletStep)step).getTasklet();
                        if (tasklet instanceof StoppableTasklet) {
                            StepSynchronizationManager.register(stepExecution);
                            ((StoppableTasklet)tasklet).stop();
                            StepSynchronizationManager.release();
                        }
                    }
                }
                catch (NoSuchStepException e) {
                    log.warn("Step not found",e);
                }
            }
        }
        return true;
    }
    /*
     * (non-Javadoc)
     * 
     * @see
     * org.duracloud.snapshot.service.SnapshotJobManager#cancelSnapshot(java.
     * lang.String)
     */
    @Override
    @Transactional
    public void cancelSnapshot(String snapshotId) throws SnapshotException {
        checkInitialized();
        final Snapshot snapshot = getSnapshot(snapshotId);
        stop(snapshot);
        String snapshotDir = ContentDirUtils.getDestinationPath(snapshotId, this.config.getContentRootDir());
        deleteDirectory(snapshotDir);

        new Thread(new Runnable(){
            @Override
            public void run() {
                final DuracloudEndPointConfig source = snapshot.getSource();
                final String spaceId = source.getSpaceId();
                final ContentStore contentStore =
                    storeClientHelper.create(source,
                                                  config.getDuracloudUsername(),
                                                  config.getDuracloudPassword());
                try {
                    String result = new Retrier().execute(new Retriable(){
                        /* (non-Javadoc)
                         * @see org.duracloud.common.retry.Retriable#retry()
                         */
                        @Override
                        public Object retry() throws Exception {
                            CompleteCancelSnapshotTaskParameters params = 
                                new CompleteCancelSnapshotTaskParameters();
                            params.setSpaceId(spaceId);
                            return contentStore.performTask(SnapshotConstants.COMPLETE_SNAPSHOT_CANCEL_TASK_NAME, 
                                                     params.serialize());
                        }
                    });
                    
                    log.info("snapshot cancellation is complete: {}", result);

                }catch(Exception ex){
                    log.error("failed to complete cancellation on the durastore side for space {}:  {}",
                              spaceId,
                              ex.getMessage(),
                              ex);
                }
                
            }
        }).start();
    }

    @Transactional
    @Override
    public void stopRestore(String restoreId) throws SnapshotException {
        _stopRestore(restoreId);
    }
    
    @Transactional
    public void cancelRestore(String restoreId) throws SnapshotException {
        checkInitialized();
        final Restoration restore = _stopRestore(restoreId);
        String restoreDir = ContentDirUtils.getSourcePath(restoreId, this.config.getContentRootDir());
        deleteDirectory(restoreDir);
        final DuracloudEndPointConfig destination = restore.getDestination();
        
        new Thread(new Runnable(){
            @Override
            public void run() {
                final String spaceId = destination.getSpaceId();
                @SuppressWarnings("unused")
                final ContentStore contentStore =
                    storeClientHelper.create(destination,
                                                  config.getDuracloudUsername(),
                                                  config.getDuracloudPassword());
                try {
                    String result = new Retrier().execute(new Retriable(){
                        @Override
                        public Object retry() throws Exception {
                            contentStore.deleteSpace(spaceId);
                            return null;
                        }
                    });
                    
                    log.info("restore cancellation is complete: {}", result);

                }catch(Exception ex){
                    log.error("failed to delete restore space {} as part of cleanup:  {}",
                              spaceId,
                              ex.getMessage(),
                              ex);
                }
                
            }
        }).start();
    }

    /**
     * @param restoreId
     * @return
     * @throws RestorationNotFoundException
     */
    private Restoration _stopRestore(String restoreId) throws SnapshotException {
        final Restoration restore = getRestoration(restoreId);
        stop(restore);
        return restore;
    }

    /**
     * @param entity
     * @throws SnapshotException
     */
    private void stop(final BaseEntity entity) throws SnapshotException {
        JobExecution execution = getJobExecution(entity);
        if(execution == null){
            log.info("no job executions associated with {}", entity);
            return;
        }
        Job job = this.builderManager.getBuilder(entity)
                                     .buildJob(entity, this.config);
        try {
            stop(execution, job);
        } catch (NoSuchJobExecutionException e1) {
            log.warn("job execution not found: " + e1.getMessage());
        } catch (JobExecutionNotRunningException e1) {
            log.warn("job execution not running: " + e1.getMessage());
        }
    }
  /**
     * @param path
     */
    private void deleteDirectory(String path) {
        log.info("deleting restore dir: {}", path);
        File dir = new File(path);
        if (!dir.exists()) {
            log.info("nothing to delete: {} does not exist.", path);
        } else {
            boolean success = FileUtils.deleteQuietly(dir);
            log.info("deleted dir {}: success={}", path, success);
        }
    }

 
    /**
     * 
     */
    private void checkInitialized() throws SnapshotException {
        if (!isInitialized()) {
            throw new SnapshotException("The application must be initialized " + "before it can be invoked!", null);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.duracloud.snapshot.rest.SnapshotJobManager#getJobExecution(java.lang
     * .String)
     */
    @Override
    public BatchStatus getStatus(String snapshotId) throws SnapshotNotFoundException, SnapshotException {

        checkInitialized();
        JobExecution ex = getJobExecution(this.snapshotRepo.findByName(snapshotId));
        if (ex == null) {
            return BatchStatus.UNKNOWN;
        } else {
            return ex.getStatus();
        }
    }

    /**
     * @param snapshotId
     * @return
     */
    private JobExecution getJobExecution(BaseEntity entity) {
        BatchJobBuilder builder = this.builderManager.getBuilder(entity);
        JobParameters params = builder.buildIdentifyingJobParameters(entity);
        String jobName = builder.getJobName();
        JobExecution ex = this.jobRepository.getLastJobExecution(jobName, params);
        return ex;
    }
    
    
    
}

/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import java.text.MessageFormat;
import java.util.Date;

import org.duracloud.snapshot.SnapshotException;
import org.duracloud.snapshot.common.SnapshotServiceConstants;
import org.duracloud.snapshot.db.model.Restoration;
import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.db.repo.RestoreRepo;
import org.duracloud.snapshot.db.repo.SnapshotRepo;
import org.duracloud.snapshot.dto.SnapshotStatus;
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
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.amazonaws.services.elasticache.model.SnapshotNotFoundException;

/**
 * The default implementation of the <code>SnapshotJobManager</code> interface.
 * Uses spring-batch componentry.
 * 
 * @author Daniel Bernstein 
 *         Date: Feb 11, 2014
 */
@Component
public class SnapshotJobManagerImpl
    implements SnapshotJobManager{

    private static final Logger log =
        LoggerFactory.getLogger(SnapshotJobManagerImpl.class);
    private JobLauncher jobLauncher;
    private JobRepository jobRepository;
    private SnapshotRepo snapshotRepo;
    private RestoreRepo restoreRepo;
    private SnapshotJobManagerConfig config;
    private BatchJobBuilderManager builderManager;
    
    @Autowired
    public SnapshotJobManagerImpl(SnapshotRepo snapshotRepo,
                                    RestoreRepo restoreRepo,
                                    JobLauncher jobLauncher,
                                    JobRepository jobRepository,
                                    BatchJobBuilderManager manager) {
        super();
        this.restoreRepo = restoreRepo;
        this.snapshotRepo = snapshotRepo;
        this.builderManager = manager;
        this.jobLauncher = jobLauncher;
        this.jobRepository = jobRepository;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.duracloud.snapshot.rest.SnapshotJobManager#initialize(org.duracloud
     * .snapshot.rest.InitParams)
     */
    @Override
    public void init(SnapshotJobManagerConfig config) {
        
        if (isInitialized()) {
            log.warn("Already initialized. Ignorning");
            return;
        }

        this.config = config;
        
        log.info("initialized successfully.");
        
        new Thread(new Runnable(){
            @Override
            public void run() {
                try {
                    restartIncompleteJobs();
                } catch (SnapshotException e) {
                    log.error("failed to restart all incomplete jobs:" + e.getMessage(), e);
                }
            }
        }).start();
    }

    /**
     * 
     */
    private void restartIncompleteJobs() throws SnapshotException {
        log.info("checking for incomplete snapshot jobs.");
        
        for(Snapshot snapshot  : this.snapshotRepo.findByStatus(SnapshotStatus.TRANSFERRING_FROM_DURACLOUD)){
            resumeJob(SnapshotServiceConstants.SNAPSHOT_JOB_NAME, snapshot);
        }

        log.info("checking for incomplete restore jobs.");
        for(Restoration restore  : this.restoreRepo.findRunning()){
            resumeJob(SnapshotServiceConstants.RESTORE_JOB_NAME, restore);
        }
    }


    /**
     * @param snapshot
     */
    private void resumeJob(String jobName, Object entity) throws SnapshotException {

        BatchJobBuilder builder = this.builderManager.getBuilder(entity);
        Job job = builder.buildJob(entity, config);
        JobParameters params = builder.buildIdentifyingJobParameters(entity);
        JobExecution jobExecution =
            this.jobRepository.getLastJobExecution(jobName, params);
        
        BatchStatus status = jobExecution.getStatus();
        
        if(!status.isRunning()){
            return;
        }else{
            log.debug("found job execution in running state for {} (job execution = {})", entity, jobExecution);
            jobExecution.setStatus(BatchStatus.STOPPED);
            jobExecution.setEndTime(new Date());
            jobRepository.update(jobExecution);
            log.info("updated job execution in running state to stopped: {} (job execution = {})", entity, jobExecution);
        }  
        
        try {
            JobExecution execution = jobLauncher.run(job, params);
            log.info("restarted job execution = {} for {}:  newly executed job execution id = {}",
                     execution,
                     entity,
                     execution.getId());
        } catch (JobExecutionAlreadyRunningException | JobInstanceAlreadyCompleteException 
            | JobRestartException | JobParametersInvalidException e) {
            log.error(MessageFormat.format("failed to resume stopped job: jobExecution={0}, entity={1}: {2}",
                                           jobExecution,
                                           entity,
                                           e.getMessage()),
                      e);
        }
        
    }

    /**
     * 
     */
    private boolean isInitialized() {
        return this.config != null;
    }

    private Snapshot getSnapshot(String snapshotId) throws SnapshotNotFoundException{
        Snapshot snapshot = this.snapshotRepo.findByName(snapshotId);
        if(snapshot == null){
            throw new SnapshotNotFoundException(snapshotId);
        }
        
        return snapshot;
    }

     @SuppressWarnings("unchecked")
    private BatchStatus executeJob(Object entity)
        throws SnapshotException {
         
         log.debug("executing job for {}",entity);
         try {         
            @SuppressWarnings("rawtypes")
            BatchJobBuilder builder = this.builderManager.getBuilder(entity);   
            Job job = builder.buildJob(entity, config);
            JobParameters params = builder.buildJobParameters(entity);
            JobExecution execution = jobLauncher.run(job, params);
            BatchStatus status =  execution.getStatus();
            log.info("executed  {} using parameters {}: jobexecution={}, execution status={}",
                     job,
                     params,
                     execution,
                     status);
            return status;
        } catch (Exception e) {
            String message =
                "Error running job based on " + entity + ": " + e.getMessage();
            log.error(message, e);
            throw new SnapshotException(e.getMessage(), e);
        }
    }

    /* (non-Javadoc)
     * @see org.duracloud.snapshot.manager.SnapshotJobManager#executeRestoration(java.lang.String)
     */
    @Override
    public BatchStatus executeRestoration(String restorationId)
        throws SnapshotException {
        return executeJob(getRestoration(restorationId));
    }

    /**
     * @param restorationId
     * @return
     */
    private Restoration getRestoration(String restorationId)
        throws RestorationNotFoundException {
        Restoration restoration = this.restoreRepo.findByRestorationId(restorationId);
        if(restoration == null){
            throw new RestorationNotFoundException(restorationId);
        }
        return restoration;
    }

    /* (non-Javadoc)
     * @see org.duracloud.snapshot.manager.SnapshotJobManager#executeSnapshot(java.lang.String)
     */
    @Override
    public BatchStatus executeSnapshot(String snapshotId)
        throws SnapshotException {
        checkInitialized();
        return executeJob(getSnapshot(snapshotId));
    }
 
    /**
     * 
     */
    private void checkInitialized() throws SnapshotException {
        if (!isInitialized()) {
            throw new SnapshotException("The application must be initialized "
                + "before it can be invoked!", null);
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
    public BatchStatus getStatus(String snapshotId)
        throws SnapshotNotFoundException,
            SnapshotException {

        checkInitialized();
        JobExecution ex = getJobExecution(snapshotId);
        if (ex == null) {
            return BatchStatus.UNKNOWN;
        }else{
            return ex.getStatus();
        }
    }



    /**
     * @param snapshotId
     * @return
     */
    private JobExecution getJobExecution(String snapshotId) {
        Snapshot snapshot = getSnapshot(snapshotId);
        BatchJobBuilder builder = this.builderManager.getBuilder(snapshot);
        JobParameters params = builder.buildIdentifyingJobParameters(snapshot);
        JobExecution ex =
            this.jobRepository.getLastJobExecution(SnapshotServiceConstants.SNAPSHOT_JOB_NAME, params);
        return ex;
    }
}

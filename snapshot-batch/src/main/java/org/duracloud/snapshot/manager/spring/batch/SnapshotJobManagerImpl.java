/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.manager.spring.batch;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.duracloud.snapshot.db.model.Restoration;
import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.db.repo.RestorationRepo;
import org.duracloud.snapshot.db.repo.SnapshotRepo;
import org.duracloud.snapshot.manager.SnapshotConstants;
import org.duracloud.snapshot.manager.SnapshotException;
import org.duracloud.snapshot.manager.SnapshotJobManager;
import org.duracloud.snapshot.manager.SnapshotNotFoundException;
import org.duracloud.snapshot.manager.config.SnapshotJobManagerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The default implementation of the <code>SnapshotJobManager</code> interface.
 * Uses spring-batch componentry.
 * 
 * @author Daniel Bernstein Date: Feb 11, 2014
 */
@Component
public class SnapshotJobManagerImpl
    implements SnapshotJobManager{

    private static final Logger log =
        LoggerFactory.getLogger(SnapshotJobManagerImpl.class);
    private JobLauncher jobLauncher;
    private JobRepository jobRepository;

    private ApplicationContext context;
    private SnapshotRepo snapshotRepo;
    private RestorationRepo restorationRepo;
    private SnapshotJobManagerConfig config;
    private BatchJobBuilderManager builderManager;
    
    @Autowired
    public SnapshotJobManagerImpl(SnapshotRepo snapshotRepo,
                                    RestorationRepo restorationRepo,
                                    JobLauncher jobLauncher,
                                    JobRepository jobRepository,
                                    BatchJobBuilderManager manager) {
        super();
        this.restorationRepo = restorationRepo;
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
         try {         
            @SuppressWarnings("rawtypes")
            BatchJobBuilder builder = this.builderManager.getBuilder(entity);   
            Job job = builder.buildJob(entity, config);
            JobParameters params = builder.buildJobParameters(entity);
            JobExecution execution = jobLauncher.run(job, params);
            return execution.getStatus();
        } catch (Exception e) {
            String message =
                "Error running job based on " + entity + ": " + e.getMessage();
            log.error(message, e);
            throw new SnapshotException(e.getMessage(), e);
        }
    }
    

    /* (non-Javadoc)
     * @see org.duracloud.snapshot.manager.SnapshotJobManager#executeRestoration(java.lang.Long)
     */
    @Override
    public BatchStatus executeRestoration(Long restorationId)
        throws SnapshotException {
        return executeJob(getRestoration(restorationId));
    }
    

    /**
     * @param restorationId
     * @return
     */
    private Snapshot getRestoration(Long restorationId)  throws RestorationNotFoundException {
        Restoration restoration = this.restorationRepo.getOne(restorationId);
        if(restoration == null){
            throw new RestorationNotFoundException(restorationId);
        }
        return null;
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
        Snapshot snapshot = getSnapshot(snapshotId);
        BatchJobBuilder builder = this.builderManager.getBuilder(snapshot);
        JobParameters params = builder.buildIdentifyingJobParameters(snapshot);
        JobExecution ex =
            this.jobRepository.getLastJobExecution(SnapshotConstants.SNAPSHOT_JOB_NAME, params);
        if (ex == null) {
            return BatchStatus.UNKNOWN;
        }else{
            return ex.getStatus();
        }
    }
}

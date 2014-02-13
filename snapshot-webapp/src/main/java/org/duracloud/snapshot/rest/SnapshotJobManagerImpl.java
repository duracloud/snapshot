/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.rest;

import java.io.BufferedWriter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.duracloud.client.ContentStore;
import org.duracloud.common.model.ContentItem;
import org.duracloud.retrieval.mgmt.CSVFileOutputWriter;
import org.duracloud.retrieval.mgmt.OutputWriter;
import org.duracloud.retrieval.source.DuraStoreStitchingRetrievalSource;
import org.duracloud.retrieval.source.RetrievalSource;
import org.duracloud.retrieval.util.StoreClientUtil;
import org.duracloud.snapshot.spring.batch.SpaceItemReader;
import org.duracloud.snapshot.spring.batch.SpaceItemWriter;
import org.duracloud.snapshot.spring.batch.driver.SnapshotConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.builder.SimpleJobBuilder;
import org.springframework.batch.core.launch.JobInstanceAlreadyExistsException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.factory.SimpleStepFactoryBean;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The default implementation of the <code>SnapshotJobManager</code> interface.
 * Uses spring-batch componentry.
 * @author Daniel Bernstein Date: Feb 11, 2014
 */

@Component
public class SnapshotJobManagerImpl implements SnapshotJobManager {
    private static Logger log =
        LoggerFactory.getLogger(SnapshotJobManagerImpl.class);
    private JobExecutionListener jobListener;
    private JobLauncher jobLauncher;
    private JobRepository jobRepository;
    private PlatformTransactionManager transactionManager;
    private TaskExecutor taskExecutor;

    /**
     * 
     */
    public SnapshotJobManagerImpl(
        JobExecutionListener jobListener, JobLauncher jobLauncher,
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager, TaskExecutor taskExecutor) {

        super();
        this.jobListener = jobListener;
        this.jobLauncher = jobLauncher;
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.taskExecutor = taskExecutor;
    }
    
    

    /*
     * (non-Javadoc)
     * 
     * @see org.duracloud.snapshot.rest.SnapshotJobManager#getSnapshotList()
     */
    /*
     * @Override public List<SnapshotSummary> getSnapshotList() {
     * List<SnapshotSummary> list = new LinkedList<>();
     * 
     * for (int i = 0; i < 10; i++) { SnapshotSummary summary = new
     * SnapshotSummary(); list.add(summary); } return list;
     * 
     * }
     */

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.duracloud.snapshot.rest.SnapshotJobManager#createJobExecution(org
     * .duracloud.snapshot.spring.batch.driver.SnapshotConfig)
     */
    @Override
    public SnapshotStatus executeSnapshot(SnapshotConfig config)
        throws SnapshotException {
        Job job;
        try {

            StoreClientUtil clientUtil = new StoreClientUtil();
            ContentStore contentStore =
                clientUtil.createContentStore(config.getHost(),
                                              config.getPort(),
                                              config.getContext(),
                                              config.getUsername(),
                                              config.getPassword(),
                                              config.getStoreId());

            List<String> spaces = new ArrayList<>();
            spaces.add(config.getSpace());
            RetrievalSource retrievalSource =
                new DuraStoreStitchingRetrievalSource(contentStore,
                                                      spaces,
                                                      false);

            @SuppressWarnings("rawtypes")
            ItemReader itemReader = new SpaceItemReader(retrievalSource);

            File contentDir = config.getContentDir();
            File workDir = config.getWorkDir();
            OutputWriter outputWriter = new CSVFileOutputWriter(workDir);

            Path propsPath =
                FileSystems.getDefault().getPath(config.getContentDir()
                                                       .getAbsolutePath(),
                                                 "content-properties.json");
            BufferedWriter propsWriter =
                Files.newBufferedWriter(propsPath, StandardCharsets.UTF_8);

            Path md5Path =
                FileSystems.getDefault().getPath(config.getContentDir()
                                                       .getAbsolutePath(),
                                                 "manifest-md5.txt");
            BufferedWriter md5Writer =
                Files.newBufferedWriter(md5Path, StandardCharsets.UTF_8);
            Path sha256Path =
                FileSystems.getDefault().getPath(config.getContentDir()
                                                       .getAbsolutePath(),
                                                 "manifest-sha256.txt");
            BufferedWriter sha256Writer =
                Files.newBufferedWriter(sha256Path, StandardCharsets.UTF_8);

            @SuppressWarnings("rawtypes")
            ItemWriter itemWriter =
                new SpaceItemWriter( retrievalSource,
                                    contentDir,
                                    outputWriter,
                                    propsWriter,
                                    md5Writer,
                                    sha256Writer);

            SimpleStepFactoryBean<ContentItem, File> stepFactory =
                new SimpleStepFactoryBean<>();
            stepFactory.setJobRepository(jobRepository);
            stepFactory.setTransactionManager(transactionManager);
            stepFactory.setBeanName("step1");
            stepFactory.setItemReader(itemReader);
            stepFactory.setItemWriter(itemWriter);
            stepFactory.setCommitInterval(1);
            stepFactory.setThrottleLimit(20);
            stepFactory.setTaskExecutor(taskExecutor);
            Step step = (Step) stepFactory.getObject();

            JobBuilderFactory jobBuilderFactory =
                new JobBuilderFactory(jobRepository);
            JobBuilder jobBuilder = jobBuilderFactory.get("snapshot");
            SimpleJobBuilder simpleJobBuilder = jobBuilder.start(step);
            simpleJobBuilder.listener(jobListener);

            job = simpleJobBuilder.build();

        } catch (Exception e) {
            log.error("Error creating job: {}",
                      e.getMessage(),
                      e);
            throw new SnapshotException(e.getMessage(), e);
        }

        Map<String, JobParameter> params = new HashMap<>();
        String snapshotId = config.getSnapshotId();
        params.put("id", new JobParameter(snapshotId, true));

        try {
            JobExecution execution =
                jobLauncher.run(job, new JobParameters(params));
            log.info("Exit Status : {}", execution.getStatus());

            return createSnapshotStatus(snapshotId, execution);
        } catch (Exception e) {
            String message = "Error running job: "
                + snapshotId + ": " + e.getMessage();
            log.error(message,
                      e);
            throw new SnapshotException(e.getMessage(), e);
        }

    }
    
    /* (non-Javadoc)
     * @see org.duracloud.snapshot.rest.SnapshotJobManager#getJobExecution(java.lang.String)
     */
    @Override
    public SnapshotStatus getStatus(String snapshotId) throws SnapshotNotFoundException {
        Map<String,JobParameter> map = new HashMap<>();
        map.put("snapshotId", new JobParameter(snapshotId));
        JobParameters params = new JobParameters(map);
        JobExecution ex =  this.jobRepository.getLastJobExecution(snapshotId, params);
        if(ex != null){
            return createSnapshotStatus(snapshotId, ex);
        }
        
        throw new SnapshotNotFoundException("Snapshot [" + snapshotId + "] not found.");
        
    }



    /**
     * @param snapshotId
     * @param ex
     * @return
     */
    private SnapshotStatus createSnapshotStatus(String snapshotId,
                                                JobExecution ex) {
        return new SnapshotStatus(snapshotId, ex.getStatus().name());
    }
}

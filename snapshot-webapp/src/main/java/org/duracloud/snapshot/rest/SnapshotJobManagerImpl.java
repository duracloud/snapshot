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
import org.duracloud.snapshot.spring.batch.DatabaseInitializer;
import org.duracloud.snapshot.spring.batch.SpaceItemReader;
import org.duracloud.snapshot.spring.batch.SpaceItemWriter;
import org.duracloud.snapshot.spring.batch.driver.DatabaseConfig;
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
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.factory.SimpleStepFactoryBean;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The default implementation of the <code>SnapshotJobManager</code> interface.
 * Uses spring-batch componentry.
 * @author Daniel Bernstein Date: Feb 11, 2014
 */

@Component
public class SnapshotJobManagerImpl implements SnapshotJobManager, ApplicationContextAware {
    private static Logger log =
        LoggerFactory.getLogger(SnapshotJobManagerImpl.class);
    private JobExecutionListener jobListener;
    private JobLauncher jobLauncher;
    private JobRepository jobRepository;
    private PlatformTransactionManager transactionManager;
    private TaskExecutor taskExecutor;
    private ApplicationContext context;
    
    @Autowired
    public SnapshotJobManagerImpl(JobExecutionListener jobListener,
                                  PlatformTransactionManager transactionManager, 
                                  TaskExecutor taskExecutor) {

        super();
        this.jobListener = jobListener;
        this.transactionManager = transactionManager;
        this.taskExecutor = taskExecutor;
    }
    
    /**
     * @param jobLauncher the jobLauncher to set
     */
    public void setJobLauncher(JobLauncher jobLauncher) {
        this.jobLauncher = jobLauncher;
    }
    
    /**
     * @param jobRepository the jobRepository to set
     */
    protected void setJobRepository(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }
    
    /* (non-Javadoc)
     * @see org.springframework.context.ApplicationContextAware#setApplicationContext(org.springframework.context.ApplicationContext)
     */
    @Override
    public void setApplicationContext(ApplicationContext context)
        throws BeansException {
        this.context = context;
    }
    
    /* (non-Javadoc)
     * @see org.duracloud.snapshot.rest.SnapshotJobManager#initialize(org.duracloud.snapshot.rest.InitParams)
     */
    @Override
    public void initialize(InitParams params) {
        if(isInitialized()){
            log.warn("Already initialized. Ignorning");
            return;
        }

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setUrl(params.getDatabaseURL());
        dbConfig.setUsername(params.getDatabaseUser());
        dbConfig.setPassword(params.getDatabasePassword());
        
        DriverManagerDataSource dataSource = (DriverManagerDataSource)context.getBean("dataSource");
        dataSource.setUsername(dbConfig.getUsername());
        dataSource.setPassword(dbConfig.getPassword());
        dataSource.setUrl(dbConfig.getUrl());
        
        //initialize database
        DatabaseInitializer databaseInitializer =
            (DatabaseInitializer) context.getBean("databaseInitializer");
        databaseInitializer.init();

        this.jobRepository =
            (JobRepository) context.getBean("jobRepository");

        this.jobLauncher = (JobLauncher) context.getBean("jobLauncher");
    }

    /**
     * 
     */
    private boolean isInitialized() {
        return this.jobLauncher != null;
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
        checkInitialized();
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

            ItemReader<ContentItem> itemReader = new SpaceItemReader(retrievalSource);

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
    
    /**
     * 
     */
    private void checkInitialized() throws SnapshotException {
        if(!isInitialized()){
            throw new SnapshotException("The application must be initialized before it can be invoked!",
                                        null);
        }
    }

    /* (non-Javadoc)
     * @see org.duracloud.snapshot.rest.SnapshotJobManager#getJobExecution(java.lang.String)
     */
    @Override
    public SnapshotStatus getStatus(String snapshotId)
        throws SnapshotNotFoundException,
            SnapshotException {
        
        checkInitialized();
        
        Map<String, JobParameter> map = new HashMap<>();
        map.put("snapshotId", new JobParameter(snapshotId));
        JobParameters params = new JobParameters(map);
        JobExecution ex =
            this.jobRepository.getLastJobExecution(snapshotId, params);
        if (ex != null) {
            return createSnapshotStatus(snapshotId, ex);
        }

        throw new SnapshotNotFoundException("Snapshot ["
            + snapshotId + "] not found.");

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

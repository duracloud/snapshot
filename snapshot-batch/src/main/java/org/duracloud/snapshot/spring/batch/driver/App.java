/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.spring.batch.driver;

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
import org.springframework.batch.core.repository.support.JobRepositoryFactoryBean;
import org.springframework.batch.core.step.factory.SimpleStepFactoryBean;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.transaction.PlatformTransactionManager;

import com.mysql.jdbc.Driver;

/**
 * @author Erik Paulsson
 *         Date: 2/3/14
 */
public class App {

    private static final Logger LOGGER = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) throws Exception {
        ConfigParser configParser =
            new ConfigParser();
        SnapshotConfig config = configParser.processSnapshotConfigCommandLine(args);

        DatabaseConfig dbConfig = configParser.processDBCommandLine(args);

        String[] springConfig = {
            "spring/batch/config/context.xml",
            "spring/batch/config/database.xml"
        };
        
        ApplicationContext context =
            new ClassPathXmlApplicationContext(springConfig);

        //set the datasource properties before doing anything
        DriverManagerDataSource dataSource = (DriverManagerDataSource)context.getBean("dataSource");
        dataSource.setUsername(dbConfig.getUsername());
        dataSource.setPassword(dbConfig.getPassword());
        dataSource.setUrl(dbConfig.getUrl());
        
        //initialize database
        DatabaseInitializer databaseInitializer =
            (DatabaseInitializer) context.getBean("databaseInitializer");
        databaseInitializer.init();
        
        JobExecutionListener jobListener =
            (JobExecutionListener) context.getBean("jobListener");
        JobLauncher jobLauncher = (JobLauncher) context.getBean("jobLauncher");

        JobRepository jobRepository =
            (JobRepository) context.getBean("jobRepository");
        
        PlatformTransactionManager transactionManager =
            (PlatformTransactionManager) context.getBean("transactionManager");
        TaskExecutor taskExecutor =
            (TaskExecutor) context.getBean("taskExecutor");

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
        RetrievalSource retrievalSource = new DuraStoreStitchingRetrievalSource(
            contentStore, spaces, false);

        ItemReader<ContentItem> itemReader = new SpaceItemReader(retrievalSource);

        File contentDir = config.getContentDir();
        File workDir = config.getWorkDir();
        OutputWriter outputWriter = new CSVFileOutputWriter(workDir);

        Path propsPath = FileSystems.getDefault().getPath(
            config.getContentDir().getAbsolutePath(),
            "content-properties.json");
        BufferedWriter propsWriter =
            Files.newBufferedWriter(propsPath,
                                    StandardCharsets.UTF_8);

        Path md5Path = FileSystems.getDefault().getPath(
            config.getContentDir().getAbsolutePath(), "manifest-md5.txt");
        BufferedWriter md5Writer =
            Files.newBufferedWriter(md5Path,
                                    StandardCharsets.UTF_8);
        Path sha256Path = FileSystems.getDefault().getPath(
            config.getContentDir().getAbsolutePath(), "manifest-sha256.txt");
        BufferedWriter sha256Writer =
            Files.newBufferedWriter(sha256Path,
                                    StandardCharsets.UTF_8);
        ItemWriter itemWriter = new SpaceItemWriter(retrievalSource,
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

        JobBuilderFactory jobBuilderFactory = new JobBuilderFactory(jobRepository);
        JobBuilder jobBuilder = jobBuilderFactory.get("snapshot");
        SimpleJobBuilder simpleJobBuilder = jobBuilder.start(step);
        simpleJobBuilder.listener(jobListener);
        Job job = simpleJobBuilder.build();

        Map<String, JobParameter> params = new HashMap<>();
        String snapshotId = config.getSnapshotId();
        params.put("id", new JobParameter(snapshotId, true));

        try {

            JobExecution execution = jobLauncher.run(job, new JobParameters(params));
            LOGGER.info("Exit Status : {}", execution.getStatus());

        } catch (Exception e) {
            LOGGER.error("Error running job: " + snapshotId, e);
        }
    }
}

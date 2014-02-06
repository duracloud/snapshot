/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.spring.batch;

import org.duracloud.client.ContentStore;
import org.duracloud.common.model.ContentItem;
import org.duracloud.retrieval.mgmt.CSVFileOutputWriter;
import org.duracloud.retrieval.mgmt.OutputWriter;
import org.duracloud.retrieval.source.DuraStoreStitchingRetrievalSource;
import org.duracloud.retrieval.source.RetrievalSource;
import org.duracloud.retrieval.util.StoreClientUtil;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.builder.SimpleJobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.factory.SimpleStepFactoryBean;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Erik Paulsson
 *         Date: 2/3/14
 */
public class App {
    public static void main(String[] args) throws Exception {
        String[] springConfig = {
            "spring\\batch\\config\\context.xml",
            "spring\\batch\\config\\database.xml"
        };

        ApplicationContext context =
            new ClassPathXmlApplicationContext(springConfig);

        JobLauncher jobLauncher = (JobLauncher) context.getBean("jobLauncher");
        JobRepository jobRepository =
            (JobRepository) context.getBean("jobRepository");
        PlatformTransactionManager transactionManager =
            (PlatformTransactionManager) context.getBean("transactionManager");
        TaskExecutor taskExecutor =
            (TaskExecutor) context.getBean("taskExecutor");

        StoreClientUtil clientUtil = new StoreClientUtil();
        ContentStore contentStore =
            clientUtil.createContentStore("<duracloud-host>",
                                          80,
                                          "durastore",
                                          "<duracloud-user>",
                                          "<secret-pass>",
                                          "0");
        List spaces = new ArrayList<String>();
        spaces.add("<duracloud-space>");
        RetrievalSource retrievalSource = new DuraStoreStitchingRetrievalSource(
            contentStore, spaces, false);

        ItemReader itemReader = new SpaceItemReader(retrievalSource);
        ItemWriter itemWriter = new SpaceItemWriter();

        File contentDir = new File("<content-dir>");
        File workDir = new File("<work-dir>");
        OutputWriter outputWriter = new CSVFileOutputWriter(workDir);
        ItemProcessor itemProcessor = new SpaceItemProcessor(retrievalSource,
                                                             contentDir,
                                                             outputWriter);

        SimpleStepFactoryBean stepFactory = new SimpleStepFactoryBean<ContentItem, File>();
        stepFactory.setJobRepository(jobRepository);
        stepFactory.setTransactionManager(transactionManager);
        stepFactory.setBeanName("step1");
        stepFactory.setItemReader(itemReader);
        stepFactory.setItemProcessor(itemProcessor);
        stepFactory.setItemWriter(itemWriter);
        stepFactory.setCommitInterval(1);
        stepFactory.setThrottleLimit(20);
        stepFactory.setTaskExecutor(taskExecutor);
        Step step = (Step) stepFactory.getObject();

        JobBuilderFactory jobBuilderFactory = new JobBuilderFactory(jobRepository);
        JobBuilder jobBuilder = jobBuilderFactory.get("snapshot");
        SimpleJobBuilder simpleJobBuilder = jobBuilder.start(step);
        Job job = simpleJobBuilder.build();

        Map<String, JobParameter> params = new HashMap();
        params.put("id", new JobParameter("<snapshot-id>", true));

        try {

            JobExecution execution = jobLauncher.run(job, new JobParameters(params));
            System.out.println("Exit Status : " + execution.getStatus());

        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("Done");
    }
}

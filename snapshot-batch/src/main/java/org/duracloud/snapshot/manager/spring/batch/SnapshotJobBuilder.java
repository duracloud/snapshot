/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.manager.spring.batch;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
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
import org.duracloud.snapshot.db.ContentDirUtils;
import org.duracloud.snapshot.db.model.DuracloudEndPointConfig;
import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.manager.SnapshotConstants;
import org.duracloud.snapshot.manager.SnapshotException;
import org.duracloud.snapshot.manager.config.SnapshotJobManagerConfig;
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
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * @author Daniel Bernstein Date: Feb 19, 2014
 */
@Component
public class SnapshotJobBuilder implements BatchJobBuilder<Snapshot> {
    private static Logger log = LoggerFactory.getLogger(SnapshotJobBuilder.class);

    private static final String MANIFEST_SHA256_TXT_FILE_NAME =
        "manifest-sha256.txt";
    private static final String MANIFEST_MD5_TXT_FILE_NAME = "manifest-md5.txt";
    private static final String CONTENT_PROPERTIES_JSON_FILENAME =
        "content-properties.json";

    private JobExecutionListener jobListener;
    private JobRepository jobRepository;
    private PlatformTransactionManager transactionManager;
    private TaskExecutor taskExecutor;
    
    @Autowired
    public SnapshotJobBuilder(JobExecutionListener jobListener, 
                              JobRepository jobRepository,
                              PlatformTransactionManager transactionManager, 
                              TaskExecutor taskExecutor) {

        this.jobListener = jobListener;
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.taskExecutor = taskExecutor;
    }

    /* (non-Javadoc)
     * @see org.duracloud.snapshot.manager.spring.batch.BatchJobBuilder#buildJob(java.lang.Object, org.duracloud.snapshot.manager.config.SnapshotJobManagerConfig)
     */
    @Override
    public Job
        buildJob(Snapshot snapshot, SnapshotJobManagerConfig config) throws SnapshotException {

        Job job;
        try {
            
            DuracloudEndPointConfig source = snapshot.getSource();
            StoreClientUtil clientUtil = new StoreClientUtil();

            ContentStore contentStore =
                clientUtil.createContentStore(source.getHost(),
                                              source.getPort(),
                                              SnapshotConstants.DURASTORE_CONTEXT,
                                              config.getDuracloudUsername(),
                                              config.getDuracloudPassword(),
                                              source.getStoreId());

            List<String> spaces = new ArrayList<>();
            spaces.add(source.getSpaceId());

            RetrievalSource retrievalSource =
                new DuraStoreStitchingRetrievalSource(contentStore,
                                                      spaces,
                                                      false);

            ItemReader<ContentItem> itemReader =
                new SpaceItemReader(retrievalSource);

            File contentDir = new File(ContentDirUtils.getDestinationPath(snapshot, config.getContentRootDir()));
            if(!contentDir.exists()){
                contentDir.mkdirs();
            }

            File workDir = config.getWorkDir();
            OutputWriter outputWriter = new CSVFileOutputWriter(workDir);

            BufferedWriter propsWriter =
                createWriter(contentDir, CONTENT_PROPERTIES_JSON_FILENAME);
            BufferedWriter md5Writer =
                createWriter(contentDir, MANIFEST_MD5_TXT_FILE_NAME);
            BufferedWriter sha256Writer =
                createWriter(contentDir, MANIFEST_SHA256_TXT_FILE_NAME);

            ItemWriter itemWriter =
                new SpaceItemWriter(retrievalSource,
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
            JobBuilder jobBuilder = jobBuilderFactory.get(SnapshotConstants.SNAPSHOT_JOB_NAME);
            SimpleJobBuilder simpleJobBuilder = jobBuilder.start(step);
            simpleJobBuilder.listener(jobListener);

            job = simpleJobBuilder.build();

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
    public JobParameters buildIdentifyingJobParameters(Snapshot snapshot) {
        Map<String, JobParameter> map = createIdentifyingJobParameters(snapshot);
        JobParameters params = new JobParameters(map);
        return params;
    }
    
    /* (non-Javadoc)
     * @see org.duracloud.snapshot.manager.spring.batch.BatchJobBuilder#buildJobParameters(java.lang.Object)
     */
    @Override
    public JobParameters buildJobParameters(Snapshot snapshot) {
        return buildIdentifyingJobParameters(snapshot);
    }

    private Map<String,JobParameter> createIdentifyingJobParameters(Snapshot snapshot) {
        Map<String, JobParameter> map = new HashMap<>();
        map.put(SnapshotConstants.OBJECT_ID, new JobParameter(snapshot.getId(), true));
        return map;
    }

    
    /**
     * @param contentDir
     * @param file
     * @return
     * @throws IOException
     */
    private BufferedWriter createWriter(File contentDir, String file)
        throws IOException {
        Path propsPath = getPath(contentDir, file);
        BufferedWriter propsWriter =
            Files.newBufferedWriter(propsPath, StandardCharsets.UTF_8);
        return propsWriter;
    }

    /**
     * @param contentDir
     * @param filename
     * @return
     */
    private Path getPath(File contentDir, String filename) {
        Path path =
            FileSystems.getDefault().getPath(contentDir.getAbsolutePath(),
                                             filename);
        return path;
    }
}

/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.duracloud.client.ContentStore;
import org.duracloud.common.model.ContentItem;
import org.duracloud.manifeststitch.StitchedManifestGenerator;
import org.duracloud.retrieval.mgmt.LoggingOutputWriter;
import org.duracloud.retrieval.source.DuraStoreStitchingRetrievalSource;
import org.duracloud.retrieval.source.RetrievalSource;
import org.duracloud.snapshot.SnapshotException;
import org.duracloud.snapshot.common.SnapshotServiceConstants;
import org.duracloud.snapshot.db.ContentDirUtils;
import org.duracloud.snapshot.db.model.DuracloudEndPointConfig;
import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.service.SnapshotJobManagerConfig;
import org.duracloud.snapshot.service.SnapshotManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * @author Daniel Bernstein Date: Feb 19, 2014
 */
@Component
public class SnapshotJobBuilder extends AbstractJobBuilder implements BatchJobBuilder<Snapshot> {
    private static Logger log = LoggerFactory.getLogger(SnapshotJobBuilder.class);

    private static final String MANIFEST_SHA256_TXT_FILE_NAME =
        SnapshotServiceConstants.MANIFEST_SHA256_TXT_FILE_NAME;
    private static final String MANIFEST_MD5_TXT_FILE_NAME =
        SnapshotServiceConstants.MANIFEST_MD5_TXT_FILE_NAME;
    private SnapshotJobExecutionListener jobListener;
    private JobRepository jobRepository;
    private PlatformTransactionManager transactionManager;
    private TaskExecutor taskExecutor;
    private SnapshotManager snapshotManager;
    private StoreClientHelper storeClientHelper;

    @Autowired
    public SnapshotJobBuilder(SnapshotJobExecutionListener jobListener,
                              JobRepository jobRepository,
                              PlatformTransactionManager transactionManager,
                              @Qualifier("itemTaskExecutor") TaskExecutor taskExecutor,
                              SnapshotManager snapshotManager,
                              StoreClientHelper storeClientHelper) {

        this.jobListener = jobListener;
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.taskExecutor = taskExecutor;
        this.snapshotManager = snapshotManager;
        this.storeClientHelper = storeClientHelper;
    }

    /* (non-Javadoc)
     * @see org.duracloud.snapshot.manager.spring.batch.BatchJobBuilder#buildJob(java.lang.Object, org.duracloud
     * .snapshot.manager.config.SnapshotJobManagerConfig)
     */
    @Override
    public Job buildJob(Snapshot snapshot, SnapshotJobManagerConfig config)
        throws SnapshotException {

        Job job;
        try {

            DuracloudEndPointConfig source = snapshot.getSource();

            ContentStore contentStore =
                storeClientHelper.create(source, config.getDuracloudUsername(),
                                         config.getDuracloudPassword());

            List<String> spaces = new ArrayList<>();
            spaces.add(source.getSpaceId());

            RetrievalSource retrievalSource =
                new DuraStoreStitchingRetrievalSource(contentStore,
                                                      spaces,
                                                      false);

            ItemReader<ContentItem> itemReader =
                new SpaceItemReader(retrievalSource);

            File contentDir =
                new File(ContentDirUtils.getDestinationPath(snapshot.getName(),
                                                            config.getContentRootDir()));
            if (!contentDir.exists()) {
                contentDir.mkdirs();
            }

            File propsFile = new File(contentDir, SnapshotServiceConstants.CONTENT_PROPERTIES_JSON_FILENAME);
            File md5File = new File(contentDir, MANIFEST_MD5_TXT_FILE_NAME);
            File sha256File = new File(contentDir, MANIFEST_SHA256_TXT_FILE_NAME);

            SpaceManifestDpnManifestVerifier verifier =
                new SpaceManifestDpnManifestVerifier(md5File,
                                                     new StitchedManifestGenerator(contentStore),
                                                     source.getSpaceId());
            ItemWriter itemWriter =
                new SpaceItemWriter(snapshot,
                                    retrievalSource,
                                    contentDir,
                                    new LoggingOutputWriter(),
                                    propsFile,
                                    md5File,
                                    sha256File,
                                    snapshotManager,
                                    verifier);

            SimpleStepFactoryBean<ContentItem, File> stepFactory =
                new SimpleStepFactoryBean<>();
            stepFactory.setJobRepository(jobRepository);
            stepFactory.setTransactionManager(transactionManager);
            stepFactory.setBeanName("step1");
            stepFactory.setItemReader(itemReader);
            stepFactory.setItemWriter(itemWriter);
            stepFactory.setCommitInterval(1);
            setThrottleLimitForContentTransfers(stepFactory);
            stepFactory.setTaskExecutor(taskExecutor);
            Step step = (Step) stepFactory.getObject();

            JobBuilderFactory jobBuilderFactory =
                new JobBuilderFactory(jobRepository);
            JobBuilder jobBuilder =
                jobBuilderFactory.get(getJobName());
            SimpleJobBuilder simpleJobBuilder = jobBuilder.start(step);
            simpleJobBuilder.listener(jobListener);

            job = simpleJobBuilder.build();
            log.debug("build job {}", job);

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

    private Map<String, JobParameter> createIdentifyingJobParameters(Snapshot snapshot) {
        return SnapshotJobParameterMarshaller.marshal(snapshot);
    }

    /* (non-Javadoc)
     * @see org.duracloud.snapshot.service.impl.BatchJobBuilder#getJobName()
     */
    @Override
    public String getJobName() {
        return SnapshotServiceConstants.SNAPSHOT_JOB_NAME;
    }
}

/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import java.io.File;

import org.duracloud.client.ContentStore;
import org.duracloud.common.constant.Constants;
import org.duracloud.manifeststitch.StitchedManifestGenerator;
import org.duracloud.retrieval.util.StoreClientUtil;
import org.duracloud.snapshot.SnapshotConstants;
import org.duracloud.snapshot.SnapshotException;
import org.duracloud.snapshot.common.SnapshotServiceConstants;
import org.duracloud.snapshot.db.ContentDirUtils;
import org.duracloud.snapshot.db.model.DuracloudEndPointConfig;
import org.duracloud.snapshot.db.model.Restoration;
import org.duracloud.snapshot.db.model.SnapshotContentItem;
import org.duracloud.snapshot.db.repo.SnapshotContentItemRepo;
import org.duracloud.snapshot.service.RestoreManager;
import org.duracloud.snapshot.service.SnapshotJobManagerConfig;
import org.duracloud.sync.endpoint.DuraStoreChunkSyncEndpoint;
import org.duracloud.sync.endpoint.EndPointLogger;
import org.duracloud.sync.endpoint.SyncEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepListener;
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
 * @author Daniel Bernstein Date: Feb 19, 2014
 */
@Component
public class RestoreJobBuilder implements BatchJobBuilder<Restoration> {
    private static Logger log = LoggerFactory.getLogger(RestoreJobBuilder.class);

    private RestoreJobExecutionListener jobListener;
    private JobRepository jobRepository;
    private PlatformTransactionManager transactionManager;
    private TaskExecutor taskExecutor;

    private RestoreManager restoreManager;
    private SnapshotContentItemRepo snapshotContentItemRepo;

    @Autowired
    public RestoreJobBuilder(
        RestoreJobExecutionListener jobListener, JobRepository jobRepository,
        PlatformTransactionManager transactionManager, TaskExecutor taskExecutor, RestoreManager restoreManager,
        SnapshotContentItemRepo snapshotContentItemRepo) {
        this.jobListener = jobListener;
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.taskExecutor = taskExecutor;
        this.restoreManager = restoreManager;
        this.snapshotContentItemRepo = snapshotContentItemRepo;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.duracloud.snapshot.manager.spring.batch.BatchJobBuilder#buildJob(java
     * .lang.Object,
     * org.duracloud.snapshot.manager.config.SnapshotJobManagerConfig)
     */
    @Override
    public Job buildJob(Restoration restoration, SnapshotJobManagerConfig jobManagerConfig) throws SnapshotException {
        Job job;

        try {

            DuracloudEndPointConfig destination = restoration.getDestination();
            String destinationSpaceId = destination.getSpaceId();
            String restoreId = restoration.getRestorationId();

            StoreClientUtil clientUtil = new StoreClientUtil();
            ContentStore contentStore =
                clientUtil.createContentStore(destination.getHost(),
                                              destination.getPort(),
                                              SnapshotServiceConstants.DURASTORE_CONTEXT,
                                              jobManagerConfig.getDuracloudUsername(),
                                              jobManagerConfig.getDuracloudPassword(),
                                              destination.getStoreId());

            JobBuilderFactory jobBuilderFactory = new JobBuilderFactory(jobRepository);
            JobBuilder jobBuilder = jobBuilderFactory.get(getJobName());
            SimpleJobBuilder simpleJobBuilder =
                jobBuilder.start(buildVerifyDpnTransferUsingDpnManifestStep(restoreId, jobManagerConfig))
                          .next(buildVerifyDpnTransferUsingSnapshotRepoStep(restoreId, jobManagerConfig))
                          .next(buildRestoreContentStep(restoreId, destinationSpaceId, contentStore, jobManagerConfig))
                          .next(buildRestoreContentPropertiesStep(restoreId,
                                                                  destinationSpaceId,
                                                                  contentStore,
                                                                  jobManagerConfig))
                          .next(buildVerifyDuraCloudTransferStep(restoreId,
                                                                 destinationSpaceId,
                                                                 contentStore,
                                                                 jobManagerConfig));
            simpleJobBuilder.listener(jobListener);
            job = simpleJobBuilder.build();
            log.debug("build job {}", job);
        } catch (Exception e) {
            log.error("Error creating job: {}", e.getMessage(), e);
            throw new SnapshotException(e.getMessage(), e);
        }
        return job;
    }

    /**
     * @param restoreId
     * @param jobManagerConfig
     * @return
     */
    private Step buildVerifyDpnTransferUsingSnapshotRepoStep(String restoreId,
                                                             SnapshotJobManagerConfig jobManagerConfig)
                                                                 throws Exception {
        File restoreDir = new File(ContentDirUtils.getSourcePath(restoreId, jobManagerConfig.getContentRootDir()));
        Restoration restore = this.restoreManager.get(restoreId);
        SnapshotRepoManifestReader reader =
            new SnapshotRepoManifestReader(this.snapshotContentItemRepo, restore.getSnapshot().getName());

        SnapshotContentItemVerifier writer =
            new SnapshotContentItemVerifier(restoreId,
                                            getRestoreMd5Manifest(restoreDir),
                                            restore.getSnapshot().getName(),
                                            restoreManager);
        SimpleStepFactoryBean<SnapshotContentItem, SnapshotContentItem> stepFactory = new SimpleStepFactoryBean<>();

        stepFactory.setJobRepository(jobRepository);
        stepFactory.setTransactionManager(transactionManager);
        stepFactory.setBeanName("verifyDpnTransferUsingSnapshotRepo");
        stepFactory.setItemReader(reader);
        stepFactory.setItemWriter(writer);
        stepFactory.setCommitInterval(50);
        stepFactory.setThrottleLimit(20);
        stepFactory.setTaskExecutor(taskExecutor);
        stepFactory.setListeners(new StepListener[] { writer });
        return stepFactory.getObject();
    }

    /**
     * @param restoreId
     * @param destinationSpaceId
     * @param contentStore
     * @param jobManagerConfig
     * @return
     */
    private Step buildVerifyDuraCloudTransferStep(String restoreId,
                                                  String destinationSpaceId,
                                                  ContentStore contentStore,
                                                  SnapshotJobManagerConfig jobManagerConfig) throws Exception {

        File restoreDir = getRestoreDir(restoreId, jobManagerConfig);

        File md5Manifest = getRestoreMd5Manifest(restoreDir);

        DpnManifestReader reader = new DpnManifestReader(md5Manifest);
        SpaceManifestDpnManifestVerifier spaceManifestVerifier =
            new SpaceManifestDpnManifestVerifier(md5Manifest,
                                                 new StitchedManifestGenerator(contentStore),
                                                 destinationSpaceId);
        SpaceVerifier writer = new SpaceVerifier(restoreId, 
                                                 spaceManifestVerifier, 
                                                 destinationSpaceId, 
                                                 restoreManager);

        SimpleStepFactoryBean<ManifestEntry, ManifestEntry> stepFactory = new SimpleStepFactoryBean<>();
        stepFactory.setJobRepository(jobRepository);
        stepFactory.setTransactionManager(transactionManager);
        stepFactory.setBeanName("verifyDuraCloudTransfer");
        stepFactory.setItemReader(reader);
        stepFactory.setItemWriter(writer);
        stepFactory.setCommitInterval(1);
        stepFactory.setThrottleLimit(20);
        stepFactory.setTaskExecutor(taskExecutor);
        stepFactory.setListeners(new StepListener[] { writer });
        stepFactory.setAllowStartIfComplete(false);
        return stepFactory.getObject();
    }

    /**
     * @param restoreId
     * @param jobManagerConfig
     * @return
     */
    private Step buildVerifyDpnTransferUsingDpnManifestStep(String restoreId, SnapshotJobManagerConfig jobManagerConfig)
        throws Exception {

        File restoreDir = getRestoreDir(restoreId, jobManagerConfig);

        File md5Manifest = getRestoreMd5Manifest(restoreDir);

        DpnManifestReader reader = new DpnManifestReader(md5Manifest);

        File contentDir = getRestoreContentDir(restoreDir);

        ManifestVerifier writer = new ManifestVerifier(restoreId, contentDir, restoreManager);

        SimpleStepFactoryBean<ManifestEntry, ManifestEntry> stepFactory = new SimpleStepFactoryBean<>();
        stepFactory.setJobRepository(jobRepository);
        stepFactory.setTransactionManager(transactionManager);
        stepFactory.setBeanName("verifyDpnTransferUsingDpnManifest");
        stepFactory.setItemReader(reader);
        stepFactory.setItemWriter(writer);
        stepFactory.setCommitInterval(50);
        stepFactory.setThrottleLimit(20);
        stepFactory.setTaskExecutor(taskExecutor);
        stepFactory.setListeners(new StepListener[] { writer, reader });
        return stepFactory.getObject();
    }

    /**
     * @param restoreDir
     * @return
     */
    private File getRestoreMd5Manifest(File restoreDir) {
        File md5Manifest = new File(restoreDir, ManifestFileHelper.MANIFEST_MD5_TEXT_FILE_NAME);

        if (!md5Manifest.exists()) {
            throw new RuntimeException("The md5 manifest file is missing: " + md5Manifest.getAbsolutePath());
        }
        return md5Manifest;
    }

    /**
     * @param restoreDir
     * @return
     */
    private File getRestoreContentDir(File restoreDir) {
        File contentDir = new File(restoreDir, "data");

        if (!contentDir.exists()) {
            throw new RuntimeException("The content direcotry is missing for restoration:  "
                + contentDir.getAbsolutePath());
        }
        return contentDir;
    }

    /**
     * @param restorationId
     * @param jobManagerConfig
     * @return
     */
    private File getRestoreDir(String restorationId, SnapshotJobManagerConfig jobManagerConfig) {
        File restoreDir = new File(ContentDirUtils.getSourcePath(restorationId, jobManagerConfig.getContentRootDir()));

        if (!restoreDir.exists()) {
            throw new RuntimeException("The directory for the restored snapshot "
                + "does not exist in bridge storage: missing: " + restoreDir.getAbsolutePath());
        }
        return restoreDir;
    }

    private Step buildRestoreContentPropertiesStep(String restorationId,
                                                   String destinationSpaceId,
                                                   ContentStore contentStore,
                                                   SnapshotJobManagerConfig jobManagerConfig) throws Exception {

        File contentPropertiesJsonFile =
            new File(ContentDirUtils.getSourcePath(restorationId, jobManagerConfig.getContentRootDir()),
                     SnapshotServiceConstants.CONTENT_PROPERTIES_JSON_FILENAME);

        if (!contentPropertiesJsonFile.exists()) {
            throw new RuntimeException("The restored content properties file is missing : "
                + contentPropertiesJsonFile.getAbsolutePath());
        }

        ContentPropertiesFileReader reader = new ContentPropertiesFileReader(contentPropertiesJsonFile);

        ContentPropertiesWriter writer = new ContentPropertiesWriter(contentStore, destinationSpaceId);

        SimpleStepFactoryBean<ContentProperties, ContentProperties> stepFactory = new SimpleStepFactoryBean<>();
        stepFactory.setJobRepository(jobRepository);
        stepFactory.setTransactionManager(transactionManager);
        stepFactory.setBeanName("restoreContentProperties");
        stepFactory.setItemReader(reader);
        stepFactory.setItemWriter(writer);
        stepFactory.setCommitInterval(1);
        stepFactory.setThrottleLimit(20);
        stepFactory.setTaskExecutor(taskExecutor);
        stepFactory.setListeners(new StepListener[] { writer });
        return stepFactory.getObject();
    }

    private Step buildRestoreContentStep(String restorationId,
                                         String destinationSpaceId,
                                         ContentStore contentStore,
                                         SnapshotJobManagerConfig jobManagerConfig) throws Exception {

        SyncEndpoint endpoint =
            new DuraStoreChunkSyncEndpoint(contentStore,
                                           jobManagerConfig.getDuracloudUsername(),
                                           destinationSpaceId,
                                           false,
                                           true,
                                           1073741824); // 1GiB chunk size
        endpoint.addEndPointListener(new EndPointLogger());

        File watchDir =
            new File(ContentDirUtils.getSourcePath(restorationId, jobManagerConfig.getContentRootDir())
                + File.separator + "data");

        if (!watchDir.exists()) {
            throw new RuntimeException("The content directory for the restored "
                + "snapshot does not exist in bridge storage: " + "missing watchDir: " + watchDir.getAbsolutePath());
        }

        FileSystemReader reader = new FileSystemReader(watchDir);

        SyncWriter writer =
            new SyncWriter(restorationId, 
                           watchDir, 
                           endpoint, 
                           contentStore, 
                           destinationSpaceId, 
                           restoreManager);

        SimpleStepFactoryBean<File, File> stepFactory = new SimpleStepFactoryBean<>();
        stepFactory.setJobRepository(jobRepository);
        stepFactory.setTransactionManager(transactionManager);
        stepFactory.setBeanName("restoreContent");
        stepFactory.setItemReader(reader);
        stepFactory.setItemWriter(writer);
        stepFactory.setCommitInterval(20);
        stepFactory.setThrottleLimit(20);
        stepFactory.setTaskExecutor(taskExecutor);
        stepFactory.setListeners(new StepListener[] { writer });
        return stepFactory.getObject();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.duracloud.snapshot.manager.spring.batch.BatchJobBuilder#
     * buildIdentifyingJobParameters(java.lang.Object)
     */
    @Override
    public JobParameters buildIdentifyingJobParameters(Restoration restoration) {
        return new JobParameters(RestoreJobParameterMarshaller.marshal(restoration));
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.duracloud.snapshot.manager.spring.batch.BatchJobBuilder#
     * buildJobParameters(java.lang.Object)
     */
    @Override
    public JobParameters buildJobParameters(Restoration entity) {
        return buildIdentifyingJobParameters(entity);
    }
    
    /* (non-Javadoc)
     * @see org.duracloud.snapshot.service.impl.BatchJobBuilder#getJobName()
     */
    @Override
    public String getJobName() {
        return SnapshotServiceConstants.RESTORE_JOB_NAME;
    }
}

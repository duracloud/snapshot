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
import java.util.List;
import java.util.Map;

import org.duracloud.client.ContentStore;
import org.duracloud.common.collection.WriteOnlyStringSet;
import org.duracloud.common.constant.Constants;
import org.duracloud.common.retry.Retriable;
import org.duracloud.common.retry.Retrier;
import org.duracloud.snapshot.SnapshotConstants;
import org.duracloud.snapshot.db.model.SnapshotContentItem;
import org.duracloud.snapshot.dto.RestoreStatus;
import org.duracloud.snapshot.service.RestoreManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.ItemWriteListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.item.ItemWriter;

/**
 * @author Daniel Bernstein Date: Jul 29, 2015
 */
public class SnapshotContentItemVerifier extends StepExecutionSupport
    implements ItemWriter<SnapshotContentItem>, ItemWriteListener<SnapshotContentItem> {

    /**
     * 
     */
    private Logger log = LoggerFactory.getLogger(SpaceVerifier.class);
    private File manifestFile;
    private String restoreId;
    private String snapshotName;
    private RestoreManager restoreManager;
    private WriteOnlyStringSet manifestSet;
    /**
     * @param restoreId
     * @param manifestFile
     * @param snapshotName
     * @param restoreManager
     */
    public SnapshotContentItemVerifier(
        String restoreId, File manifestFile, String snapshotName, RestoreManager restoreManager) {
        this.restoreId = restoreId;
        this.manifestFile = manifestFile;
        this.snapshotName = snapshotName;
        this.restoreManager = restoreManager;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.springframework.batch.core.ItemWriteListener#beforeWrite(java.util.
     * List)
     */
    @Override
    public void beforeWrite(List<? extends SnapshotContentItem> items) {

    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.springframework.batch.core.ItemWriteListener#afterWrite(java.util.
     * List)
     */
    @Override
    public void afterWrite(List<? extends SnapshotContentItem> items) {
        //be sure not to count snapshot prop file.
        int size = items.size();
        for(SnapshotContentItem item : items){
            if(item.getContentId().equals(Constants.SNAPSHOT_PROPS_FILENAME)){
                size -= 1;
            }
        }
        addToItemsRead(size);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.springframework.batch.core.ItemWriteListener#onWriteError(java.lang.
     * Exception, java.util.List)
     */
    @Override
    public void onWriteError(Exception exception, List<? extends SnapshotContentItem> items) {
        addError(exception.getMessage());
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.springframework.batch.core.StepExecutionListener#beforeStep(org.
     * springframework.batch.core.StepExecution)
     */
    @Override
    public void beforeStep(StepExecution stepExecution) {
        super.beforeStep(stepExecution);
        resetContextState();
        try {

            this.manifestSet = ManifestFileHelper.loadManifestSetFromFile(this.manifestFile);

            new Retrier().execute(new Retriable() {
                /*
                 * (non-Javadoc)
                 * 
                 * @see org.duracloud.common.retry.Retriable#retry()
                 */
                @Override
                public Object retry() throws Exception {
                    RestoreStatus newStatus = RestoreStatus.VERIFYING_SNAPSHOT_REPO_AGAINST_MANIFEST;
                    restoreManager.transitionRestoreStatus(restoreId, newStatus, "");
                    return null;
                }
            });

        } catch (Exception ex) {
            addError("failed to transition status to "
                + RestoreStatus.VERIFYING_SNAPSHOT_REPO_AGAINST_MANIFEST + ": " + ex.getMessage());
            stepExecution.addFailureException(ex);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.springframework.batch.core.StepExecutionListener#afterStep(org.
     * springframework.batch.core.StepExecution)
     */
    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
            // compare counts (which should not include SNAPSHOT_PROPS_FILENAME
            // on the snapshot repo side since it does not get written to the
            // manifest.
            long snapshotItemCount = getItemsRead();
            if (snapshotItemCount == this.manifestSet.size()) {
                log.debug("snapshot repo count matches manifest count: "
                    + "step_execution_id={} job_execution_id={} snapshot_name={}",
                          stepExecution.getId(),
                          stepExecution.getJobExecutionId(),
                          this.snapshotName);
            } else {
                addError("snapshot ("
                    + snapshotName + ") content item count (" + snapshotItemCount
                    + ") does not match manifest count (" + manifestSet.size() + ")");
            }

            ExitStatus status = stepExecution.getExitStatus();

            List<String> errors = getErrors();
            if (errors.size() > 0) {
                status = status.and(ExitStatus.FAILED);

                for (String error : errors) {
                    status = status.addExitDescription(error);
                }

                log.error("snapshot repo verification finished: step_execution_id={} "
                    + "job_execution_id={} snapshot_name={} status=\"{}\"",
                          stepExecution.getId(),
                          stepExecution.getJobExecutionId(),
                          snapshotName,
                          status);
                
                failExecution();

                resetContextState();

            } else {
                status = status.and(ExitStatus.COMPLETED);
            }

            return status;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.springframework.batch.item.ItemWriter#write(java.util.List)
     */
    @Override
    public void write(List<? extends SnapshotContentItem> items) throws Exception {
        for (SnapshotContentItem item : items) {
            Map<String, String> props = PropertiesSerializer.deserialize(item.getMetadata());
            String contentId = item.getContentId();
            String checksum = props.get(ContentStore.CONTENT_CHECKSUM);

            // verify that manifest contains every item from the database except
            // SNAPSHOT_PROPS_FILENAME
            if (!contentId.equals(Constants.SNAPSHOT_PROPS_FILENAME)) {
                if (!this.manifestSet.contains(ManifestFileHelper.formatManifestSetString(contentId, checksum))) {
                    addError(MessageFormat.format("Content item {0} with checksum {1} not found in manifest "
                        + "for snapshot {2}", contentId, checksum, this.snapshotName));
                }
            }
        }
    }

}

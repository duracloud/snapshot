/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import java.io.File;
import java.util.List;

import org.duracloud.client.ContentStore;
import org.duracloud.common.constant.Constants;
import org.duracloud.common.retry.Retriable;
import org.duracloud.common.retry.Retrier;
import org.duracloud.domain.Space;
import org.duracloud.error.ContentStoreException;
import org.duracloud.error.NotFoundException;
import org.duracloud.snapshot.dto.RestoreStatus;
import org.duracloud.snapshot.service.RestoreManager;
import org.duracloud.sync.endpoint.MonitoredFile;
import org.duracloud.sync.endpoint.SyncEndpoint;
import org.duracloud.sync.endpoint.SyncResultType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.ItemWriteListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.item.ItemWriter;
import org.springframework.util.CollectionUtils;

/**
 * This class is responsible for syncing content from the bridge to DuraCloud.
 *
 * @author Daniel Bernstein Date: Jul 17, 2014
 */
public class SyncWriter extends StepExecutionSupport implements ItemWriter<File>, ItemWriteListener<File> {
    private static Logger log = LoggerFactory.getLogger(SyncWriter.class);

    private SyncEndpoint endpoint;
    private File watchDir;
    private ContentStore contentStore;
    private String destinationSpaceId;
    private RestoreManager restoreManager;
    private String restorationId;

    /**
     * @param restorationId
     * @param watchDir
     * @param endpoint
     * @param contentStore
     * @param destinationSpaceId
     * @param restoreManager
     */
    public SyncWriter(String restorationId,
                      File watchDir,
                      SyncEndpoint endpoint,
                      ContentStore contentStore,
                      String destinationSpaceId,
                      RestoreManager restoreManager) {
        super();
        this.endpoint = endpoint;
        this.watchDir = watchDir;
        this.contentStore = contentStore;
        this.destinationSpaceId = destinationSpaceId;
        this.restoreManager = restoreManager;
        this.restorationId = restorationId;
    }

    // StepExecution Interface
    /*
     * (non-Javadoc)
     *
     * @see org.springframework.batch.core.StepExecutionListener#afterStep(org.
     * springframework.batch.core.StepExecution)
     */
    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        ExitStatus status = super.afterStep(stepExecution);

        List<String> errors = getErrors();
        if (errors.isEmpty()) {
            try {
                RestoreStatus newStatus = RestoreStatus.TRANSFER_TO_DURACLOUD_COMPLETE;
                restoreManager.transitionRestoreStatus(restorationId, newStatus, "");

                // restore the snapshot props file to the data directory.
                restoreFile(new File(this.watchDir.getParentFile(), Constants.SNAPSHOT_PROPS_FILENAME),
                            watchDir.getParentFile());
                return status.and(ExitStatus.COMPLETED);

            } catch (Exception e) {
                String message = "failed to transition restore status: " + e.getMessage();
                log.error(message, e);
                return status.and(ExitStatus.FAILED).addExitDescription(message);
            }

        } else {

            status = status.and(ExitStatus.FAILED);
            status.addExitDescription("Transfer to DuraCloud failed: " +
                                      errors.size() + " items failed.");
            for (String error : errors) {
                status.addExitDescription(error);
            }

            resetContextState();

            return status;
        }
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
        try {
            RestoreStatus newStatus = RestoreStatus.TRANSFERRING_TO_DURACLOUD;
            restoreManager.transitionRestoreStatus(restorationId, newStatus, "");
            Space space = this.contentStore.getSpace(destinationSpaceId, null, 1, null);
            if (!CollectionUtils.isEmpty(space.getContentIds())) {
                stepExecution.addFailureException(new RuntimeException("destination space "
                                                                       + destinationSpaceId +
                                                                       " must be empty to receive restored content"));
            }
        } catch (NotFoundException ex) {
            try {
                this.contentStore.createSpace(destinationSpaceId);
            } catch (ContentStoreException e) {
                addError(ex.getMessage());
                stepExecution.addFailureException(e);
            }
        } catch (Exception ex) {
            addError(ex.getMessage());
            stepExecution.addFailureException(ex);
        }
    }
    // ItemWriteListener interface

    /*
     * (non-Javadoc)
     *
     * @see
     * org.springframework.batch.core.ItemWriteListener#beforeWrite(java.util.
     * List)
     */
    @Override
    public void beforeWrite(List<? extends File> items) {
    }

    /*
     * (non-Javadoc)
     *
     * @see org.springframework.batch.item.ItemWriter#write(java.util.List)
     */
    @Override
    public void write(List<? extends File> items) throws Exception {
        log.info("starting to write {} file(s) to duracloud", items.size());
        for (final File file : items) {
            restoreFile(file, watchDir);
        }
    }

    /**
     * @param file
     * @throws Exception
     */
    private void restoreFile(final File file, final File watchDir) throws Exception {
        try {
            new Retrier().execute(new Retriable() {

                @Override
                public Object retry() throws Exception {
                    MonitoredFile monitoredFile = new MonitoredFile(file);
                    SyncResultType result =
                        endpoint.syncFileAndReturnDetailedResult(monitoredFile, watchDir);
                    if (result.equals(SyncResultType.FAILED)) {
                        String message = "Failed to upload " + file.getAbsolutePath() +
                                         " after uploading " +
                                         monitoredFile.getStreamBytesRead() +
                                         " of " + file.length() + " bytes.";
                        throw new Exception(message);
                    }

                    log.info("successfully uploaded {}: result = {}",
                             file.getAbsolutePath(), result);

                    return result;
                }
            });
        } catch (Exception ex) {
            addError(ex.getMessage());
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.springframework.batch.core.ItemWriteListener#afterWrite(java.util.
     * List)
     */
    @Override
    public void afterWrite(List<? extends File> items) {
        addToItemsRead(items.size());
    }

    // ItemWriteListener
    /*
     * (non-Javadoc)
     *
     * @see
     * org.springframework.batch.core.ItemWriteListener#onWriteError(java.lang.
     * Exception, java.util.List)
     */
    @Override
    public void onWriteError(Exception ex, List<? extends File> items) {
        log.error("Error writing item(s): " + items.toString(), ex);
    }

}

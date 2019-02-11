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

import org.duracloud.common.retry.Retriable;
import org.duracloud.common.retry.Retrier;
import org.duracloud.common.util.ChecksumUtil;
import org.duracloud.common.util.ChecksumUtil.Algorithm;
import org.duracloud.snapshot.dto.RestoreStatus;
import org.duracloud.snapshot.service.RestoreManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.ItemWriteListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.item.ItemWriter;

/**
 * This class verifies the manifest entry against the local file system.
 *
 * @author Daniel Bernstein
 * Date: Jul 28, 2015
 */
public class ManifestVerifier extends StepExecutionSupport implements ItemWriter<ManifestEntry>,
                                                                      ItemWriteListener<ManifestEntry> {

    private Logger log = LoggerFactory.getLogger(ManifestVerifier.class);
    private String restorationId;
    private File contentDir;
    private RestoreManager restoreManager;

    /**
     * @param restorationId
     * @param contentDir
     * @param restorationManager
     */
    public ManifestVerifier(String restorationId,
                            File contentDir,
                            RestoreManager restorationManager) {
        super();
        this.restorationId = restorationId;
        this.contentDir = contentDir;
        this.restoreManager = restorationManager;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.springframework.batch.core.ItemWriteListener#beforeWrite(java.util.
     * List)
     */
    @Override
    public void beforeWrite(List<? extends ManifestEntry> items) {
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.springframework.batch.core.ItemWriteListener#afterWrite(java.util.
     * List)
     */
    @Override
    public void afterWrite(List<? extends ManifestEntry> items) {
        addToItemsRead(items.size());
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.springframework.batch.core.ItemWriteListener#onWriteError(java.lang.
     * Exception, java.util.List)
     */
    @Override
    public void onWriteError(Exception exception, List<? extends ManifestEntry> items) {
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
            new Retrier().execute(new Retriable() {
                /*
                 * (non-Javadoc)
                 *
                 * @see org.duracloud.common.retry.Retriable#retry()
                 */
                @Override
                public Object retry() throws Exception {
                    RestoreStatus newStatus = RestoreStatus.VERIFYING_RETRIEVED_CONTENT;
                    restoreManager.transitionRestoreStatus(restorationId, newStatus, "");
                    return null;
                }
            });
        } catch (Exception ex) {
            addError("failed to transition status to "
                     + RestoreStatus.VERIFYING_RETRIEVED_CONTENT + ": " + ex.getMessage());
            stepExecution.addFailureException(ex);
            failExecution();
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
        ExitStatus status = stepExecution.getExitStatus();
        List<String> errors = getErrors();
        if (errors.size() > 0) {
            status = status.and(ExitStatus.FAILED);
            for (String error : errors) {
                status = status.addExitDescription(error);
            }

            resetContextState();
            stepExecution.upgradeStatus(BatchStatus.FAILED);
            stepExecution.setTerminateOnly();
            log.error("manifest verification finished: step_execution_id={} " +
                      "job_execution_id={} restore_id={} exit_status=\"{}\"",
                      stepExecution.getId(),
                      stepExecution.getJobExecutionId(),
                      restorationId,
                      status);

        } else {
            log.info("manifest verification finished:step_execution_id={} " +
                     "job_execution_id={} restore_id={} exit_status=\"{}\"",
                     stepExecution.getId(),
                     stepExecution.getJobExecutionId(),
                     restorationId,
                     status);

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
    public void write(List<? extends ManifestEntry> items) throws Exception {
        for (ManifestEntry entry : items) {
            try {
                String contentId = entry.getContentId();
                String checksum = entry.getChecksum();

                File file = new File(this.contentDir, contentId);
                if (!file.exists()) {
                    String message =
                        MessageFormat.format("content ({0}) not found in " +
                                             "path ({1}) for restore ({2})",
                                             contentId,
                                             file.getAbsolutePath(),
                                             restorationId);
                    log.error(message);
                    addError(message);
                } else {
                    ChecksumUtil checksumUtil = new ChecksumUtil(Algorithm.MD5);

                    String fileChecksum = checksumUtil.generateChecksum(file);
                    if (!checksum.equals(fileChecksum)) {
                        String message =
                            MessageFormat.format("content id ({0}) manifest " +
                                                 "checksum ({1})  does not match " +
                                                 "file ({2}) checksum ({3})",
                                                 contentId,
                                                 checksum,
                                                 file.getAbsolutePath(),
                                                 fileChecksum);
                        log.error(message);
                        addError(message);
                    } else {
                        log.debug("successfully verified entry {}", entry);
                    }
                }

            } catch (Exception ex) {
                String message = "failed to verify " + entry + ": " + ex.getMessage();
                log.error(message, ex);
                addError(message);
            }
        }
    }

}

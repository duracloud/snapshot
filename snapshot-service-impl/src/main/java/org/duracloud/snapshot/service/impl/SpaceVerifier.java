/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import java.text.MessageFormat;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicLong;

import org.duracloud.client.ContentStore;
import org.duracloud.common.constant.Constants;
import org.duracloud.common.retry.Retriable;
import org.duracloud.common.retry.Retrier;
import org.duracloud.manifeststitch.StitchedManifestGenerator;
import org.duracloud.snapshot.dto.RestoreStatus;
import org.duracloud.snapshot.service.RestoreManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.ItemWriteListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.ItemWriter;

/**
 * This class verifies that the manifest entry's checksum matches the checksum
 * of the item in the destination space.
 * 
 * @author Daniel Bernstein 
 *         Date: Jul 29, 2015
 */
public class SpaceVerifier extends StepExecutionSupport
    implements ItemWriter<ManifestEntry>, ItemWriteListener<ManifestEntry> {

    private Logger log = LoggerFactory.getLogger(SpaceVerifier.class);
    private String spaceId;
    private RestoreManager restoreManager;
    private String restoreId;
    private SpaceManifestDpnManifestVerifier verifier;
    /**
     * 
     * @param contentStore
     */
    public SpaceVerifier(String restoreId, SpaceManifestDpnManifestVerifier verifier, String spaceId, RestoreManager restoreManager) {
        this.restoreId = restoreId;
        this.verifier = verifier;
        this.spaceId = spaceId;
        this.restoreManager = restoreManager;
    }

    public void beforeWrite(List<? extends ManifestEntry> items) {}
    public void afterWrite(List<? extends ManifestEntry> items) {}
    public void onWriteError(Exception ex, List<? extends ManifestEntry> items) {}
    public void write(List<? extends ManifestEntry> items) throws Exception {}


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
                    RestoreStatus newStatus = RestoreStatus.VERIFYING_TRANSFERRED_CONTENT;
                    restoreManager.transitionRestoreStatus(restoreId, newStatus, "");
                    return null;
                }
            });
        } catch (Exception ex) {
            addError("failed to transition status to "
                + RestoreStatus.VERIFYING_TRANSFERRED_CONTENT + ": " + ex.getMessage());
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
    public ExitStatus afterStep(final StepExecution stepExecution) {
        if (getErrors().size() == 0) {
            List<String> verifyErrors = verifySpace(verifier);
            for(String error : verifyErrors){
                addError(error);
            }
        }

        ExitStatus status = stepExecution.getExitStatus();
        List<String> errors = getErrors();
        if (errors.size() > 0) {
            status = status.and(ExitStatus.FAILED);

            for (String error : errors) {
                status = status.addExitDescription(error);
            }

            failExecution();

            log.error("space verification step finished: step_execution_id={} "
                + "job_execution_id={}  spaceId={} status=\"{}\"",
                      stepExecution.getId(),
                      stepExecution.getJobExecutionId(),
                      spaceId,
                      status);
        } else {

            status = status.and(ExitStatus.COMPLETED);
            log.info("space verification step finished: step_execution_id={} "
                + "job_execution_id={}  spaceId={} exit_status={} ",
                     stepExecution.getId(),
                     stepExecution.getJobExecutionId(),
                     spaceId,
                     status);
        }

        return status;
    }


}

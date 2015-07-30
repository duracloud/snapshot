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
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.duracloud.common.util.ChecksumUtil;
import org.duracloud.common.util.ChecksumUtil.Algorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.ItemWriteListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.ItemWriter;

/**
 * This class verifies the manifest entry against the local file system.
 * 
 * @author Daniel Bernstein Date: Jul 28, 2015
 */
public class ManifestVerifier
    implements ItemWriter<ManifestEntry>, StepExecutionListener, ItemWriteListener<ManifestEntry> {
    private Logger log = LoggerFactory.getLogger(ManifestVerifier.class);
    private String restorationId;
    private File contentDir;
    private List<String> errors;
    private ChecksumUtil checksumUtil = new ChecksumUtil(Algorithm.MD5);

    /**
     * @param restorationId
     * @param contentDir
     * @param restoreManager
     */
    public ManifestVerifier(String restorationId, File contentDir) {
        this.restorationId = restorationId;
        this.contentDir = contentDir;
        this.errors = new LinkedList<>();
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
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.springframework.batch.core.StepExecutionListener#afterStep(org.
     * springframework.batch.core.StepExecution)
     */
    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        if (this.errors.size() > 0) {
            log.error("manifest verification finished: result=failed step_execution_id={} job_execution_id={} restore_id={} errors=\"{}\"",
                      stepExecution.getId(),
                      stepExecution.getJobExecutionId(),
                      restorationId,
                      StringUtils.join(errors, ", \n"));
            return ExitStatus.FAILED;
        } else {
            log.info("manifest verification finished: result=success step_execution_id={} job_execution_id={} restore_id={}",
                     stepExecution.getId(),
                     stepExecution.getJobExecutionId(),
                     restorationId);
            return ExitStatus.COMPLETED;
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.springframework.batch.item.ItemWriter#write(java.util.List)
     */
    @Override
    public void write(List<? extends ManifestEntry> items) throws Exception {
        for (ManifestEntry entry : items) {
            String contentId = entry.getContentId();
            String checksum = entry.getChecksum();

            File file = new File(this.contentDir.getAbsolutePath() + File.separator + contentId);
            if (!file.exists()) {
                String message =
                    MessageFormat.format("content (\"{0}\") not found in path ({1}) for restore ({2})",
                                         contentId,
                                         file.getAbsolutePath(),
                                         restorationId);
                log.error(message);
                errors.add(message);
            } else {
                String fileChecksum = checksumUtil.generateChecksum(file);
                if (!checksum.equals(fileChecksum)) {
                    String message =
                        MessageFormat.format("content id's (\"{0}\")  manifest checksum ({1}) does not match file's checksum",
                                             contentId,
                                             file.getAbsolutePath());
                    log.error(message);
                    errors.add(message);
                } else {
                    log.debug("successfully verified entry {}", entry);
                }
            }
        }
    }

}

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

import org.apache.commons.lang3.StringUtils;
import org.duracloud.client.ContentStore;
import org.duracloud.common.retry.Retriable;
import org.duracloud.common.retry.Retrier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.ItemWriteListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.ItemWriter;

/**
 * This class verifies that the manifest entry's md5 matches the md5 of the item
 * in the destination space.
 * 
 * @author Daniel Bernstein 
 *         Date: Jul 29, 2015
 */
public class SpaceVerifier
    implements ItemWriter<ManifestEntry>, StepExecutionListener, ItemWriteListener<ManifestEntry> {

    private Logger log = LoggerFactory.getLogger(SpaceVerifier.class);
    private ContentStore contentStore;
    private String spaceId;
    private AtomicLong manifestEntryCount = new AtomicLong(0);
    private List<String> errors = new LinkedList<>();

    /**
     * 
     * @param contentStore
     */
    public SpaceVerifier(ContentStore contentStore, String spaceId) {
        this.contentStore = contentStore;
        this.spaceId = spaceId;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.springframework.batch.core.ItemWriteListener#beforeWrite(java.util.
     * List)
     */
    @Override
    public void beforeWrite(List<? extends ManifestEntry> items) {}

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.springframework.batch.core.ItemWriteListener#afterWrite(java.util.
     * List)
     */
    @Override
    public void afterWrite(List<? extends ManifestEntry> items) {}

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.springframework.batch.core.ItemWriteListener#onWriteError(java.lang.
     * Exception, java.util.List)
     */
    @Override
    public void onWriteError(Exception exception, List<? extends ManifestEntry> items) {}

    /*
     * (non-Javadoc)
     * 
     * @see org.springframework.batch.core.StepExecutionListener#beforeStep(org.
     * springframework.batch.core.StepExecution)
     */
    @Override
    public void beforeStep(StepExecution stepExecution) {
        this.manifestEntryCount.set(0);
        this.errors.clear();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.springframework.batch.core.StepExecutionListener#afterStep(org.
     * springframework.batch.core.StepExecution)
     */
    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        if (errors.size() == 0) {
            try {
                long spaceCount = new Retrier().execute(new Retriable() {

                    @Override
                    public Object retry() throws Exception {
                        Long count = 0l;
                        Iterator<String> iterator = contentStore.getSpaceContents(spaceId);
                        try {
                            while (iterator.next() != null) {
                                count++;
                            }
                        } catch (NoSuchElementException ex) {
                        }
                        return count;
                    }
                });

                if (spaceCount != manifestEntryCount.get()) {
                    errors.add(MessageFormat.format("counts do not match: step_execution_id={0}  job_execution_id={1} store_id={2} spaceId={3} manifest_count={4} "
                        + "space_count={5}",
                                                    stepExecution.getId(),
                                                    stepExecution.getJobExecutionId(),
                                                    contentStore.getStoreId(),
                                                    spaceId,
                                                    manifestEntryCount.get(),
                                                    spaceCount));
                }

            } catch (Exception ex) {
                errors.add(MessageFormat.format("failed to count items in space:  step_execution_id={0} job_execution_id={1} store_id={2} spaceId={3} message=\"{4}\"",
                                                stepExecution.getId(),
                                                stepExecution.getJobExecutionId(),
                                                contentStore.getStoreId(),
                                                spaceId,
                                                ex.getMessage()));
            }
        }

        ExitStatus status = errors.size() == 0 ? ExitStatus.COMPLETED : ExitStatus.FAILED;

        if (status == ExitStatus.COMPLETED) {
            log.info("space verification finished: result=success step_execution_id={} job_execution_id={} store_id={} spaceId={}",
                     stepExecution.getId(),
                     stepExecution.getJobExecutionId(),
                     contentStore.getStoreId(),
                     spaceId);
        } else {
            log.error("space verification finished: result=failed step_execution_id={} job_execution_id={} store_id={} spaceId={} errors=\"{}\"",
                      stepExecution.getId(),
                      stepExecution.getJobExecutionId(),
                      contentStore.getStoreId(),
                      spaceId,
                      StringUtils.join(errors, ", \n"));
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
        for (final ManifestEntry item : items) {
            try {
                new Retrier().execute(new Retriable() {
                    @Override
                    public Object retry() throws Exception {
                        String contentId = item.getContentId();
                        Map<String, String> props = contentStore.getContentProperties(spaceId, contentId);
                        String remoteItemChecksum = props.get(ContentStore.CONTENT_CHECKSUM);
                        String manifestChecksum = item.getChecksum();
                        if (!manifestChecksum.equals(remoteItemChecksum)) {
                            throw new Exception(MessageFormat.format("Checksums do not match: store_id={0}, spaceId={1}, "
                                + "contentId={2}, manifest_checksum={3}, " + "content_checksum_property={4}",
                                                                     contentStore.getStoreId(),
                                                                     spaceId,
                                                                     contentId,
                                                                     manifestChecksum,
                                                                     remoteItemChecksum));
                        }

                        log.debug("Checksums match: store_id={}, spaceId={}, contentId={}",
                                  contentStore.getStoreId(),
                                  spaceId,
                                  contentId);
                        manifestEntryCount.incrementAndGet();
                        return null;
                    }
                });

            } catch (Exception ex) {
                errors.add(ex.getMessage());
            }
        }
    }
}

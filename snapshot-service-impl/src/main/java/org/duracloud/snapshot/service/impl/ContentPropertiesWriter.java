/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import org.duracloud.client.ContentStore;
import org.duracloud.common.constant.Constants;
import org.duracloud.common.retry.Retriable;
import org.duracloud.common.retry.Retrier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.ItemWriteListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.ItemWriter;

import java.util.List;

/**
 * @author Daniel Bernstein 
 *         Date: Aug 22, 2014
 */
public class ContentPropertiesWriter
    implements ItemWriter<ContentProperties>, StepExecutionListener,
    ItemWriteListener<ContentProperties> {
    private static Logger log = LoggerFactory.getLogger(ContentPropertiesWriter.class);
    private String destinationSpaceId;
    private ContentStore contentStore;
    private String storeId; 
    private String storageProviderType;
    /**
     * @param contentStore
     * @param destinationSpaceId
     */
    public ContentPropertiesWriter(
        ContentStore contentStore, String destinationSpaceId) {
        this.contentStore = contentStore;
        this.destinationSpaceId = destinationSpaceId;
        this.storeId = contentStore.getStoreId();
        this.storageProviderType = contentStore.getStorageProviderType();
        log.debug("constructed ContentPropertiesWriter for destination spaceId ({}), " +
                  "storeId ({}), storeType({})",
                  destinationSpaceId,
                  storeId,
                  storageProviderType);
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
        log.debug("firing:  status = {}", status);
        return status;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.springframework.batch.core.ItemWriteListener#afterWrite(java.util
     * .List)
     */
    @Override
    public void afterWrite(List<? extends ContentProperties> items) {
        log.debug("firing afterWrite {}", items);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.springframework.batch.core.StepExecutionListener#beforeStep(org.
     * springframework.batch.core.StepExecution)
     */
    @Override
    public void beforeStep(StepExecution stepExecution) {
        log.debug("firing beforeStep {}", stepExecution);

    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.springframework.batch.core.ItemWriteListener#beforeWrite(java.util
     * .List)
     */
    @Override
    public void beforeWrite(List<? extends ContentProperties> items) {
        log.debug("firing beforeWrite {}", items);

    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.springframework.batch.core.ItemWriteListener#onWriteError(java.lang
     * .Exception, java.util.List)
     */
    @Override
    public void onWriteError(Exception exception,
                             List<? extends ContentProperties> items) {
        log.error("firing onWriteError: currrently not handling: exception message=" +
                  exception.getMessage(), exception);
        for(ContentProperties props : items){
            log.error("item failed: " + props);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.springframework.batch.item.ItemWriter#write(java.util.List)
     */
    @Override
    public void write(List<? extends ContentProperties> items) throws Exception {
        for (final ContentProperties props : items) {
            new Retrier().execute(new Retriable() {

                @Override
                public Object retry() throws Exception {
                    contentStore.setContentProperties(destinationSpaceId,
                                                      props.getContentId(),
                                                      props.getProperties());
                    log.debug("wrote content properties ({}) " +
                              "to space ({}) on store ({}/{}):",
                              props,
                              destinationSpaceId,
                              storeId,
                              storageProviderType);
                    return null;
                }
            });
        }
    }

}

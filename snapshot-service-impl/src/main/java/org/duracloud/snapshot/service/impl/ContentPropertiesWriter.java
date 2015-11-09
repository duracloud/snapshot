/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.duracloud.chunk.manifest.ChunksManifest;
import org.duracloud.client.ContentStore;
import org.duracloud.common.retry.Retriable;
import org.duracloud.common.retry.Retrier;
import org.duracloud.error.ContentStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.ItemWriteListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.item.ItemWriter;

/**
 * @author Daniel Bernstein 
 *         Date: Aug 22, 2014
 */
public class ContentPropertiesWriter extends StepExecutionSupport
    implements ItemWriter<ContentProperties>, ItemWriteListener<ContentProperties> {
    private static Logger log = LoggerFactory.getLogger(ContentPropertiesWriter.class);
    private String destinationSpaceId;
    private ContentStore contentStore;
    private String storeId;
    private String storageProviderType;

    /**
     * @param contentStore
     * @param destinationSpaceId
     */
    public ContentPropertiesWriter(ContentStore contentStore, String destinationSpaceId) {
        this.contentStore = contentStore;
        this.destinationSpaceId = destinationSpaceId;
        this.storeId = contentStore.getStoreId();
        this.storageProviderType = contentStore.getStorageProviderType();
        log.debug("constructed ContentPropertiesWriter for destination spaceId ({}), " + "storeId ({}), storeType({})",
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
        List<String> errors = getErrors();
        if (errors.size() > 0) {
            status = status.and(ExitStatus.FAILED);

            for (String error : errors) {
                status = status.addExitDescription(error);
            }

            failExecution();
            resetContextState();
            
            log.error("content properties step finished with errors: step_execution_id={} "
                + "job_execution_id={} store_id={} status=\"{}\"",
                      stepExecution.getId(),
                      stepExecution.getJobExecutionId(),
                      contentStore.getStoreId(),
                      status);
        } else {

            status = status.and(ExitStatus.COMPLETED);
            log.info("content properties step finished: step_execution_id={} "
                + "job_execution_id={} store_id={}  exit_status={} ",
                     stepExecution.getId(),
                     stepExecution.getJobExecutionId(),
                     contentStore.getStoreId(),
                     status);
        }

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
        addToItemsRead(items.size());
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
    public void onWriteError(Exception exception, List<? extends ContentProperties> items) {
        log.error("firing onWriteError: currrently not handling: exception message=" + exception.getMessage(),
                  exception);
        for (ContentProperties props : items) {
            addError("item failed: " + props + "; exception="+exception.getMessage());
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
                    try {
                    contentStore.setContentProperties(destinationSpaceId, props.getContentId(), props.getProperties());
                    log.debug("wrote content properties ({}) to space ({}) on store ({}/{}):",
                              props,
                              destinationSpaceId,
                              storeId,
                              storageProviderType);
                    return null;
                    }catch(ContentStoreException ex){
                        log.warn("failed to update content properties ({}) to space({}); on store({}/{}). Trying with chunk manifest extension.",
                                 props,
                                 destinationSpaceId,
                                 storeId,
                                 storageProviderType);

                        //make sure the chunk manifest's properties are layed on top of the stitched file 
                        //properties to ensure that mutable "system-ish" properties such as content type are not
                        //overwritten.
                        String manifestId = props.getContentId() + ChunksManifest.manifestSuffix;
                        Map<String,String> properties = new HashMap<>(props.getProperties());
                        Map<String, String> chunkManifestProperties =
                            contentStore.getContentProperties(destinationSpaceId, manifestId);
                        properties.putAll(chunkManifestProperties);
                        contentStore.setContentProperties(destinationSpaceId,
                                                          manifestId,
                                                          properties);                        
                        return null;
                    }
                }
            });
        }
    }

}

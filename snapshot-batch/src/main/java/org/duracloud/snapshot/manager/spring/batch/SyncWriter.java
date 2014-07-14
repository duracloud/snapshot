/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.manager.spring.batch;

import java.io.File;
import java.util.List;

import org.duracloud.client.ContentStore;
import org.duracloud.common.retry.Retriable;
import org.duracloud.common.retry.Retrier;
import org.duracloud.domain.Space;
import org.duracloud.error.ContentStoreException;
import org.duracloud.error.NotFoundException;
import org.duracloud.sync.endpoint.MonitoredFile;
import org.duracloud.sync.endpoint.SyncEndpoint;
import org.duracloud.sync.endpoint.SyncResultType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.ItemWriteListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.ItemWriter;
import org.springframework.util.CollectionUtils;

/**
 * @author Daniel Bernstein Date: Jul 17, 2014
 */
public class SyncWriter
    implements ItemWriter<File>, StepExecutionListener, ItemWriteListener<File> {
    private static Logger log = LoggerFactory.getLogger(SyncWriter.class);
    
    private SyncEndpoint endpoint;
    private File watchDir;
    private ContentStore contentStore;
    private String destinationSpaceId;
    
    
    /**
     * @param endpoint
     * @param watchDir
     * @param contentStore 
     * @param string 
     */
    public SyncWriter(File watchDir, SyncEndpoint endpoint, ContentStore contentStore, String destinationSpaceId) {
        super();
        this.endpoint = endpoint;
        this.watchDir = watchDir;
        this.contentStore = contentStore;
        this.destinationSpaceId = destinationSpaceId;
    }

    //StepExecution Interface
    /* (non-Javadoc)
     * @see org.springframework.batch.core.StepExecutionListener#afterStep(org.springframework.batch.core.StepExecution)
     */
    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        return stepExecution.getExitStatus();
    }

    /* (non-Javadoc)
     * @see org.springframework.batch.core.StepExecutionListener#beforeStep(org.springframework.batch.core.StepExecution)
     */
    @Override
    public void beforeStep(StepExecution stepExecution) {
       try {
           Space space = this.contentStore.getSpace(destinationSpaceId, null, 1, null);
           if(!CollectionUtils.isEmpty(space.getContentIds())){
                stepExecution.addFailureException(new RuntimeException("destination space "
                    + destinationSpaceId
                    + " must be empty to receive restored content"));
           }
        } catch (NotFoundException ex) {
            try {
                this.contentStore.createSpace(destinationSpaceId);
            } catch (ContentStoreException e) {
                stepExecution.addFailureException(e);
            }
        } catch (ContentStoreException ex) {
            stepExecution.addFailureException(ex);
        }
    }    
    //ItemWriteListener interface

    /* (non-Javadoc)
     * @see org.springframework.batch.core.ItemWriteListener#beforeWrite(java.util.List)
     */
    @Override
    public void beforeWrite(List<? extends File> items) {}
    
    /* (non-Javadoc)
     * @see org.springframework.batch.item.ItemWriter#write(java.util.List)
     */
    @Override
    public void write(List<? extends File> items) throws Exception {
        log.info("starting to write " + items.size() + " file(s) to duracloud");
        for(final File file : items){
            new Retrier().execute(new Retriable() {
                
                @Override
                public Object retry() throws Exception {
                    MonitoredFile monitoredFile = new MonitoredFile(file);
                    SyncResultType result =  endpoint.syncFileAndReturnDetailedResult(monitoredFile, watchDir);
                    if(result.equals(SyncResultType.FAILED)){
                        String message = "Failed to upload "
                            + file.getAbsolutePath() + " after uploading "
                            + monitoredFile.getStreamBytesRead() + " of "
                            + file.length() + " bytes.";
                        throw new Exception(message);
                    }
                    
                    log.info("successfully uploaded " + file.getAbsolutePath() + ": result = " + result);
                    
                    return result;
                }
            });
        }
    }

    
    /* (non-Javadoc)
     * @see org.springframework.batch.core.ItemWriteListener#afterWrite(java.util.List)
     */
    @Override
    public void afterWrite(List<? extends File> items) {}

    //ItemWriteListener
    /* (non-Javadoc)
     * @see org.springframework.batch.core.ItemWriteListener#onWriteError(java.lang.Exception, java.util.List)
     */
    @Override
    public void onWriteError(Exception ex, List<? extends File> items) {
        log.error("Error writing item(s): " + items.toString(), ex);
        //TODO How should this be properly handled.
        //the interface doesn't supply any information about which items were processed
        //and which one(s) failed.
    }


}

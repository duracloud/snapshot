/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.MessageFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.duracloud.client.ContentStore;
import org.duracloud.common.collection.WriteOnlyStringSet;
import org.duracloud.common.constant.Constants;
import org.duracloud.common.retry.Retriable;
import org.duracloud.common.retry.Retrier;
import org.duracloud.snapshot.db.model.SnapshotContentItem;
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
 * @author Daniel Bernstein
 *         Date: Jul 29, 2015
 */
public class SnapshotContentItemVerifier  
        implements ItemWriter<SnapshotContentItem>, 
                   StepExecutionListener, 
                   ItemWriteListener<SnapshotContentItem>{

    private Logger log = LoggerFactory.getLogger(SpaceVerifier.class);
    private File manifestFile;
    private String restoreId;
    private WriteOnlyStringSet manifestSet;
    private String snapshotName;
    private List<String> errors;
    private AtomicLong snapshotContentItemCount;
    private RestoreManager restoreManager;

    /**
     * @param restoreId
     * @param manifestFile
     * @param snapshotName
     * @param restoreManager
     */
    public SnapshotContentItemVerifier(String restoreId,
                                       File manifestFile,
                                       String snapshotName,
                                       RestoreManager restoreManager) {
        this.restoreId = restoreId;
        this.manifestFile = manifestFile;
        this.snapshotName = snapshotName;
        this.errors = new LinkedList<>();
        this.snapshotContentItemCount = new AtomicLong(0);
        this.restoreManager = restoreManager;
    }

    /* (non-Javadoc)
     * @see org.springframework.batch.core.ItemWriteListener#beforeWrite(java.util.List)
     */
    @Override
    public void beforeWrite(List<? extends SnapshotContentItem> items) {
        
    }

    /* (non-Javadoc)
     * @see org.springframework.batch.core.ItemWriteListener#afterWrite(java.util.List)
     */
    @Override
    public void afterWrite(List<? extends SnapshotContentItem> items) {}

    /* (non-Javadoc)
     * @see org.springframework.batch.core.ItemWriteListener#onWriteError(java.lang.Exception, java.util.List)
     */
    @Override
    public void onWriteError(Exception exception, List<? extends SnapshotContentItem> items) {}

    /* (non-Javadoc)
     * @see org.springframework.batch.core.StepExecutionListener#beforeStep(org.springframework.batch.core.StepExecution)
     */
    @Override
    public void beforeStep(StepExecution stepExecution) {

        if(this.manifestSet == null){
            int count = 0;
            
            try(
                BufferedReader breader =
                    new BufferedReader(new FileReader(this.manifestFile))){
                while(breader.readLine() != null){
                    count++;
                }
            }catch(Exception ex){
                throw new RuntimeException(ex);
            }

            this.manifestSet = new WriteOnlyStringSet(count);

            try {
                DpnManifestReader reader = new DpnManifestReader(this.manifestFile);
                
                ManifestEntry entry = null;
                while((entry = reader.read()) != null){
                    this.manifestSet.add(formatManifestSetString(entry.getContentId(), 
                                                                 entry.getChecksum()));
                }
            
                new Retrier().execute(new Retriable(){
                    /* (non-Javadoc)
                     * @see org.duracloud.common.retry.Retriable#retry()
                     */
                    @Override
                    public Object retry() throws Exception {
                        RestoreStatus newStatus =
                            RestoreStatus.VERIFYING_SNAPSHOT_REPO_AGAINST_MANIFEST;
                        restoreManager.transitionRestoreStatus(restoreId, newStatus, "");
                        return null;
                    }
                });
            
            } catch (Exception ex) {
                this.errors.add("failed to transition status to " +
                    RestoreStatus.VERIFYING_SNAPSHOT_REPO_AGAINST_MANIFEST + ": " + 
                    ex.getMessage());
                stepExecution.addFailureException(ex);
            }
            
            
        }
    }

    /**
     * @param contentId
     * @param checksum
     * @return
     */
    private String formatManifestSetString(String contentId, String checksum) {
        return new StringBuilder().append(contentId)
                                  .append(":")
                                  .append(checksum)
                                  .toString();
    }

    /* (non-Javadoc)
     * @see org.springframework.batch.core.StepExecutionListener#afterStep(org.springframework.batch.core.StepExecution)
     */
    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        try{

            // compare counts (which should not include SNAPSHOT_PROPS_FILENAME
            // on the snapshot repo side since it does not get written to the manifest.
            if(snapshotContentItemCount.get() == this.manifestSet.size()){
                log.debug("snapshot repo count matches manifest count: " +
                          "step_execution_id={} job_execution_id={} snapshot_name={}",
                          stepExecution.getId(),
                          stepExecution.getJobExecutionId(),
                          this.snapshotName);
            }else{
                errors.add("snapshot ("+snapshotName+") content item count (" +
                           snapshotContentItemCount.get() +
                           ") does not match manifest count (" + manifestSet.size() + ")");
            }

            
            ExitStatus status = stepExecution.getExitStatus();
            
            if(this.errors.size() > 0){
                status = status.and(ExitStatus.FAILED);
                
                for(String error:errors){
                    status = status.addExitDescription(error);
                }

                stepExecution.upgradeStatus(BatchStatus.FAILED);
                stepExecution.setTerminateOnly();
                log.error("snapshot repo verification finished: step_execution_id={} " +
                          "job_execution_id={} snapshot_name={} status=\"{}\"",
                          stepExecution.getId(),
                          stepExecution.getJobExecutionId(),
                          snapshotName,
                          status);                
            }else{
                status = status.and(ExitStatus.COMPLETED);
            }
            
            return status;
        }finally{
            this.manifestSet = null;
            this.snapshotContentItemCount.set(0);
        }

    }

    /* (non-Javadoc)
     * @see org.springframework.batch.item.ItemWriter#write(java.util.List)
     */
    @Override
    public void write(List<? extends SnapshotContentItem> items) throws Exception {
        for(SnapshotContentItem item : items){
            Map<String,String> props = PropertiesSerializer.deserialize(item.getMetadata());
            String contentId = item.getContentId();
            String checksum = props.get(ContentStore.CONTENT_CHECKSUM);
            
            //verify that manifest contains every item from the database except SNAPSHOT_PROPS_FILENAME
            if(!contentId.equals(Constants.SNAPSHOT_PROPS_FILENAME)){
                if(!this.manifestSet.contains(formatManifestSetString(contentId,checksum))){
                    errors.add(
                        MessageFormat.format(
                            "Content item {0} with checksum {1} not found in manifest " +
                            "for snapshot {2}",
                        contentId, checksum,
                        this.snapshotName));
                }
                snapshotContentItemCount.incrementAndGet();
            }
        }
    }

}

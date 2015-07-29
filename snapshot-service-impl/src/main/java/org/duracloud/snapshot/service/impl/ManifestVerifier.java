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

import org.duracloud.snapshot.service.RestoreManager;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.ItemWriteListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.ItemWriter;

/**
 * @author Daniel Bernstein
 *         Date: Jul 28, 2015
 */
public class ManifestVerifier  implements ItemWriter<ManifestEntry>, StepExecutionListener, ItemWriteListener<ManifestEntry>{

    /**
     * @param restorationId
     * @param contentDir
     * @param restoreManager
     */
    public ManifestVerifier(String restorationId, File contentDir, RestoreManager restoreManager) {
        // TODO Auto-generated constructor stub
    }

    /* (non-Javadoc)
     * @see org.springframework.batch.core.ItemWriteListener#beforeWrite(java.util.List)
     */
    @Override
    public void beforeWrite(List<? extends ManifestEntry> items) {
        // TODO Auto-generated method stub
        
    }

    /* (non-Javadoc)
     * @see org.springframework.batch.core.ItemWriteListener#afterWrite(java.util.List)
     */
    @Override
    public void afterWrite(List<? extends ManifestEntry> items) {
        // TODO Auto-generated method stub
        
    }

    /* (non-Javadoc)
     * @see org.springframework.batch.core.ItemWriteListener#onWriteError(java.lang.Exception, java.util.List)
     */
    @Override
    public void onWriteError(Exception exception, List<? extends ManifestEntry> items) {
        // TODO Auto-generated method stub
        
    }

    /* (non-Javadoc)
     * @see org.springframework.batch.core.StepExecutionListener#beforeStep(org.springframework.batch.core.StepExecution)
     */
    @Override
    public void beforeStep(StepExecution stepExecution) {
        // TODO Auto-generated method stub
        
    }

    /* (non-Javadoc)
     * @see org.springframework.batch.core.StepExecutionListener#afterStep(org.springframework.batch.core.StepExecution)
     */
    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see org.springframework.batch.item.ItemWriter#write(java.util.List)
     */
    @Override
    public void write(List<? extends ManifestEntry> items) throws Exception {
        // TODO Auto-generated method stub
        
    }

}

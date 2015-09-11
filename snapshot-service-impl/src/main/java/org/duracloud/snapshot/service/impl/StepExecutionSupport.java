/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.ExecutionContext;

/**
 * This class serves as a base class for item readers and writers.
 * 
 * @author Daniel Bernstein Date: Jul 28, 2015
 */
public abstract class StepExecutionSupport implements StepExecutionListener {

    private Logger log = LoggerFactory.getLogger(StepExecutionSupport.class);
    private StepExecution stepExecution;
    private static String ITEMS_READ_KEY = "lines.read";
    private static final String ERRORS_KEY = "errors";

    protected ExecutionContext getExecutionContext() {
        return this.stepExecution.getExecutionContext();
    }

    protected synchronized void addError(String error){
        synchronized(this.stepExecution){
            List<String> errors = getErrors();
            errors.add(error);
            getExecutionContext().put(ERRORS_KEY, errors);
        }
    }

    /**
     * @return
     */
    protected List<String> getErrors() {
        List<String> errors = (List<String>) getExecutionContext().get(ERRORS_KEY);
        if (errors == null) {
            errors = new LinkedList<>();
        }
        return errors;
    }

    protected void addToItemsRead(long value){
        addToLong(ITEMS_READ_KEY, value);
    }
    
    protected long getItemsRead(){
        return getLongValue(ITEMS_READ_KEY);
    }
    
    /**
     * @param it
     */
    protected void skipLinesAlreadyRead(Iterator it) {
        long linesRead = getItemsRead();
        if(linesRead > 0){
            for(long i = 0; i < linesRead; i++){
                if(it.hasNext()){
                    it.next();
                }
            }
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
        this.stepExecution = stepExecution;
    }
    
    protected StepExecution getStepExecution(){
        return this.stepExecution;
    }

    /**
     * 
     */
    protected void failExecution() {
        this.stepExecution.upgradeStatus(BatchStatus.FAILED);
        this.stepExecution.setTerminateOnly();
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
        return status;
    }

    /**
     * Adds the specified value to the existing key.
     * @param key
     * @param value
     */
    protected void addToLong(String key, long value) {
        synchronized(this.stepExecution){
            long currentValue = getLongValue(key);
            getExecutionContext().putLong(key,currentValue+value);
        }

    }

    /**
     * @param key
     * @return
     */
    protected long getLongValue(String key) {
        synchronized(this.stepExecution){
           return getExecutionContext().getLong(key,0l);
        }

    }


}

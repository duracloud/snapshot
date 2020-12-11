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

import org.duracloud.common.retry.ExceptionHandler;
import org.duracloud.common.retry.Retriable;
import org.duracloud.common.retry.Retrier;
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
    public static String ITEMS_READ_KEY = "lines.read";
    public static final String ERRORS_KEY = "errors";
    private boolean test = false;

    protected ExecutionContext getExecutionContext() {
        return this.stepExecution.getExecutionContext();
    }

    protected synchronized void addError(String error) {
        synchronized (this.stepExecution) {
            List<String> errors = getErrors();
            errors.add(error);
            getExecutionContext().put(ERRORS_KEY, errors);
        }
    }

    protected synchronized void clearErrors() {
        synchronized (this.stepExecution) {
            List<String> errors = new LinkedList<>();
            getExecutionContext().put(ERRORS_KEY, errors);
        }
    }

    protected void resetContextState() {
        //items read state variable must be set back to zero to
        //ensure that the step will be run from top of the list on failure.
        addToItemsRead(getItemsRead() * -1);
        clearErrors();
    }

    /**
     * @return a list of errors from the execution context or empty list if there are no errors
     */
    protected List<String> getErrors() {
        List<String> errors = (List<String>) getExecutionContext().get(ERRORS_KEY);
        if (errors == null) {
            errors = new LinkedList<>();
        }
        return errors;
    }

    protected void addToItemsRead(long value) {
        addToLong(ITEMS_READ_KEY, value);
    }

    protected long getItemsRead() {
        return getLongValue(ITEMS_READ_KEY);
    }

    /**
     * Skips the iterator ahead to the items read value stored in the execution context
     *
     * @param it any iterator
     */
    protected void skipLinesAlreadyRead(Iterator it) {
        long linesRead = getItemsRead();
        if (linesRead > 0) {
            for (long i = 0; i < linesRead; i++) {
                if (it.hasNext()) {
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

    protected StepExecution getStepExecution() {
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
        this.stepExecution = stepExecution;
        ExitStatus status = stepExecution.getExitStatus();
        return status;
    }

    /**
     * Adds the specified value to the existing key.
     *
     * @param key
     * @param value
     */
    protected void addToLong(String key, long value) {
        synchronized (this.stepExecution) {
            long currentValue = getLongValue(key);
            getExecutionContext().putLong(key, currentValue + value);
        }

    }

    protected long getLongValue(String key) {
        synchronized (this.stepExecution) {
            return getExecutionContext().getLong(key, 0l);
        }

    }

    protected List<String> verifySpace(final SpaceManifestSnapshotManifestVerifier verifier) {
        List<String> errors = new LinkedList<>();
        String spaceId = verifier.getSpaceId();
        boolean verified = false;
        try {
            verified = new Retrier(4, 60000, 2).execute(new Retriable() {
                @Override
                public Object retry() throws Exception {
                    boolean result = verifier.verify();
                    if (!result && !test) {
                        String message = "verification failed.  Retrying...";
                        log.warn(message);
                        throw new Exception(message);
                    }
                    return result;
                }
            }, new ExceptionHandler() {
                @Override
                public void handle(Exception ex) {
                    log.warn("failed to verify: " + ex.getMessage());
                }
            });

        } catch (Exception e) {
            String message =
                MessageFormat.format("unexpected error during space verification: step_execution_id={0} "
                                     + "job_execution_id={1}  spaceId={2}: message={3}",
                                     stepExecution.getId(),
                                     stepExecution.getJobExecutionId(),
                                     spaceId,
                                     e.getMessage());
            log.error(message);
        }

        if (!verified) {
            for (String error : verifier.getErrors()) {
                errors.add(error);
            }

            errors.add(MessageFormat.format("space manifest does not match the snapshot manifest: " +
                                            "step_execution_id={0} job_execution_id={1}  spaceId={2}",
                                            stepExecution.getId(),
                                            stepExecution.getJobExecutionId(),
                                            spaceId));
        }

        return errors;
    }

    public void setIsTest() {
        this.test = true;
    }

}

/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.manager.spring.batch;

import org.duracloud.snapshot.manager.SnapshotException;
import org.duracloud.snapshot.manager.config.SnapshotJobManagerConfig;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;

/**
 * @author Daniel Bernstein
 *         Date: Jul 25, 2014
 */
public interface BatchJobBuilder<T> {
    /**
     * Creates a job for the specified entity;
     * @param entity
     * @param config
     * @return
     */
    Job buildJob(T entity, SnapshotJobManagerConfig config) throws SnapshotException;
    
    JobParameters buildJobParameters(T entity);

    JobParameters buildIdentifyingJobParameters(T entity);

}

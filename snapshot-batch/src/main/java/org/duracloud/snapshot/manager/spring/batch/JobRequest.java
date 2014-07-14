/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.manager.spring.batch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;

/**
 * @author Daniel Bernstein
 *         Date: Jul 23, 2014
 */
public class JobRequest {
    private Job job;
    private JobParameters jobParameters;
    /**
     * @param job
     * @param jobParameters
     */
    public JobRequest(Job job, JobParameters jobParameters) {
        super();
        this.job = job;
        this.jobParameters = jobParameters;
    }
    /**
     * @return the job
     */
    public Job getJob() {
        return job;
    }
    /**
     * @return the jobParameters
     */
    public JobParameters getJobParameters() {
        return jobParameters;
    }
    
    
}

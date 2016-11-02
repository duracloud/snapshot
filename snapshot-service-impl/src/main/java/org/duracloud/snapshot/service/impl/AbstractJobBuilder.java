/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.step.factory.SimpleStepFactoryBean;

/**
 * @author Daniel Bernstein
 *         Date: Nov 2, 2016
 */
public class AbstractJobBuilder {

    private static Logger log = LoggerFactory.getLogger(AbstractJobBuilder.class);

    /**
     * @param stepFactory
     */
    protected void setThrottleLimitForContentTransfers(SimpleStepFactoryBean<?,?> stepFactory) {
        String threadsPerJob = System.getProperty("duracloud.bridge.threads-per-job", "7");
        log.debug("threadsPerJob = {}", threadsPerJob);
        stepFactory.setThrottleLimit(Integer.parseInt(threadsPerJob));
    }

}

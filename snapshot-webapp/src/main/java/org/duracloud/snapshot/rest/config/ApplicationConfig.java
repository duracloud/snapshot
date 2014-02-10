/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.rest.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

/**
 * @author Daniel Bernstein
 *         Date: Feb 4, 2014
 */
@Configuration
public class ApplicationConfig {
    private static Logger log = LoggerFactory.getLogger(ApplicationConfig.class);
    /**
     * 
     */
    public ApplicationConfig() {
        log.info("creating ApplicationConfig instance...");
    }
}

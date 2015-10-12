/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.bridge.rest;

import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.spring.scope.RequestContextFilter;

/**
 * The entry point for the jax-rs application.
 * This class is referenced in the web.xml.
 * @author Daniel Bernstein
 *         Date: Feb 4, 2014
 */
public class Application extends ResourceConfig{

        /**
         * Register JAX-RS application components.
         */
        public Application () {
            super(
                RequestContextFilter.class,
                GeneralResource.class,
                SnapshotResource.class,
                RestoreResource.class,
                SnapshotObjectMapperProvider.class,
                JacksonFeature.class, 
                MissingJsonBodyInterceptor.class);
            
        }
        
        
    }

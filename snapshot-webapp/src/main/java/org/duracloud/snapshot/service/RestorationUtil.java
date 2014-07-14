/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service;

/**
 * @author Daniel Bernstein
 *         Date: Jul 17, 2014
 */
public class RestorationUtil {
    public static String getId(RestorationConfig config){
       return  config.getSnapshotId()
            + "." + config.getHost() 
            + "." + config.getPort() 
            + "." + config.getSpaceId();
    }
}

/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import java.util.HashMap;
import java.util.Map;

import org.duracloud.snapshot.common.SnapshotServiceConstants;
import org.duracloud.snapshot.db.model.Restoration;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;

/**
 * @author Daniel Bernstein
 *         Date: Oct 28, 2014
 */
public class RestoreJobParameterMarshaller {

    /**
     * @param restoration 
     * @return a map of job parameters to uniquely identify restoration job
     */
    public static Map<String, JobParameter> marshal(Restoration restoration) {
        Map<String, JobParameter> map = new HashMap<>();
        map.put(SnapshotServiceConstants.SPRING_BATCH_UNIQUE_ID,
                new JobParameter(restoration.getRestorationId(),true));
        return map;
    }
    
    public static String unmarshal(JobParameters parameters){
       return parameters.getString(SnapshotServiceConstants.SPRING_BATCH_UNIQUE_ID);
    }

}

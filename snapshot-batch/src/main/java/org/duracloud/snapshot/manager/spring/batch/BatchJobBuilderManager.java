/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.manager.spring.batch;

import java.util.HashMap;
import java.util.Map;

import org.duracloud.snapshot.db.model.Restoration;
import org.duracloud.snapshot.db.model.Snapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Daniel Bernstein
 *         Date: Jul 25, 2014
 */
@Component
public class BatchJobBuilderManager  {
    
    @SuppressWarnings("rawtypes")
    private Map<Class,BatchJobBuilder> builders = new HashMap<>();
    
    @Autowired
    public BatchJobBuilderManager(SnapshotJobBuilder snapshot, RestorationJobBuilder restoration){
        this.builders.put(Snapshot.class, snapshot);
        this.builders.put(Restoration.class, restoration);
    }
    
    public BatchJobBuilder getBuilder(Object entity)  {
        BatchJobBuilder builder = this.builders.get(entity.getClass());
        
        if(builder == null) throw new RuntimeException("No builder registered for " + entity.getClass());
        
        return builder;
    }
}

/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import org.duracloud.client.ContentStore;
import org.duracloud.client.task.SnapshotTaskClient;
import org.duracloud.client.task.SnapshotTaskClientImpl;
import org.duracloud.snapshot.db.model.DuracloudEndPointConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Daniel Bernstein
 *         Date: Aug 18, 2014
 */
@Component
public class SnapshotTaskClientHelper {
    private StoreClientHelper storeClientHelper;

    @Autowired
    public SnapshotTaskClientHelper(StoreClientHelper storeClientHelper){
        this.storeClientHelper= storeClientHelper;
    }
    
    public SnapshotTaskClient create(DuracloudEndPointConfig config,
                                     String username,
                                     String password) {
        ContentStore contentStore =
            storeClientHelper.create(config, username, password);
        return new SnapshotTaskClientImpl(contentStore);
    }
}

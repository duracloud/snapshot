/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import org.duracloud.client.ContentStore;
import org.duracloud.snapshot.common.SnapshotServiceConstants;
import org.duracloud.snapshot.db.model.DuracloudEndPointConfig;
import org.duracloud.sync.util.StoreClientUtil;
import org.springframework.stereotype.Component;

/**
 * @author Daniel Bernstein Date: Aug 18, 2014
 */
@Component
public class StoreClientHelper {
    public ContentStore create(DuracloudEndPointConfig config,
                               String username,
                               String password) {
        StoreClientUtil clientUtil = new StoreClientUtil();
        ContentStore contentStore =
            clientUtil.createContentStore(config.getHost(),
                                          config.getPort(),
                                          SnapshotServiceConstants.DURASTORE_CONTEXT,
                                          username,
                                          password,
                                          config.getStoreId());
        return contentStore;
    }
}

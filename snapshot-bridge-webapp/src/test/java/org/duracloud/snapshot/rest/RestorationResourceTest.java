/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.rest;


import javax.ws.rs.core.Response;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.duracloud.snapshot.bridge.rest.RestorationResource;
import org.duracloud.snapshot.bridge.rest.RestoreParams;
import org.duracloud.snapshot.bridge.service.RestorationManager;
import org.duracloud.snapshot.common.test.SnapshotTestBase;
import org.duracloud.snapshot.db.model.DuracloudEndPointConfig;
import org.duracloud.snapshot.db.model.Restoration;
import org.duracloud.snapshot.db.model.RestorationStatus;
import org.duracloud.snapshot.manager.SnapshotException;
import org.easymock.EasyMock;
import org.easymock.Mock;
import org.easymock.TestSubject;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Daniel Bernstein
 *         Date: Feb 4, 2014
 */

public class RestorationResourceTest extends SnapshotTestBase {
    
    @Mock
    private RestorationManager manager;
    @TestSubject
    private RestorationResource resource;
    
    /* (non-Javadoc)
     * @see org.duracloud.snapshot.common.test.EasyMockTestBase#setup()
     */
    @Override
    public void setup() {
        super.setup();
        resource = new RestorationResource(manager);
    }
    
  


    @Test
    public void testRestoreSnapshot() throws SnapshotException {
        
        EasyMock.expect(manager.restoreSnapshot(EasyMock.isA(String.class),
                                                EasyMock.isA(DuracloudEndPointConfig.class)))
                .andReturn(EasyMock.createMock(Restoration.class));
       replayAll();
        RestoreParams params = new RestoreParams();
        params.setHost("hoset");
        params.setPort("443");
        params.setSnapshotId("snapshot");
        params.setSpaceId("space");
        resource.restoreSnapshot(params);
        
        
    }

    @Test
    public void testSnapshotRestorationComplete() throws SnapshotException {
        long restorationId = 1000;
        EasyMock.expect(manager.restorationCompleted(restorationId))
                .andReturn(EasyMock.createMock(Restoration.class));
        replayAll();
        resource.restoreComplete(restorationId);
        
    }

    @Test
    public void testSnapshotRestorationStatus() throws SnapshotException, JSONException {
        long restorationId = 1000;
        Restoration restoration = createMock(Restoration.class);

        EasyMock.expect(manager.get(restorationId)).andReturn(restoration);

        EasyMock.expect(restoration.getId()).andReturn(restorationId);

        EasyMock.expect(restoration.getStatus())
                .andReturn(RestorationStatus.DPN_TRANSFER_COMPLETE);
        EasyMock.expect(restoration.getMemo()).andReturn("test");
        replayAll();
        Response response = resource.getStatus(restorationId);

        Assert.assertNotNull(response);

        JSONObject jsonObject = (JSONObject) response.getEntity();
        Assert.assertNotNull(jsonObject);
        Assert.assertEquals(jsonObject.get("status"),
                            RestorationStatus.DPN_TRANSFER_COMPLETE);
        Assert.assertEquals(jsonObject.get("details"), "test");
        
    }

}

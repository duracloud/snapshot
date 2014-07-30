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
import org.duracloud.snapshot.SnapshotException;
import org.duracloud.snapshot.bridge.rest.RestoreResource;
import org.duracloud.snapshot.common.test.SnapshotTestBase;
import org.duracloud.snapshot.db.model.DuracloudEndPointConfig;
import org.duracloud.snapshot.db.model.Restoration;
import org.duracloud.snapshot.dto.CreateRestoreBridgeParameters;
import org.duracloud.snapshot.dto.GetRestoreStatusBridgeResult;
import org.duracloud.snapshot.dto.RestoreStatus;
import org.duracloud.snapshot.service.RestoreManager;
import org.easymock.EasyMock;
import org.easymock.Mock;
import org.easymock.TestSubject;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Daniel Bernstein
 *         Date: Feb 4, 2014
 */

public class RestoreResourceTest extends SnapshotTestBase {
    
    @Mock
    private RestoreManager manager;
    @TestSubject
    private RestoreResource resource;
    
    /* (non-Javadoc)
     * @see org.duracloud.snapshot.common.test.EasyMockTestBase#setup()
     */
    @Override
    public void setup() {
        super.setup();
        resource = new RestoreResource(manager);
    }
    
    @Test
    public void testRestoreSnapshot() throws SnapshotException {
        
        EasyMock.expect(manager.restoreSnapshot(EasyMock.isA(String.class),
                                                EasyMock.isA(DuracloudEndPointConfig.class), EasyMock.isA(String.class)))
                .andReturn(EasyMock.createMock(Restoration.class));
       replayAll();
        CreateRestoreBridgeParameters params = new CreateRestoreBridgeParameters();
        params.setHost("hoset");
        params.setPort("443");
        params.setSnapshotId("snapshot");
        params.setSpaceId("space");
        params.setUserEmail("email");
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

        EasyMock.expect(restoration.getStatus())
                .andReturn(RestoreStatus.DPN_TRANSFER_COMPLETE);
        EasyMock.expect(restoration.getStatusText()).andReturn("test");

        replayAll();
        Response response = resource.getStatus(restorationId);

        Assert.assertNotNull(response);

        GetRestoreStatusBridgeResult entity = (GetRestoreStatusBridgeResult) response.getEntity();
        Assert.assertNotNull(entity);
        Assert.assertEquals(entity.getStatus(),
                            RestoreStatus.DPN_TRANSFER_COMPLETE);
        
    }

}

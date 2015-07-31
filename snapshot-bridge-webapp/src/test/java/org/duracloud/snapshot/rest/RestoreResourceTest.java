/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.rest;


import java.util.Date;

import javax.ws.rs.core.Response;

import org.codehaus.jettison.json.JSONException;
import org.duracloud.snapshot.SnapshotException;
import org.duracloud.snapshot.bridge.rest.RestoreResource;
import org.duracloud.snapshot.common.test.SnapshotTestBase;
import org.duracloud.snapshot.db.model.DuracloudEndPointConfig;
import org.duracloud.snapshot.db.model.Restoration;
import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.dto.RestoreStatus;
import org.duracloud.snapshot.dto.bridge.CreateRestoreBridgeParameters;
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
        String restorationId = "restoration-id";
        EasyMock.expect(manager.restoreCompleted(restorationId))
                .andReturn(EasyMock.createMock(Restoration.class));
        replayAll();
        resource.restoreComplete(restorationId);
        
    }

    @Test
    public void testGetRestore() throws SnapshotException, JSONException {
        String restorationId = "restoration-id";
        Restoration restoration = setupRestoration();
        EasyMock.expect(manager.get(restorationId)).andReturn(restoration);
        replayAll();
        Response response = resource.get(restorationId);
        Assert.assertNotNull(response);
        Assert.assertNotNull(response.getEntity());
    }
    
    @Test
    public void testGetRestoreBySnapshot() throws SnapshotException, JSONException {
        String snapshotId = "snapshot-id";
        Restoration restoration = setupRestoration();
        EasyMock.expect(manager.getBySnapshotId(snapshotId)).andReturn(restoration);
        replayAll();
        Response response = resource.getBySnapshot(snapshotId);
        Assert.assertNotNull(response);
        Assert.assertNotNull(response.getEntity());
    }

    /**
     * @return
     */
    private Restoration setupRestoration() {
        Restoration r = createMock(Restoration.class);
        EasyMock.expect(r.getRestorationId()).andReturn("restore-id");
        EasyMock.expect(r.getStatus()).andReturn(RestoreStatus.DPN_TRANSFER_COMPLETE);
        Snapshot snapshot = createMock(Snapshot.class);
        EasyMock.expect(r.getSnapshot()).andReturn(snapshot);
        EasyMock.expect(snapshot.getName()).andReturn("snapshot-id");
        EasyMock.expect(r.getStartDate()).andReturn(new Date());
        EasyMock.expect(r.getEndDate()).andReturn(new Date());
        EasyMock.expect(r.getExpirationDate()).andReturn(new Date());
        EasyMock.expect(r.getStatusText()).andReturn("status text");
        DuracloudEndPointConfig dest = createMock(DuracloudEndPointConfig.class);
        EasyMock.expect(r.getDestination()).andReturn(dest);
        EasyMock.expect(dest.getHost()).andReturn("host");
        EasyMock.expect(dest.getPort()).andReturn(443);
        EasyMock.expect(dest.getStoreId()).andReturn("store-id");
        EasyMock.expect(dest.getSpaceId()).andReturn("space-id");
        
        return r;
    }

}

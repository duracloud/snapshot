/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.bridge.rest;


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
import static org.easymock.EasyMock.*;
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
    public void setup() throws Exception {
        super.setup();
        resource = new RestoreResource(manager);
    }
    
    @Test
    public void testRestoreSnapshot() throws SnapshotException {
        Restoration restoration = createMock(Restoration.class);
        expect(restoration.getRestorationId()).andReturn("restoration-id");
        expect(restoration.getStatus()).andReturn(RestoreStatus.INITIALIZED);

        expect(manager.restoreSnapshot(isA(String.class),
                                                isA(DuracloudEndPointConfig.class), isA(String.class)))
                .andReturn(restoration);
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
        Restoration restoration = createMock(Restoration.class);
        expect(restoration.getStatus()).andReturn(RestoreStatus.DPN_TRANSFER_COMPLETE);
        expect(restoration.getStatusText()).andReturn(isA(String.class));

        String restorationId = "restoration-id";
        expect(manager.restoreCompleted(restorationId))
                .andReturn(restoration);
        replayAll();
        resource.restoreComplete(restorationId);
        
    }

    @Test
    public void testCancelSnapshot() throws SnapshotException {
        String restorationId = "restoration-id";
        Restoration restore = createMock(Restoration.class);
        expect(restore.getStatus()).andReturn(RestoreStatus.WAITING_FOR_DPN);
        expect(manager.get(restorationId)).andReturn(restore);
        manager.cancelRestore(restorationId);
        expectLastCall();
        replayAll();
        resource.cancel(restorationId);
        
    }

    @Test
    public void testGetRestore() throws SnapshotException, JSONException {
        String restorationId = "restoration-id";
        Restoration restoration = setupRestoration();
        expect(manager.get(restorationId)).andReturn(restoration);
        replayAll();
        Response response = resource.get(restorationId);
        Assert.assertNotNull(response);
        Assert.assertNotNull(response.getEntity());
    }
    
    @Test
    public void testGetRestoreBySnapshot() throws SnapshotException, JSONException {
        String snapshotId = "snapshot-id";
        Restoration restoration = setupRestoration();
        expect(manager.getBySnapshotId(snapshotId)).andReturn(restoration);
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
        expect(r.getRestorationId()).andReturn("restore-id");
        expect(r.getStatus()).andReturn(RestoreStatus.DPN_TRANSFER_COMPLETE);
        Snapshot snapshot = createMock(Snapshot.class);
        expect(r.getSnapshot()).andReturn(snapshot);
        expect(snapshot.getName()).andReturn("snapshot-id");
        expect(r.getStartDate()).andReturn(new Date());
        expect(r.getEndDate()).andReturn(new Date());
        expect(r.getExpirationDate()).andReturn(new Date());
        expect(r.getStatusText()).andReturn("status text");
        DuracloudEndPointConfig dest = createMock(DuracloudEndPointConfig.class);
        expect(r.getDestination()).andReturn(dest);
        expect(dest.getHost()).andReturn("host");
        expect(dest.getPort()).andReturn(443);
        expect(dest.getStoreId()).andReturn("store-id");
        expect(dest.getSpaceId()).andReturn("space-id");
        
        return r;
    }

}

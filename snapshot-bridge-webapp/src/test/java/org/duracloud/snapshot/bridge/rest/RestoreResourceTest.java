/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.bridge.rest;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.isA;
import static org.junit.Assert.assertEquals;

import java.util.Date;
import javax.ws.rs.core.Response;

import org.duracloud.snapshot.SnapshotException;
import org.duracloud.snapshot.common.test.SnapshotTestBase;
import org.duracloud.snapshot.db.model.DuracloudEndPointConfig;
import org.duracloud.snapshot.db.model.Restoration;
import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.dto.RestoreStatus;
import org.duracloud.snapshot.dto.bridge.CreateRestoreBridgeParameters;
import org.duracloud.snapshot.dto.bridge.RequestRestoreBridgeParameters;
import org.duracloud.snapshot.service.RestoreManager;
import org.duracloud.snapshot.service.SnapshotManager;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.easymock.Mock;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Daniel Bernstein
 * Date: Feb 4, 2014
 */

public class RestoreResourceTest extends SnapshotTestBase {

    @Mock
    private RestoreManager manager;

    @Mock
    private SnapshotManager snapshotManager;

    @Mock
    private Restoration restoration;

    @Mock
    private Snapshot snapshot;

    private RestoreResource resource;

    /* (non-Javadoc)
     * @see org.duracloud.snapshot.common.test.EasyMockTestBase#setup()
     */
    @Override
    public void setup() throws Exception {
        super.setup();
        resource = new RestoreResource(manager, snapshotManager);
    }

    @Test
    public void testRestoreSnapshot() throws SnapshotException {
        String restorationId = "restoration-id";
        String userEmail = "user-email";

        EasyMock.expect(manager.restoreSnapshot(EasyMock.isA(String.class),
                                                EasyMock.isA(DuracloudEndPointConfig.class),
                                                EasyMock.isA(String.class)))
                .andReturn(restoration);

        EasyMock.expect(restoration.getSnapshot())
                .andReturn(snapshot);
        EasyMock.expect(restoration.getRestorationId())
                .andReturn(restorationId).times(2);
        EasyMock.expect(restoration.getStatus())
                .andReturn(RestoreStatus.INITIALIZED);

        Capture<String> historyCapture = Capture.newInstance(CaptureType.FIRST);
        EasyMock.expect(snapshotManager.updateHistory(EasyMock.isA(Snapshot.class),
                                                      EasyMock.capture(historyCapture)))
                .andReturn(snapshot);

        replayAll();

        CreateRestoreBridgeParameters params = new CreateRestoreBridgeParameters();
        params.setHost("host");
        params.setPort("443");
        params.setSnapshotId("snapshot");
        params.setSpaceId("space");
        params.setUserEmail(userEmail);
        resource.restoreSnapshot(params);

        String history = historyCapture.getValue();
        String expectedHistory =
            "[{'restore-action':'RESTORE_INITIATED'}," +
            "{'restore-id':'" + restorationId + "'}," +
            "{'initiating-user':'" + userEmail + "'}]";
        assertEquals(expectedHistory, history.replaceAll("\\s", ""));
    }

    @Test
    public void testRequesetRestoreSnapshot() throws SnapshotException {
        String restorationId = "restoration-id";
        String userEmail = "user-email";

        EasyMock.expect(
            manager.requestRestoreSnapshot(EasyMock.isA(String.class),
                                           EasyMock.isA(DuracloudEndPointConfig.class),
                                           EasyMock.isA(String.class)))
                .andReturn(snapshot);

        Capture<String> historyCapture = Capture.newInstance(CaptureType.FIRST);
        EasyMock.expect(snapshotManager.updateHistory(EasyMock.isA(Snapshot.class),
                                                      EasyMock.capture(historyCapture)))
                .andReturn(snapshot);

        replayAll();

        RequestRestoreBridgeParameters params = new RequestRestoreBridgeParameters();
        params.setHost("host");
        params.setPort("443");
        params.setSnapshotId("snapshot");
        params.setSpaceId("space");
        params.setUserEmail(userEmail);
        resource.requestRestoreSnapshot(params);

        String history = historyCapture.getValue();
        String expectedHistory =
            "[{'restore-action':'RESTORE_REQUESTED'}," +
            "{'initiating-user':'" + userEmail + "'}]";
        assertEquals(expectedHistory, history.replaceAll("\\s", ""));
    }

    @Test
    public void testRestorationComplete() throws SnapshotException {
        String restorationId = "restoration-id";
        expect(restoration.getStatus()).andReturn(RestoreStatus.STORAGE_RETRIEVAL_COMPLETE);
        expect(restoration.getStatusText()).andReturn(isA(String.class));

        expect(manager.restoreCompleted(restorationId))
            .andReturn(restoration);
        replayAll();
        resource.restoreComplete(restorationId);
    }

    @Test
    public void testCancelSnapshot() throws SnapshotException {
        String restorationId = "restoration-id";
        Restoration restore = createMock(Restoration.class);
        expect(restore.getStatus()).andReturn(RestoreStatus.RETRIEVING_FROM_STORAGE);
        expect(manager.get(restorationId)).andReturn(restore);
        manager.cancelRestore(restorationId);
        expectLastCall();
        replayAll();
        resource.cancel(restorationId);

    }

    @Test
    public void testGetRestore() throws SnapshotException {
        String restorationId = "restoration-id";
        Restoration restoration = setupRestoration();
        expect(manager.get(restorationId)).andReturn(restoration);
        replayAll();
        Response response = resource.get(restorationId);
        Assert.assertNotNull(response);
        Assert.assertNotNull(response.getEntity());
    }

    @Test
    public void testGetRestoreBySnapshot() throws SnapshotException {
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
        expect(r.getStatus()).andReturn(RestoreStatus.STORAGE_RETRIEVAL_COMPLETE);
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

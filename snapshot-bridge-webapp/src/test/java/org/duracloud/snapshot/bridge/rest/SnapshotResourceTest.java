/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.bridge.rest;

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.isA;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.http.HttpStatus;
import org.duracloud.client.ContentStore;
import org.duracloud.common.constant.Constants;
import org.duracloud.common.notification.NotificationManager;
import org.duracloud.snapshot.SnapshotException;
import org.duracloud.snapshot.common.test.SnapshotTestBase;
import org.duracloud.snapshot.db.model.DuracloudEndPointConfig;
import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.db.model.SnapshotContentItem;
import org.duracloud.snapshot.db.model.SnapshotHistory;
import org.duracloud.snapshot.db.repo.SnapshotContentItemRepo;
import org.duracloud.snapshot.db.repo.SnapshotRepo;
import org.duracloud.snapshot.dto.SnapshotStatus;
import org.duracloud.snapshot.dto.SnapshotSummary;
import org.duracloud.snapshot.dto.bridge.CancelSnapshotBridgeResult;
import org.duracloud.snapshot.dto.bridge.CompleteSnapshotBridgeParameters;
import org.duracloud.snapshot.dto.bridge.CompleteSnapshotBridgeResult;
import org.duracloud.snapshot.dto.bridge.CreateSnapshotBridgeParameters;
import org.duracloud.snapshot.dto.bridge.CreateSnapshotBridgeResult;
import org.duracloud.snapshot.dto.bridge.GetSnapshotContentBridgeResult;
import org.duracloud.snapshot.dto.bridge.GetSnapshotListBridgeResult;
import org.duracloud.snapshot.dto.bridge.RestartSnapshotBridgeResult;
import org.duracloud.snapshot.dto.bridge.UpdateSnapshotHistoryBridgeParameters;
import org.duracloud.snapshot.dto.bridge.UpdateSnapshotHistoryBridgeResult;
import org.duracloud.snapshot.id.SnapshotIdentifier;
import org.duracloud.snapshot.service.AlternateIdAlreadyExistsException;
import org.duracloud.snapshot.service.BridgeConfiguration;
import org.duracloud.snapshot.service.EventLog;
import org.duracloud.snapshot.service.SnapshotJobManager;
import org.duracloud.snapshot.service.SnapshotJobManagerConfig;
import org.duracloud.snapshot.service.SnapshotManager;
import org.duracloud.snapshot.service.impl.StoreClientHelper;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.Mock;
import org.junit.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.data.domain.PageRequest;

/**
 * @author Daniel Bernstein
 * Date: Feb 4, 2014
 */

public class SnapshotResourceTest extends SnapshotTestBase {

    @Mock
    private SnapshotJobManager jobManager;

    @Mock
    private SnapshotManager snapshotManager;

    @Mock
    private SnapshotRepo snapshotRepo;

    @Mock
    private SnapshotContentItemRepo snapshotContentItemRepo;

    @Mock
    private NotificationManager notificationManager;

    @Mock
    private BridgeConfiguration bridgeConfiguration;

    private SnapshotResource resource;

    @Mock
    private Snapshot snapshot;

    @Mock
    private DuracloudEndPointConfig source;

    @Mock
    private EventLog eventLog;

    @Mock
    private StoreClientHelper helper;

    /*
     * (non-Javadoc)
     *
     * @see org.duracloud.snapshot.common.test.EasyMockTestBase#setup()
     */
    @Override
    public void setup() throws Exception {
        super.setup();
        resource =
            new SnapshotResource(jobManager,
                                 snapshotManager,
                                 snapshotRepo,
                                 snapshotContentItemRepo,
                                 eventLog,
                                 helper);
    }

    @Test
    public void testGetSnapshot() throws SnapshotException {

        expect(snapshotRepo.findByName("snapshotId"))
            .andReturn(snapshot);
        expect(snapshotContentItemRepo
                   .countBySnapshotName("snapshotId"))
            .andReturn(300l);
        expect(snapshot.getSnapshotAlternateIds())
            .andReturn(new ArrayList<String>());
        expect(snapshot.getStatus())
            .andReturn(SnapshotStatus.SNAPSHOT_COMPLETE);

        expect(snapshot.getSource()).andReturn(source);

        expect(source.getHost()).andReturn("host");
        expect(source.getSpaceId()).andReturn("spaceId");
        expect(source.getStoreId()).andReturn("storeId");
        expect(snapshot.getDescription()).andReturn("description");
        expect(snapshot.getSnapshotDate()).andReturn(new Date());
        expect(snapshot.getName()).andReturn("snapshotId");
        expect(snapshot.getMemberId()).andReturn("memberId");

        expect(snapshot.getTotalSizeInBytes()).andReturn(1000l);

        replayAll();
        resource.getSnapshot("snapshotId");
    }

    @Test
    public void testGetNotFound() throws SnapshotException {
        expect(snapshotRepo.findByName("snapshotId")).andReturn(null);
        replayAll();
        resource.getSnapshot("snapshotId");
    }

    @Test
    public void testCreate() throws Exception {
        String host = "host";
        String port = "444";
        String storeId = "storeId";
        String spaceId = "spaceId";
        String snapshotId =
            new SnapshotIdentifier("account-name", storeId, spaceId,
                                   System.currentTimeMillis()).getSnapshotId();
        String description = "description";
        String email = "email";
        String memberID = "uuid";

        SnapshotJobManagerConfig config = createMock(SnapshotJobManagerConfig.class);
        expect(jobManager.getConfig())
            .andReturn(config);

        expect(config.getDuracloudUsername()).andReturn("username");
        expect(config.getDuracloudPassword()).andReturn("password");

        ContentStore contentStore = createMock(ContentStore.class);
        expect(helper.create(isA(DuracloudEndPointConfig.class),
                             isA(String.class),
                             isA(String.class))).andReturn(contentStore);

        expect(contentStore.getSpaceContents(spaceId)).andReturn(Arrays.asList(Constants.SNAPSHOT_PROPS_FILENAME,
                                                                               "first-item").iterator());
        expect(jobManager.executeSnapshot(snapshotId))
            .andReturn(BatchStatus.UNKNOWN);

        expect(snapshotRepo.findByName(snapshotId)).andReturn(null);

        expect(snapshotRepo.saveAndFlush(isA(Snapshot.class)))
            .andReturn(snapshot);
        eventLog.logSnapshotUpdate(snapshot);
        expectLastCall();

        expect(snapshot.getStatus())
            .andReturn(SnapshotStatus.INITIALIZED);

        expect(this.snapshotManager.updateHistory(snapshot,
                                                  "[{'snapshot-action':'SNAPSHOT_INITIATED'}," +
                                                  "{'initiating-user':'" + email + "'}," +
                                                  "{'snapshot-id':'" + snapshotId + "'}]"))
            .andReturn(snapshot);

        replayAll();

        CreateSnapshotBridgeParameters params =
            new CreateSnapshotBridgeParameters(host, port, storeId, spaceId, description, email, memberID);
        CreateSnapshotBridgeResult result = (CreateSnapshotBridgeResult) resource.create(snapshotId, params)
                                                                                 .getEntity();

        assertNotNull(result);
        assertEquals(snapshotId, result.getSnapshotId());
        assertEquals(SnapshotStatus.INITIALIZED, result.getStatus());

    }

    @Test
    public void testCreateFailDueToEmptySnapshot() throws Exception {
        String host = "host";
        String port = "444";
        String storeId = "storeId";
        String spaceId = "spaceId";
        String snapshotId =
            new SnapshotIdentifier("account-name", storeId, spaceId,
                                   System.currentTimeMillis()).getSnapshotId();
        String description = "description";
        String email = "email";
        String memberID = "uuid";

        SnapshotJobManagerConfig config = createMock(SnapshotJobManagerConfig.class);
        expect(jobManager.getConfig())
            .andReturn(config);

        expect(config.getDuracloudUsername()).andReturn("username");
        expect(config.getDuracloudPassword()).andReturn("password");

        expect(snapshotRepo.findByName(snapshotId)).andReturn(null);

        ContentStore contentStore = createMock(ContentStore.class);
        expect(helper.create(isA(DuracloudEndPointConfig.class),
                             isA(String.class),
                             isA(String.class))).andReturn(contentStore);

        expect(contentStore.getSpaceContents(spaceId)).andReturn(Arrays.asList(Constants.SNAPSHOT_PROPS_FILENAME)
                                                                       .iterator());
        replayAll();

        CreateSnapshotBridgeParameters params =
            new CreateSnapshotBridgeParameters(host, port, storeId, spaceId, description, email, memberID);
        Response response = resource.create(snapshotId, params);
        assertEquals(Status.CONFLICT.getStatusCode(), response.getStatus());
        assertTrue(((ResponseDetails) response.getEntity()).getMessage().contains("empty space"));

    }

    @Test
    public void testRestartSuccess() throws SnapshotException {
        String snapshotId = "snapshot-id";
        expect(snapshotRepo.findByName(snapshotId)).andReturn(snapshot);
        expect(snapshot.getStatus()).andReturn(SnapshotStatus.FAILED_TO_TRANSFER_FROM_DURACLOUD);
        expect(snapshot.getStatus()).andReturn(SnapshotStatus.INITIALIZED);

        expect(jobManager.executeSnapshot(snapshotId))
            .andReturn(BatchStatus.STARTING);
        snapshot.setEndDate(null);
        expectLastCall();
        snapshot.setStatusText(isA(String.class));
        expectLastCall();
        snapshot.setStatus(SnapshotStatus.INITIALIZED);
        expectLastCall();
        expect(snapshotRepo.saveAndFlush(snapshot))
            .andReturn(snapshot);
        eventLog.logSnapshotUpdate(snapshot);
        expectLastCall();
        replayAll();

        Response response = resource.restart(snapshotId);
        assertEquals(Status.ACCEPTED.getStatusCode(), response.getStatus());
        RestartSnapshotBridgeResult result =
            (RestartSnapshotBridgeResult) response.getEntity();

        assertNotNull(result);
        assertNotNull(result.getDescription());
        assertEquals(SnapshotStatus.INITIALIZED, result.getStatus());

    }

    @Test
    public void testRestartFailure() throws SnapshotException {
        String snapshotId = "snapshot-id";
        expect(snapshotRepo.findByName(snapshotId)).andReturn(snapshot);
        expect(snapshot.getStatus()).andReturn(SnapshotStatus.SNAPSHOT_COMPLETE);

        replayAll();

        Response response = resource.restart(snapshotId);
        assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        ResponseDetails result =
            (ResponseDetails) response.getEntity();

        assertNotNull(result);

    }

    @Test
    public void testComplete() throws SnapshotException {
        String snapshotId = "snapshot-name";
        List<String> snapshotAlternateIds = new ArrayList<String>();
        String altId1 = "alternate-name-1";
        String altId2 = "alternate-name-2";

        snapshotAlternateIds.add(altId1);
        snapshotAlternateIds.add(altId2);

        CompleteSnapshotBridgeParameters params =
            new CompleteSnapshotBridgeParameters(snapshotAlternateIds);

        expect(this.snapshotRepo.findByName(snapshotId)).andReturn(snapshot);
        expect(this.snapshotManager.addAlternateSnapshotIds(snapshot, snapshotAlternateIds))
            .andReturn(snapshot);
        expect(this.snapshotManager.updateHistory(snapshot,
                                                  "[{'snapshot-action':'SNAPSHOT_COMPLETED'}," +
                                                  "{'snapshot-id':'" + snapshotId + "'}," +
                                                  "{'alternate-ids':['" + altId1 + "','" + altId2 + "']}]"))
            .andReturn(snapshot);

        expect(this.snapshotManager.transferToStorageComplete(snapshotId)).andReturn(snapshot);
        expect(snapshot.getStatus()).andReturn(SnapshotStatus.CLEANING_UP);
        expect(snapshot.getStatusText()).andReturn("ok");
        replayAll();
        Response response = resource.complete(snapshotId, params);
        assertEquals(200, response.getStatus());
        assertTrue(response.getEntity() instanceof CompleteSnapshotBridgeResult);
    }

    @Test
    public void testCompleteDuplicateAltId() throws SnapshotException {
        String snapshotId = "snapshot-name";
        List<String> snapshotAlternateIds = new ArrayList<String>();
        String altId1 = "alternate-name-1";
        String altId2 = "alternate-name-2";

        snapshotAlternateIds.add(altId1);
        snapshotAlternateIds.add(altId2);

        CompleteSnapshotBridgeParameters params =
            new CompleteSnapshotBridgeParameters(snapshotAlternateIds);

        expect(this.snapshotRepo.findByName(snapshotId)).andReturn(snapshot);
        expect(this.snapshotManager.addAlternateSnapshotIds(snapshot, snapshotAlternateIds))
            .andThrow(new AlternateIdAlreadyExistsException("Duplicate ID"));

        replayAll();

        Response response = resource.complete(snapshotId, params);
        assertEquals(400, response.getStatus());
        assertNotNull(response.getEntity());
    }

    @Test
    public void testCancel() throws SnapshotException {
        String snapshotId = "snapshot-name";

        expect(this.snapshotRepo.findByName(snapshotId)).andReturn(snapshot);
        expect(snapshot.getStatus()).andReturn(SnapshotStatus.FAILED_TO_TRANSFER_FROM_DURACLOUD);

        this.snapshotManager.deleteSnapshot(snapshotId);
        expectLastCall();
        this.jobManager.cancelSnapshot(snapshotId);
        expectLastCall();
        replayAll();
        Response response = resource.cancel(snapshotId);
        assertTrue(response.getEntity() instanceof CancelSnapshotBridgeResult);
    }

    @Test
    public void testCancelFailure() throws SnapshotException {
        String snapshotId = "snapshot-name";

        expect(this.snapshotRepo.findByName(snapshotId)).andReturn(snapshot);
        expect(snapshot.getStatus()).andReturn(SnapshotStatus.CLEANING_UP);

        replayAll();
        Response response = resource.cancel(snapshotId);
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatus());
        assertTrue(response.getEntity() instanceof ResponseDetails);
        assertNotNull(((ResponseDetails) response.getEntity()).getMessage());
    }

    @Test
    public void testGetSnapshotList() {
        String sourceHost = "source-host";

        String snapshotName = "snapshot-name";
        String description = "description";
        String storeId = "store-id";
        String spaceId = "space-id";
        SnapshotStatus status = SnapshotStatus.SNAPSHOT_COMPLETE;
        List<Snapshot> snapshotList = new LinkedList<>();
        expect(snapshot.getDescription()).andReturn(description);
        expect(snapshot.getName()).andReturn(snapshotName);
        expect(snapshot.getStatus()).andReturn(status);
        DuracloudEndPointConfig source = createMock(DuracloudEndPointConfig.class);
        expect(source.getStoreId()).andReturn(storeId);
        expect(source.getSpaceId()).andReturn(spaceId);
        expect(snapshot.getSource()).andReturn(source);
        snapshotList.add(snapshot);
        expect(this.snapshotRepo.findBySourceHost(sourceHost))
            .andReturn(snapshotList);
        replayAll();

        Response response = this.resource.list(sourceHost, null, null);

        GetSnapshotListBridgeResult result =
            (GetSnapshotListBridgeResult) response.getEntity();

        List<SnapshotSummary> summaries =
            (List<SnapshotSummary>) result.getSnapshots();

        assertEquals(1, summaries.size());

        SnapshotSummary summary = summaries.get(0);

        assertEquals(snapshotName, summary.getSnapshotId());
        assertEquals(description, summary.getDescription());
        assertEquals(status, summary.getStatus());
        assertEquals(storeId, summary.getSourceStoreId());
        assertEquals(spaceId, summary.getSourceSpaceId());

    }

    @Test
    public void testListSnapshotsNoParams() {
        expect(snapshotRepo.findAll())
            .andReturn(new ArrayList<Snapshot>());
        replayAll();
        resource.listSnapshots(null, null, null);
    }

    @Test
    public void testListSnapshotsHost() {
        String host = "host";
        expect(snapshotRepo.findBySourceHost(host))
            .andReturn(new ArrayList<Snapshot>());
        replayAll();
        resource.listSnapshots(host, null, null);
    }

    @Test
    public void testListSnapshotsHostStoreId() {
        String host = "host";
        String storeId = "store-id";
        expect(snapshotRepo.findBySourceHostAndSourceStoreId(host, storeId))
            .andReturn(new ArrayList<Snapshot>());
        replayAll();
        resource.listSnapshots(host, storeId, null);
    }

    @Test
    public void testListSnapshotsHostStoreIdStatus() {
        String host = "host";
        String storeId = "store-id";
        SnapshotStatus status = SnapshotStatus.SNAPSHOT_COMPLETE;
        expect(snapshotRepo
                   .findBySourceHostAndSourceStoreIdAndStatus(host, storeId, status))
            .andReturn(new ArrayList<Snapshot>());
        replayAll();
        resource.listSnapshots(host, storeId, status);
    }

    @Test
    public void testListSnapshotStoreId() {
        String storeId = "store-id";
        expect(snapshotRepo.findBySourceStoreId(storeId))
            .andReturn(new ArrayList<Snapshot>());
        replayAll();
        resource.listSnapshots(null, storeId, null);
    }

    @Test
    public void testListSnapshotsStatus() {
        SnapshotStatus status = SnapshotStatus.SNAPSHOT_COMPLETE;
        expect(snapshotRepo.findByStatusOrderBySnapshotDateAsc(status))
            .andReturn(new ArrayList<Snapshot>());
        replayAll();
        resource.listSnapshots(null, null, status);
    }

    @Test
    public void testListSnapshotsHostStatus() {
        String host = "host";
        SnapshotStatus status = SnapshotStatus.SNAPSHOT_COMPLETE;
        expect(snapshotRepo.findBySourceHostAndStatus(host, status))
            .andReturn(new ArrayList<Snapshot>());
        replayAll();
        resource.listSnapshots(host, null, status);
    }

    @Test
    public void testListSnapshotsStoreIdStatus() {
        String storeId = "store-id";
        SnapshotStatus status = SnapshotStatus.SNAPSHOT_COMPLETE;
        expect(snapshotRepo.findBySourceStoreIdAndStatus(storeId, status))
            .andReturn(new ArrayList<Snapshot>());
        replayAll();
        resource.listSnapshots(null, storeId, status);
    }

    @Test
    public void testCountSnapshotsNoParams() {
        expect(snapshotRepo.count())
            .andReturn(Long.valueOf(1));
        replayAll();
        resource.getSnapshotsCount(null, null, null);
    }

    @Test
    public void testCountSnapshotsHost() {
        String host = "host";
        expect(snapshotRepo.countBySourceHost(host))
            .andReturn(Long.valueOf(1));
        replayAll();
        resource.getSnapshotsCount(host, null, null);
    }

    @Test
    public void testCountSnapshotsHostStoreId() {
        String host = "host";
        String storeId = "store-id";
        expect(snapshotRepo.countBySourceHostAndSourceStoreId(host, storeId))
            .andReturn(Long.valueOf(1));
        replayAll();
        resource.getSnapshotsCount(host, storeId, null);
    }

    @Test
    public void testCountSnapshotsHostStoreIdStatus() {
        String host = "host";
        String storeId = "store-id";
        SnapshotStatus status = SnapshotStatus.SNAPSHOT_COMPLETE;
        expect(snapshotRepo
                   .countBySourceHostAndSourceStoreIdAndStatus(host, storeId, status))
            .andReturn(Long.valueOf(1));
        replayAll();
        resource.getSnapshotsCount(host, storeId, status);
    }

    @Test
    public void testCountSnapshotsStoreId() {
        String storeId = "store-id";
        expect(snapshotRepo.countBySourceStoreId(storeId))
            .andReturn(Long.valueOf(1));
        replayAll();
        resource.getSnapshotsCount(null, storeId, null);
    }

    @Test
    public void testCountSnapshotsStatus() {
        SnapshotStatus status = SnapshotStatus.SNAPSHOT_COMPLETE;
        expect(snapshotRepo.countByStatusOrderBySnapshotDateAsc(status))
            .andReturn(Long.valueOf(1));
        replayAll();
        resource.getSnapshotsCount(null, null, status);
    }

    @Test
    public void testCountSnapshotsHostStatus() {
        String host = "host";
        SnapshotStatus status = SnapshotStatus.SNAPSHOT_COMPLETE;
        expect(snapshotRepo.countBySourceHostAndStatus(host, status))
            .andReturn(Long.valueOf(1));
        replayAll();
        resource.getSnapshotsCount(host, null, status);
    }

    @Test
    public void testCountSnapshotsStoreIdStatus() {
        String storeId = "store-id";
        SnapshotStatus status = SnapshotStatus.SNAPSHOT_COMPLETE;
        expect(snapshotRepo.countBySourceStoreIdAndStatus(storeId, status))
            .andReturn(Long.valueOf(1));
        replayAll();
        resource.getSnapshotsCount(null, storeId, status);
    }

    @Test
    public void testCountSnapshotsFilesNoParams() {
        expect(snapshotRepo.findAll())
            .andReturn(new ArrayList<Snapshot>());
        replayAll();
        resource.getSnapshotsFiles(null, null, null);
    }

    @Test
    public void testCountSnapshotsFilesHost() {
        String host = "host";
        expect(snapshotRepo.findBySourceHost(host))
            .andReturn(new ArrayList<Snapshot>());
        replayAll();
        resource.getSnapshotsFiles(host, null, null);
    }

    @Test
    public void testCountSnapshotsFilesHostStoreId() {
        String host = "host";
        String storeId = "store-id";
        expect(snapshotRepo.findBySourceHostAndSourceStoreId(host, storeId))
            .andReturn(new ArrayList<Snapshot>());
        replayAll();
        resource.getSnapshotsFiles(host, storeId, null);
    }

    @Test
    public void testCountSnapshotsFilesHostStoreIdStatus() {
        String host = "host";
        String storeId = "store-id";
        SnapshotStatus status = SnapshotStatus.SNAPSHOT_COMPLETE;
        expect(snapshotRepo
                   .findBySourceHostAndSourceStoreIdAndStatus(host, storeId, status))
            .andReturn(new ArrayList<Snapshot>());
        replayAll();
        resource.getSnapshotsFiles(host, storeId, status);
    }

    @Test
    public void testCountSnapshotsFilesStoreId() {
        String storeId = "store-id";
        expect(snapshotRepo.findBySourceStoreId(storeId))
            .andReturn(new ArrayList<Snapshot>());
        replayAll();
        resource.getSnapshotsFiles(null, storeId, null);
    }

    @Test
    public void testCountSnapshotsFilesStatus() {
        SnapshotStatus status = SnapshotStatus.SNAPSHOT_COMPLETE;
        expect(snapshotRepo.findByStatusOrderBySnapshotDateAsc(status))
            .andReturn(new ArrayList<Snapshot>());
        replayAll();
        resource.getSnapshotsFiles(null, null, status);
    }

    @Test
    public void testCountSnapshotsFilesHostStatus() {
        String host = "host";
        SnapshotStatus status = SnapshotStatus.SNAPSHOT_COMPLETE;
        expect(snapshotRepo.findBySourceHostAndStatus(host, status))
            .andReturn(new ArrayList<Snapshot>());
        replayAll();
        resource.getSnapshotsFiles(host, null, status);
    }

    @Test
    public void testCountSnapshotsFilesStoreIdStatus() {
        String storeId = "store-id";
        SnapshotStatus status = SnapshotStatus.SNAPSHOT_COMPLETE;
        expect(snapshotRepo.findBySourceStoreIdAndStatus(storeId, status))
            .andReturn(new ArrayList<Snapshot>());
        replayAll();
        resource.getSnapshotsFiles(null, storeId, status);
    }

    @Test
    public void testGetSnapshotContent() {
        String snapshotId = "snapshot-id";
        String prefix = "prefix";
        int page = 1;
        int pageSize = 5;
        String metaName = "metadata-name";
        String metaValue = "metadata-value";
        Long count = 1000l;

        Capture<PageRequest> pageRequestCapture = Capture.newInstance(CaptureType.FIRST);

        SnapshotContentItem item = new SnapshotContentItem();
        item.setContentId("test");
        item.setMetadata("{\"" + metaName + "\" : \"" + metaValue + "\"}");

        List<SnapshotContentItem> contentIds =
            Arrays.asList(new SnapshotContentItem[] {item});
        expect(snapshotContentItemRepo
                   .findBySnapshotNameAndContentIdStartingWithOrderByContentIdAsc(eq(snapshotId),
                                                                                  eq(prefix),
                                                                                  capture(pageRequestCapture)))
            .andReturn(contentIds);

        expect(snapshotContentItemRepo
                   .countBySnapshotName(eq(snapshotId)))
            .andReturn(count);

        replayAll();

        Response response =
            resource.getContent(snapshotId, page, pageSize, prefix);
        GetSnapshotContentBridgeResult result =
            (GetSnapshotContentBridgeResult) response.getEntity();

        PageRequest pageRequest = pageRequestCapture.getValue();
        assertEquals(page, pageRequest.getPageNumber());
        assertEquals(pageSize, pageRequest.getPageSize());

        org.duracloud.snapshot.dto.SnapshotContentItem resultItem =
            result.getContentItems().get(0);
        assertEquals("test", resultItem.getContentId());
        assertEquals(metaValue,
                     resultItem.getContentProperties().get(metaName));
        assertEquals(count, result.getTotalCount());

    }

    @Test
    public void testGetSnapshotContentNoPrefix() {
        String snapshotId = "snapshot-id";
        int page = 1;
        int pageSize = 5;
        String metaName = "metadata-name";
        String metaValue = "metadata-value";
        Long count = 1000l;

        Capture<PageRequest> pageRequestCapture = Capture.newInstance(CaptureType.FIRST);

        SnapshotContentItem item = new SnapshotContentItem();
        item.setContentId("test");
        item.setMetadata("{\"" + metaName + "\" : \"" + metaValue + "\"}");

        List<SnapshotContentItem> contentIds =
            Arrays.asList(new SnapshotContentItem[] {item});
        expect(snapshotContentItemRepo
                   .findBySnapshotNameOrderByContentIdAsc(eq(snapshotId),
                                                          capture(pageRequestCapture)))
            .andReturn(contentIds);

        expect(snapshotContentItemRepo
                   .countBySnapshotName(eq(snapshotId)))
            .andReturn(count);

        replayAll();

        Response response =
            resource.getContent(snapshotId, page, pageSize, null);
        GetSnapshotContentBridgeResult result =
            (GetSnapshotContentBridgeResult) response.getEntity();

        PageRequest pageRequest = pageRequestCapture.getValue();
        assertEquals(page, pageRequest.getPageNumber());
        assertEquals(pageSize, pageRequest.getPageSize());

        org.duracloud.snapshot.dto.SnapshotContentItem resultItem =
            result.getContentItems().get(0);
        assertEquals("test", resultItem.getContentId());
        assertEquals(metaValue,
                     resultItem.getContentProperties().get(metaName));
        assertEquals(count, result.getTotalCount());

    }

    @Test
    public void testUpdateHistory() {
        String snapshotId = "snapshot-id";
        String history = "this is some history";
        String storeId = "store-id";
        String spaceId = "space-id";
        DuracloudEndPointConfig source = createMock(DuracloudEndPointConfig.class);
        expect(source.getStoreId()).andReturn(storeId);
        expect(source.getSpaceId()).andReturn(spaceId);
        expect(snapshot.getSource()).andReturn(source);

        // object to send as JSON request
        UpdateSnapshotHistoryBridgeParameters params =
            new UpdateSnapshotHistoryBridgeParameters(false, history);
        // list of history back from snapshot
        ArrayList<SnapshotHistory> historyList = new ArrayList<SnapshotHistory>();
        SnapshotHistory test = new SnapshotHistory();
        test.setHistory(history);
        test.setSnapshot(snapshot);
        test.setHistoryDate(new Date());
        historyList.add(test);

        expect(this.snapshotRepo.findByName(snapshotId)).andReturn(snapshot);
        expect(this.snapshotManager.updateHistory(snapshot, history)).andReturn(snapshot);
        expect(snapshot.getSnapshotHistory()).andReturn(historyList);
        expect(snapshot.getName()).andReturn(snapshotId);
        expect(snapshot.getStatus()).andReturn(SnapshotStatus.SNAPSHOT_COMPLETE);
        expect(snapshot.getDescription()).andReturn("description");

        replayAll();

        Response response = resource.updateHistory(snapshotId, params);

        assertTrue(response.getEntity() instanceof UpdateSnapshotHistoryBridgeResult);
        assertEquals(history, ((UpdateSnapshotHistoryBridgeResult) response.getEntity()).getHistory());
    }

}

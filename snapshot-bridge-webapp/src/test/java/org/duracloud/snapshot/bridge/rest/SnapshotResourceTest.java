/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.bridge.rest;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.codehaus.jettison.json.JSONException;
import org.duracloud.common.notification.NotificationManager;
import org.duracloud.snapshot.SnapshotException;
import org.duracloud.snapshot.bridge.rest.ResponseDetails;
import org.duracloud.snapshot.bridge.rest.SnapshotResource;
import org.duracloud.snapshot.common.test.SnapshotTestBase;
import org.duracloud.snapshot.db.model.DuracloudEndPointConfig;
import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.db.model.SnapshotContentItem;
import org.duracloud.snapshot.db.model.SnapshotHistory;
import org.duracloud.snapshot.db.repo.SnapshotContentItemRepo;
import org.duracloud.snapshot.db.repo.SnapshotRepo;
import org.duracloud.snapshot.dto.SnapshotStatus;
import org.duracloud.snapshot.dto.SnapshotSummary;
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
import org.duracloud.snapshot.service.BridgeConfiguration;
import org.duracloud.snapshot.service.SnapshotJobManager;
import org.duracloud.snapshot.service.SnapshotManager;
import org.easymock.Capture;
import org.easymock.Mock;
import org.easymock.TestSubject;
import org.junit.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.data.domain.PageRequest;

/**
 * @author Daniel Bernstein 
 *         Date: Feb 4, 2014
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

    @TestSubject
    private SnapshotResource resource;

    @Mock
    private Snapshot snapshot;

    @Mock
    private DuracloudEndPointConfig source;

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
                                 snapshotContentItemRepo);
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
    public void testCreate() throws SnapshotException {
        String host = "host";
        String port = "444";
        String storeId = "storeId";
        String spaceId = "spaceId";
        String snapshotId =
            new SnapshotIdentifier("account-name", storeId, spaceId,
                                   System.currentTimeMillis()).getSnapshotId();
        String description = "description";
        String email = "email";

        expect(jobManager.executeSnapshot(snapshotId))
                .andReturn(BatchStatus.UNKNOWN);

        expect(snapshotRepo.findByName(snapshotId)).andReturn(null);

        expect(snapshotRepo.saveAndFlush(isA(Snapshot.class)))
                .andReturn(snapshot);

        expect(snapshot.getStatus())
                .andReturn(SnapshotStatus.INITIALIZED);

        replayAll();

        CreateSnapshotBridgeResult result =
            (CreateSnapshotBridgeResult) resource.create(snapshotId,
                                                         new CreateSnapshotBridgeParameters(host,
                                                                                            port,
                                                                                            storeId,
                                                                                            spaceId,
                                                                                            description,
                                                                                            email))
                                                 .getEntity();

        assertNotNull(result);
        assertEquals(snapshotId, result.getSnapshotId());
        assertEquals(SnapshotStatus.INITIALIZED, result.getStatus());

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
    public void testComplete() throws SnapshotException, JSONException {
        String snapshotId = "snapshot-name";
        List<String> snapshotAlternateIds = new ArrayList<String>();
        String altId1 = "alternate-name-1";
        String altId2 = "alternate-name-2";
        
        snapshotAlternateIds.add(altId1);
        snapshotAlternateIds.add(altId2);

        CompleteSnapshotBridgeParameters params =
            new CompleteSnapshotBridgeParameters(snapshotAlternateIds);

        expect(this.snapshotRepo.findByName(snapshotId)).andReturn(snapshot);
        expect(this.snapshotManager.addAlternateSnapshotIds(snapshot, snapshotAlternateIds)).andReturn(snapshot);
        expect(this.snapshotManager.updateHistory(snapshot,
                                                  "{\"alternateIds\":[\""+altId1+"\",\""+altId2+"\"]}"))
        .andReturn(snapshot);

        expect(this.snapshotManager.transferToDpnNodeComplete(snapshotId)).andReturn(snapshot);
        expect(snapshot.getStatus()).andReturn(SnapshotStatus.CLEANING_UP);
        expect(snapshot.getStatusText()).andReturn("ok");
        replayAll();
        Response response = resource.complete(snapshotId, params);
        assertTrue(response.getEntity() instanceof CompleteSnapshotBridgeResult);
    }

    @Test
    public void testGetSnapshotList() {
        String sourceHost = "source-host";

        String snapshotName = "snapshot-name";
        String description = "description";
        SnapshotStatus status = SnapshotStatus.SNAPSHOT_COMPLETE;
        List<Snapshot> snapshotList = new LinkedList<>();
        expect(snapshot.getDescription()).andReturn(description);
        expect(snapshot.getName()).andReturn(snapshotName);
        expect(snapshot.getStatus()).andReturn(status);

        snapshotList.add(snapshot);
        expect(this.snapshotRepo.findBySourceHost(sourceHost))
                .andReturn(snapshotList);
        replayAll();

        Response response = this.resource.list(sourceHost);

        GetSnapshotListBridgeResult result =
            (GetSnapshotListBridgeResult) response.getEntity();

        List<SnapshotSummary> summaries =
            (List<SnapshotSummary>) result.getSnapshots();

        assertEquals(1, summaries.size());

        SnapshotSummary summary = summaries.get(0);

        assertEquals(snapshotName, summary.getSnapshotId());
        assertEquals(description, summary.getDescription());
        assertEquals(status, summary.getStatus());
    }
    
    @Test
    public void testGetSnapshotContent(){
        String snapshotId = "snapshot-id";
        String prefix = "prefix";
        int page = 1;
        int pageSize = 5;
        String metaName = "metadata-name";
        String metaValue = "metadata-value";
        Long count = 1000l;
        
        Capture<PageRequest> pageRequestCapture = new Capture<>();
        
        SnapshotContentItem item = new SnapshotContentItem();
        item.setContentId("test");
        item.setMetadata("{\""+metaName+"\" : \""+metaValue+"\"}");

        List<SnapshotContentItem> contentIds =
            Arrays.asList(new SnapshotContentItem[]{item});
        expect(snapshotContentItemRepo
            .findBySnapshotNameAndContentIdStartingWithOrderByContentIdAsc(eq(snapshotId),
                                                        eq(prefix),
                                                        capture(
                                                            pageRequestCapture)))
                .andReturn(contentIds);

        expect(snapshotContentItemRepo
                        .countBySnapshotName(eq(snapshotId)))
                            .andReturn(count);

        replayAll();
        
        Response response =
            resource.getContent(snapshotId, page, pageSize, prefix);
        GetSnapshotContentBridgeResult result =
            (GetSnapshotContentBridgeResult)response.getEntity();

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
        assertEquals(history, ((UpdateSnapshotHistoryBridgeResult)response.getEntity()).getHistory());
    }

}

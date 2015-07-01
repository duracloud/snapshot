/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.rest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import javax.ws.rs.core.Response;

import org.codehaus.jettison.json.JSONException;
import org.duracloud.common.notification.NotificationManager;
import org.duracloud.snapshot.SnapshotException;
import org.duracloud.snapshot.bridge.rest.AlternateIdsJSONParam;
import org.duracloud.snapshot.bridge.rest.SnapshotResource;
import org.duracloud.snapshot.bridge.rest.UpdateMetadataJSONParam;
import org.duracloud.snapshot.common.test.SnapshotTestBase;
import org.duracloud.snapshot.db.model.DuracloudEndPointConfig;
import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.db.model.SnapshotContentItem;
import org.duracloud.snapshot.db.model.SnapshotMetadata;
import org.duracloud.snapshot.db.repo.SnapshotContentItemRepo;
import org.duracloud.snapshot.db.repo.SnapshotRepo;
import org.duracloud.snapshot.dto.SnapshotStatus;
import org.duracloud.snapshot.dto.SnapshotSummary;
import org.duracloud.snapshot.dto.bridge.CompleteSnapshotBridgeResult;
import org.duracloud.snapshot.dto.bridge.CreateSnapshotBridgeParameters;
import org.duracloud.snapshot.dto.bridge.CreateSnapshotBridgeResult;
import org.duracloud.snapshot.dto.bridge.GetSnapshotContentBridgeResult;
import org.duracloud.snapshot.dto.bridge.GetSnapshotListBridgeResult;
import org.duracloud.snapshot.dto.bridge.UpdateSnapshotMetadataBridgeResult;
import org.duracloud.snapshot.id.SnapshotIdentifier;
import org.duracloud.snapshot.service.BridgeConfiguration;
import org.duracloud.snapshot.service.SnapshotJobManager;
import org.duracloud.snapshot.service.SnapshotManager;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.Mock;
import org.easymock.TestSubject;
import org.junit.Assert;
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
    public void setup() {
        super.setup();
        resource =
            new SnapshotResource(jobManager,
                                 snapshotManager,
                                 snapshotRepo,
                                 snapshotContentItemRepo);
    }

    @Test
    public void testGetSnapshot() throws SnapshotException {

        EasyMock.expect(snapshotRepo.findByName("snapshotId"))
                .andReturn(snapshot);
        EasyMock.expect(snapshotContentItemRepo
                            .countBySnapshotName("snapshotId"))
                .andReturn(300l);
        EasyMock.expect(snapshot.getSnapshotAlternateIds())
                .andReturn(new ArrayList<String>());
        EasyMock.expect(snapshot.getStatus())
                .andReturn(SnapshotStatus.SNAPSHOT_COMPLETE);

        EasyMock.expect(snapshot.getSource()).andReturn(source);
        
        EasyMock.expect(source.getHost()).andReturn("host");
        EasyMock.expect(source.getSpaceId()).andReturn("spaceId");
        EasyMock.expect(source.getStoreId()).andReturn("storeId");
        EasyMock.expect(snapshot.getDescription()).andReturn("description");
        EasyMock.expect(snapshot.getSnapshotDate()).andReturn(new Date());
        EasyMock.expect(snapshot.getName()).andReturn("snapshotId");
        EasyMock.expect(snapshot.getTotalSizeInBytes()).andReturn(1000l);
        
        replayAll();
        resource.getSnapshot("snapshotId");
    }

    @Test
    public void testGetNotFound() throws SnapshotException {
        EasyMock.expect(snapshotRepo.findByName("snapshotId")).andReturn(null);
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

        EasyMock.expect(jobManager.executeSnapshot(snapshotId))
                .andReturn(BatchStatus.UNKNOWN);

        EasyMock.expect(snapshotRepo.findByName(snapshotId)).andReturn(null);

        EasyMock.expect(snapshotRepo.saveAndFlush(EasyMock.isA(Snapshot.class)))
                .andReturn(snapshot);

        EasyMock.expect(snapshot.getStatus())
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

        Assert.assertNotNull(result);
        Assert.assertEquals(snapshotId, result.getSnapshotId());
        Assert.assertEquals(SnapshotStatus.INITIALIZED, result.getStatus());

    }



    @Test
    public void testComplete() throws SnapshotException, JSONException {
        String snapshotId = "snapshot-name";
        List<String> snapshotAlternateIds = new ArrayList<String>();
        snapshotAlternateIds.add("alternate-name-1");
        snapshotAlternateIds.add("alternate-name-2");
        AlternateIdsJSONParam alternateIdsJSONParam = new AlternateIdsJSONParam();
        alternateIdsJSONParam.setAlternateIds(snapshotAlternateIds);

        EasyMock.expect(this.snapshotRepo.findByName(snapshotId)).andReturn(snapshot);
        this.snapshotManager.setAlternateSnapshotIds(snapshot, snapshotAlternateIds);
        EasyMock.expect(this.snapshotManager.transferToDpnNodeComplete(snapshotId)).andReturn(snapshot);
        EasyMock.expect(snapshot.getStatus()).andReturn(SnapshotStatus.CLEANING_UP);
        EasyMock.expect(snapshot.getStatusText()).andReturn("ok");
        EasyMock.expectLastCall();
        replayAll();
        Response response = resource.complete(snapshotId, alternateIdsJSONParam);
        Assert.assertTrue(response.getEntity() instanceof CompleteSnapshotBridgeResult);
    }

    @Test
    public void testGetSnapshotList() {
        String sourceHost = "source-host";

        String snapshotName = "snapshot-name";
        String description = "description";
        SnapshotStatus status = SnapshotStatus.SNAPSHOT_COMPLETE;
        List<Snapshot> snapshotList = new LinkedList<>();
        EasyMock.expect(snapshot.getDescription()).andReturn(description);
        EasyMock.expect(snapshot.getName()).andReturn(snapshotName);
        EasyMock.expect(snapshot.getStatus()).andReturn(status);

        snapshotList.add(snapshot);
        EasyMock.expect(this.snapshotRepo.findBySourceHost(sourceHost))
                .andReturn(snapshotList);
        replayAll();

        Response response = this.resource.list(sourceHost);

        GetSnapshotListBridgeResult result =
            (GetSnapshotListBridgeResult) response.getEntity();

        List<SnapshotSummary> summaries =
            (List<SnapshotSummary>) result.getSnapshots();

        Assert.assertEquals(1, summaries.size());

        SnapshotSummary summary = summaries.get(0);

        Assert.assertEquals(snapshotName, summary.getSnapshotId());
        Assert.assertEquals(description, summary.getDescription());
        Assert.assertEquals(status, summary.getStatus());
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
        EasyMock.expect(snapshotContentItemRepo
            .findBySnapshotNameAndContentIdStartingWithOrderByContentIdAsc(EasyMock.eq(snapshotId),
                                                        EasyMock.eq(prefix),
                                                        EasyMock.capture(
                                                            pageRequestCapture)))
                .andReturn(contentIds);

        EasyMock.expect(snapshotContentItemRepo
                        .countBySnapshotName(EasyMock.eq(snapshotId)))
                            .andReturn(count);

        replayAll();
        
        Response response =
            resource.getContent(snapshotId, page, pageSize, prefix);
        GetSnapshotContentBridgeResult result =
            (GetSnapshotContentBridgeResult)response.getEntity();

        PageRequest pageRequest = pageRequestCapture.getValue();
        Assert.assertEquals(page, pageRequest.getPageNumber());
        Assert.assertEquals(pageSize, pageRequest.getPageSize());

        org.duracloud.snapshot.dto.SnapshotContentItem resultItem =
            result.getContentItems().get(0);
        Assert.assertEquals("test", resultItem.getContentId());
        Assert.assertEquals(metaValue,
                            resultItem.getContentProperties().get(metaName));
        Assert.assertEquals(count, result.getTotalCount());

    }

    @Test
    public void testUpdateMetadata() {
        String snapshotId = "snapshot-id";
        String metadata = "this is some metadata";
        // object to send as JSON request
        UpdateMetadataJSONParam metadataUpdateParam = new UpdateMetadataJSONParam();
        metadataUpdateParam.setIsAlternate(false);
        metadataUpdateParam.setMetadata(metadata);
        // list of metadata back from snapshot
        ArrayList<SnapshotMetadata> metadataList = new ArrayList<SnapshotMetadata>();
        SnapshotMetadata test = new SnapshotMetadata();
        test.setMetadata(metadata);
        test.setSnapshot(snapshot);
        test.setMetadataDate(new Date());
        metadataList.add(test);

        EasyMock.expect(this.snapshotRepo.findByName(snapshotId)).andReturn(snapshot);
        EasyMock.expect(this.snapshotManager.updateMetadata(snapshot, metadata)).andReturn(snapshot);
        EasyMock.expect(snapshot.getSnapshotMetadata()).andReturn(metadataList);
        EasyMock.expect(snapshot.getName()).andReturn(snapshotId);
        EasyMock.expect(snapshot.getStatus()).andReturn(SnapshotStatus.SNAPSHOT_COMPLETE);
        EasyMock.expect(snapshot.getDescription()).andReturn("description");

        replayAll();

        Response response = resource.updateMetadata(snapshotId, metadataUpdateParam);

        Assert.assertTrue(response.getEntity() instanceof UpdateSnapshotMetadataBridgeResult);
        Assert.assertEquals(metadata, ((UpdateSnapshotMetadataBridgeResult)response.getEntity()).getMetadata());
    }

}

/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.rest;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import javax.ws.rs.core.Response;

import org.duracloud.common.notification.NotificationManager;
import org.duracloud.common.notification.NotificationType;
import org.duracloud.snapshot.SnapshotException;
import org.duracloud.snapshot.bridge.rest.SnapshotResource;
import org.duracloud.snapshot.bridge.service.BridgeConfiguration;
import org.duracloud.snapshot.common.test.SnapshotTestBase;
import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.db.model.SnapshotContentItem;
import org.duracloud.snapshot.db.repo.SnapshotContentItemRepo;
import org.duracloud.snapshot.db.repo.SnapshotRepo;
import org.duracloud.snapshot.dto.SnapshotStatus;
import org.duracloud.snapshot.dto.SnapshotSummary;
import org.duracloud.snapshot.dto.bridge.CreateSnapshotBridgeParameters;
import org.duracloud.snapshot.dto.bridge.CreateSnapshotBridgeResult;
import org.duracloud.snapshot.dto.bridge.GetSnapshotContentBridgeParameters;
import org.duracloud.snapshot.dto.bridge.GetSnapshotContentBridgeResult;
import org.duracloud.snapshot.dto.bridge.GetSnapshotListBridgeParameters;
import org.duracloud.snapshot.dto.bridge.GetSnapshotListBridgeResult;
import org.duracloud.snapshot.service.SnapshotJobManager;
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
    private SnapshotJobManager manager;

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

    /*
     * (non-Javadoc)
     * 
     * @see org.duracloud.snapshot.common.test.EasyMockTestBase#setup()
     */
    @Override
    public void setup() {
        super.setup();
        resource =
            new SnapshotResource(manager,
                                 snapshotRepo,
                                 snapshotContentItemRepo,
                                 notificationManager,
                                 bridgeConfiguration);
    }

    @Test
    public void testGetStatusSuccess() throws SnapshotException {

        EasyMock.expect(snapshotRepo.findByName("snapshotId"))
                .andReturn(snapshot);
        EasyMock.expect(snapshot.getStatus())
                .andReturn(SnapshotStatus.SNAPSHOT_COMPLETE);
        EasyMock.expect(snapshot.getStatusText()).andReturn("status text");

        replayAll();
        resource.getStatus("snapshotId");
    }

    @Test
    public void testGetStatusNotFound() throws SnapshotException {
        EasyMock.expect(snapshotRepo.findByName("snapshotId")).andReturn(null);
        replayAll();
        resource.getStatus("snapshotId");
    }

    @Test
    public void testCreate() throws SnapshotException {
        String host = "host";
        String port = "444";
        String storeId = "storeId";
        String spaceId = "spaceId";
        String snapshotId = "snapshot-name";
        String description = "description";
        String email = "email";

        EasyMock.expect(manager.executeSnapshot(snapshotId))
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
    public void testComplete() throws SnapshotException {
        String snapshotId = "snapshot-name";

        EasyMock.expect(snapshotRepo.findByName(snapshotId))
                .andReturn(snapshot);
        snapshot.setStatus(SnapshotStatus.SNAPSHOT_COMPLETE);
        EasyMock.expectLastCall();
        String adminEmail = "admin-email";
        String userEmail = "email";

        EasyMock.expect(snapshot.getUserEmail()).andReturn(userEmail);
        EasyMock.expect(snapshot.getStatus())
                .andReturn(SnapshotStatus.SNAPSHOT_COMPLETE);
        EasyMock.expect(snapshot.getStatusText()).andReturn("status");

        String[] stringArray = new String[] { adminEmail };
        EasyMock.expect(bridgeConfiguration.getDuracloudEmailAddresses())
                .andReturn(stringArray);

        EasyMock.expect(snapshotRepo.saveAndFlush(EasyMock.isA(Snapshot.class)))
                .andReturn(snapshot);

        notificationManager.sendNotification(EasyMock.isA(NotificationType.class),
                                             EasyMock.isA(String.class),
                                             EasyMock.isA(String.class),
                                             EasyMock.eq(adminEmail),
                                             EasyMock.eq(userEmail));
        EasyMock.expectLastCall();
        replayAll();

        resource.complete(snapshotId);

    }

    @Test
    public void testGetSnapshotList() {
        String sourceHost = "source-host";
        GetSnapshotListBridgeParameters params =
            createMock(GetSnapshotListBridgeParameters.class);

        String snapshotName = "snapshot-name";
        String description = "description";
        SnapshotStatus status = SnapshotStatus.SNAPSHOT_COMPLETE;
        List<Snapshot> snapshotList = new LinkedList<>();
        EasyMock.expect(snapshot.getDescription()).andReturn(description);
        EasyMock.expect(snapshot.getName()).andReturn(snapshotName);
        EasyMock.expect(snapshot.getStatus()).andReturn(status);

        snapshotList.add(snapshot);
        EasyMock.expect(params.getHost()).andReturn(sourceHost);
        EasyMock.expect(this.snapshotRepo.findBySourceHost(sourceHost))
                .andReturn(snapshotList);
        replayAll();

        Response response = this.resource.list(params);

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
        GetSnapshotContentBridgeParameters params = createMock(GetSnapshotContentBridgeParameters.class);
        EasyMock.expect(params.getPage()).andReturn(page);
        EasyMock.expect(params.getPageSize()).andReturn(pageSize);
        EasyMock.expect(params.getPrefix()).andReturn(prefix);
 
        Capture<PageRequest> pageRequestCapture = new Capture<>();
        
        SnapshotContentItem item = new SnapshotContentItem();
        item.setContentId("test");
        List<SnapshotContentItem> contentIds = Arrays.asList(new SnapshotContentItem[]{item});
        EasyMock.expect(snapshotContentItemRepo.findBySnapshotNameAndContentIdStartingWith(EasyMock.eq(snapshotId),
                                                        EasyMock.eq(prefix),
                                                        EasyMock.capture(pageRequestCapture)))
                .andReturn(contentIds);


        replayAll();
        
        Response response = resource.getContent(snapshotId, params);
        GetSnapshotContentBridgeResult result = (GetSnapshotContentBridgeResult)response.getEntity();

        PageRequest pageRequest = pageRequestCapture.getValue();
        
        Assert.assertEquals(page, pageRequest.getPageNumber());
        Assert.assertEquals(pageSize, pageRequest.getPageSize());
        
        Assert.assertEquals("test", result.getContentIds().get(0));

    }
}

/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.duracloud.client.ContentStore;
import org.duracloud.client.task.SnapshotTaskClient;
import org.duracloud.common.notification.NotificationManager;
import org.duracloud.common.notification.NotificationType;
import org.duracloud.error.ContentStoreException;
import org.duracloud.snapshot.SnapshotException;
import org.duracloud.snapshot.common.test.SnapshotTestBase;
import org.duracloud.snapshot.db.ContentDirUtils;
import org.duracloud.snapshot.db.model.DuracloudEndPointConfig;
import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.db.model.SnapshotContentItem;
import org.duracloud.snapshot.db.repo.SnapshotContentItemRepo;
import org.duracloud.snapshot.db.repo.SnapshotRepo;
import org.duracloud.snapshot.dto.SnapshotStatus;
import org.duracloud.snapshot.dto.task.CleanupSnapshotTaskResult;
import org.duracloud.snapshot.dto.task.CompleteSnapshotTaskResult;
import org.duracloud.snapshot.service.AlternateIdAlreadyExistsException;
import org.duracloud.snapshot.service.BridgeConfiguration;
import org.duracloud.snapshot.service.SnapshotManagerException;
import org.easymock.Capture;
import org.easymock.Mock;
import org.easymock.TestSubject;
import org.junit.Before;
import org.junit.Test;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

/**
 * @author Daniel Bernstein Date: Jul 31, 2014
 */
public class SnapshotManagerImplTest extends SnapshotTestBase {

    @TestSubject
    private SnapshotManagerImpl manager;

    @Mock
    private SnapshotContentItemRepo snapshotContentItemRepo;

    @Mock
    private SnapshotRepo snapshotRepo;

    @Mock
    private BridgeConfiguration bridgeConfig;

    @Mock
    private SnapshotTaskClientHelper snapshotTaskClientHelper;

    @Mock
    private NotificationManager notificationManager;

    @Mock
    private Snapshot snapshot;

    @Mock
    private DuracloudEndPointConfig endPointConfig;

    @Mock
    private SnapshotTaskClient snapshotTaskClient;

    @Mock
    private StoreClientHelper storeClientHelper;

    /**
     * @throws java.lang.Exception
     */
    @Before
    @Override
    public void setup() {
        super.setup();
        manager = new SnapshotManagerImpl();
        manager.setBridgeConfig(bridgeConfig);
        manager.setNotificationManager(notificationManager);
        manager.setSnapshotContentItemRepo(snapshotContentItemRepo);
        manager.setSnapshotRepo(snapshotRepo);
        manager.setSnapshotTaskClientHelper(snapshotTaskClientHelper);
        manager.setStoreClientHelper(storeClientHelper);
    }

    /**
     * Test method for
     * {@link org.duracloud.snapshot.service.impl.SnapshotManagerImpl#addContentItem(java.lang.String, org.duracloud.common.model.ContentItem, java.util.Map)}
     * .
     * 
     * @throws SnapshotManagerException
     */
    @Test
    public void testAddContentItem() throws SnapshotException {
        Map<String, String> props = new HashMap<>();
        props.put("key", "value");
        String contentId = "content-id";
        Capture<SnapshotContentItem> contentItemCapture = new Capture<>();
        expect(this.snapshotContentItemRepo
                   .findBySnapshotAndContentIdHash(isA(Snapshot.class),
                                                   isA(String.class))).andReturn(null);

        expect(this.snapshotContentItemRepo.save(capture(contentItemCapture)))
              .andReturn(createMock(SnapshotContentItem.class));
        replayAll();
        manager.addContentItem(snapshot, contentId, props);

        SnapshotContentItem item = contentItemCapture.getValue();

        assertEquals(contentId, item.getContentId());
        assertTrue(item.getMetadata().contains("\"key\""));
        assertTrue(item.getMetadata().contains("\"value\""));
        assertNotNull(item.getContentIdHash());

    }

    @Test
    public void testTransferToDpnNodeComplete() throws SnapshotException, ContentStoreException {
        String snapshotId = "snapshot-name";
        String spaceId = "space-id";
        expect(snapshotRepo.findByName(snapshotId)).andReturn(snapshot);
        snapshot.setStatus(SnapshotStatus.CLEANING_UP);
        expectLastCall();

        expect(snapshot.getName()).andReturn(snapshotId);

        File root = new File(System.getProperty("java.io.tmpdir") + 
                             File.separator + System.currentTimeMillis());
        File dir = new File(ContentDirUtils.getDestinationPath(snapshotId, root));
        dir.mkdirs();
        assertTrue(dir.exists());

        setupEndpoint();

        expect(this.bridgeConfig.getContentRootDir()).andReturn(root);

        setupTaskClientHelper();
        expect(this.endPointConfig.getSpaceId()).andReturn(spaceId);
        expect(snapshotTaskClient.cleanupSnapshot(spaceId))
            .andReturn(new CleanupSnapshotTaskResult());
        expect(snapshotRepo.saveAndFlush(isA(Snapshot.class)))
            .andReturn(snapshot);

        expectLastCall();
        replayAll();

        Snapshot snapshot = this.manager.transferToDpnNodeComplete(snapshotId);
        assertNotNull(snapshot);
        assertFalse(new File(dir.getAbsolutePath()).exists());

        try {
            FileUtils.deleteDirectory(root);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Test
    public void testAddDuplicateAlternateIdsInSameSnapshot() throws AlternateIdAlreadyExistsException{
        String altTestId = "alt-test";
        List<String> alternateIds = Arrays.asList(new String[]{altTestId});
        String snapshotId = "test";
        expect(snapshot.getName()).andReturn(snapshotId).times(2);
        expect(this.snapshotRepo.findBySnapshotAlternateIds(altTestId)).andReturn(snapshot);
        this.snapshot.addSnapshotAlternateIds(alternateIds);
        expectLastCall();
        expect(this.snapshotRepo.save(snapshot)).andReturn(snapshot);
        replayAll();
        this.manager.addAlternateSnapshotIds(snapshot, alternateIds);
    }
    
    @Test
    public void testAddDuplicateAlternateIdsInDifferentSnapshot(){
        String altTestId = "alt-test";
        List<String> alternateIds = Arrays.asList(new String[]{altTestId});
        String snapshotId = "test";
        Snapshot snapshot2 = createMock(Snapshot.class);
        
        expect(snapshot.getName()).andReturn(snapshotId);
        expect(snapshot2.getName()).andReturn("snapshot2").atLeastOnce();
        
        expect(this.snapshotRepo.findBySnapshotAlternateIds(altTestId)).andReturn(snapshot2);
        replayAll();
        try {
            this.manager.addAlternateSnapshotIds(snapshot, alternateIds);
            fail("call to addAlternateSnapshotIds should have failed");
        } catch (AlternateIdAlreadyExistsException e) {
            assertTrue(true);
        }
    }
    /**
     * 
     */
    private void setupTaskClientHelper() {
        expect(snapshotTaskClientHelper.create(eq(endPointConfig),
                                               isA(String.class),
                                               isA(String.class)))
        .andReturn(snapshotTaskClient);
    }

    /**
     * 
     */
    private void setupEndpoint() {
        expect(this.bridgeConfig.getDuracloudUsername()).andReturn("username").anyTimes();
        expect(this.bridgeConfig.getDuracloudPassword()).andReturn("password").anyTimes();
        expect(snapshot.getSource()).andReturn(endPointConfig);
    }

    @Test
    public void testFinalizeSnapshots() throws SnapshotException, ContentStoreException {

        List<Snapshot> snapshots = new ArrayList<>();
        snapshots.add(snapshot);
        expect(this.snapshotRepo.findByStatus(eq(SnapshotStatus.CLEANING_UP)))
            .andReturn(snapshots);

        ContentStore contentStore = createMock(ContentStore.class);

        expect(storeClientHelper.create(isA(DuracloudEndPointConfig.class),
                                        isA(String.class),
                                        isA(String.class))).andReturn(contentStore);

        Iterator<String> it = new ArrayList<String>().iterator();

        expect(contentStore.getSpaceContents(isA(String.class))).andReturn(it);

        setupEndpoint();

        String spaceId = "space-id";
        expect(this.endPointConfig.getSpaceId()).andReturn(spaceId);

        String snapshotId = "snapshot-name";
        expect(snapshot.getName()).andReturn(snapshotId);
        snapshot.setStatus(SnapshotStatus.SNAPSHOT_COMPLETE);
        expectLastCall();
        snapshot.setStatusText(isA(String.class));
        expectLastCall();

        snapshot.setEndDate(isA(Date.class));
        expectLastCall();
        String adminEmail = "admin-email";
        String userEmail = "email";

        expect(snapshot.getUserEmail()).andReturn(userEmail);

        String[] stringArray = new String[] { adminEmail };
        expect(bridgeConfig.getDuracloudEmailAddresses()).andReturn(stringArray);

        expect(snapshotRepo.saveAndFlush(isA(Snapshot.class))).andReturn(snapshot);

        notificationManager.sendNotification(isA(NotificationType.class),
                                             isA(String.class),
                                             isA(String.class),
                                             eq(adminEmail),
                                             eq(userEmail));
        expectLastCall();

        setupTaskClientHelper();

        CompleteSnapshotTaskResult result = createMock(CompleteSnapshotTaskResult.class);
        expect(result.getResult()).andReturn("success");
        expect(this.snapshotTaskClient.completeSnapshot(spaceId)).andReturn(result);
        replayAll();

        this.manager.finalizeSnapshots();

    }

}

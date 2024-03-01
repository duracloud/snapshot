/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import static org.easymock.EasyMock.anyLong;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.isA;
import static org.easymock.EasyMock.isNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.duracloud.client.ContentStore;
import org.duracloud.client.task.SnapshotTaskClient;
import org.duracloud.common.constant.Constants;
import org.duracloud.common.notification.NotificationManager;
import org.duracloud.common.notification.NotificationType;
import org.duracloud.common.util.WaitUtil;
import org.duracloud.error.ContentStoreException;
import org.duracloud.error.NotFoundException;
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
import org.duracloud.snapshot.service.EventLog;
import org.duracloud.snapshot.service.SnapshotManagerException;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.Mock;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Daniel Bernstein Date: Jul 31, 2014
 */
public class SnapshotManagerImplTest extends SnapshotTestBase {

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

    @Mock
    private EventLog eventLog;

    /**
     * @throws java.lang.Exception
     */
    @Before
    @Override
    public void setup() throws Exception {
        super.setup();
        manager = new SnapshotManagerImpl();
        manager.setBridgeConfig(bridgeConfig);
        manager.setNotificationManager(notificationManager);
        manager.setSnapshotContentItemRepo(snapshotContentItemRepo);
        manager.setSnapshotRepo(snapshotRepo);
        manager.setSnapshotTaskClientHelper(snapshotTaskClientHelper);
        manager.setStoreClientHelper(storeClientHelper);
        manager.setEventLog(eventLog);
    }

    /**
     * @throws SnapshotManagerException
     */
    @Test
    public void testAddContentItem() throws SnapshotException {
        Map<String, String> props = new HashMap<>();
        props.put("key", "value");
        String contentId = "content-id";
        Capture<SnapshotContentItem> contentItemCapture = Capture.newInstance(CaptureType.FIRST);
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
    public void testTransferToSnapshotStorageComplete() throws SnapshotException, ContentStoreException, IOException {
        String snapshotId = "snapshot-name";
        String spaceId = "space-id";
        expect(snapshotRepo.findByName(snapshotId)).andReturn(snapshot);
        snapshot.setStatus(SnapshotStatus.CLEANING_UP);
        expectLastCall();
        snapshot.setStatusText(isA(String.class));
        expectLastCall();

        expect(snapshot.getName()).andReturn(snapshotId);

        File root = new File(System.getProperty("java.io.tmpdir") +
                             File.separator + System.currentTimeMillis());

        System.setProperty(BridgeConfiguration.DURACLOUD_BRIDGE_ROOT_SYSTEM_PROPERTY,
                           root.getAbsolutePath());

        File dir = new File(ContentDirUtils.getDestinationPath(snapshotId, BridgeConfiguration.getContentRootDir()));
        dir.mkdirs();
        assertTrue(dir.exists());

        for (String f : SnapshotManagerImpl.METADATA_FILENAMES) {
            File file = new File(dir, f);
            file.deleteOnExit();
            try (FileOutputStream fos = new FileOutputStream(file)) {
                IOUtils.write("test", fos);
            }
        }

        ContentStore contentStore = createMock(ContentStore.class);
        expect(contentStore.getSpace(eq(Constants.SNAPSHOT_METADATA_SPACE),
                                     isNull(String.class),
                                     anyLong(),
                                     isNull(String.class))).andThrow(new NotFoundException("not found"));
        contentStore.createSpace(eq(Constants.SNAPSHOT_METADATA_SPACE));
        expectLastCall();
        expect(contentStore.addContent(eq(Constants.SNAPSHOT_METADATA_SPACE),
                                       eq(snapshotId + ".zip"),
                                       isA(InputStream.class),
                                       anyLong(),
                                       eq("application/zip"),
                                       isA(String.class),
                                       (Map<String, String>) isNull())).andReturn("test");

        expect(storeClientHelper.create(isA(DuracloudEndPointConfig.class),
                                        isA(String.class),
                                        isA(String.class))).andReturn(contentStore);

        setupEndpoint();

        setupTaskClientHelper();
        expect(this.endPointConfig.getSpaceId()).andReturn(spaceId);
        expect(snapshotTaskClient.cleanupSnapshot(spaceId))
            .andReturn(new CleanupSnapshotTaskResult());
        expect(snapshotRepo.saveAndFlush(isA(Snapshot.class)))
            .andReturn(snapshot);
        eventLog.logSnapshotUpdate(snapshot);
        expectLastCall();

        expectLastCall();
        replayAll();

        Snapshot snapshot = this.manager.transferToStorageComplete(snapshotId);
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
    public void testTransferError() throws SnapshotException {
        String snapshotId = "snapshot-name";
        String errorDetails = "error-details";

        // Set state
        expect(snapshotRepo.findByName(snapshotId)).andReturn(snapshot);
        snapshot.setStatus(SnapshotStatus.ERROR);
        expectLastCall();
        snapshot.setStatusText(errorDetails);
        expect(snapshotRepo.saveAndFlush(snapshot)).andReturn(snapshot);
        eventLog.logSnapshotUpdate(snapshot);
        expectLastCall();

        // Send email
        String add1 = "add-1";
        String add2 = "add-2";
        String[] addresses = {add1, add2};
        expect(bridgeConfig.getDuracloudEmailAddresses()).andReturn(addresses);
        notificationManager.sendNotification(eq(NotificationType.EMAIL),
                                             isA(String.class),
                                             isA(String.class),
                                             eq(add1),
                                             eq(add2));

        replayAll();

        Snapshot snapshot = manager.transferError(snapshotId, errorDetails);
        assertNotNull(snapshot);
    }

    @Test
    public void testAddDuplicateAlternateIdsInSameSnapshot() throws AlternateIdAlreadyExistsException {
        String altTestId = "alt-test";
        List<String> alternateIds = Arrays.asList(new String[] {altTestId});
        String snapshotId = "test";
        expect(snapshot.getId()).andReturn(1l);
        expect(this.snapshotRepo.findById(isA(Long.class))).andReturn(Optional.of(snapshot));

        expect(snapshot.getName()).andReturn(snapshotId).times(2);
        expect(this.snapshotRepo.findBySnapshotAlternateIds(altTestId))
            .andReturn(snapshot);
        this.snapshot.addSnapshotAlternateIds(alternateIds);
        expectLastCall();
        expect(this.snapshotRepo.saveAndFlush(snapshot)).andReturn(snapshot);
        replayAll();
        this.manager.addAlternateSnapshotIds(snapshot, alternateIds);
    }

    @Test
    public void testAddDuplicateAlternateIdsInDifferentSnapshot() {
        String altTestId = "alt-test";
        List<String> alternateIds = Arrays.asList(new String[] {altTestId});
        String snapshotId = "test";
        Snapshot snapshot2 = createMock(Snapshot.class);
        expect(snapshot.getName()).andReturn(snapshotId);
        expect(snapshot2.getName()).andReturn("snapshot2").atLeastOnce();

        expect(snapshot.getId()).andReturn(1l);
        expect(this.snapshotRepo.findById(isA(Long.class))).andReturn(Optional.of(snapshot));

        expect(this.snapshotRepo.findBySnapshotAlternateIds(altTestId)).andReturn(snapshot2);
        replayAll();
        try {
            this.manager.addAlternateSnapshotIds(snapshot, alternateIds);
            fail("call to addAlternateSnapshotIds should have failed");
        } catch (AlternateIdAlreadyExistsException e) {
            assertTrue(true);
        }
    }

    private void setupTaskClientHelper() {
        expect(snapshotTaskClientHelper.create(eq(endPointConfig),
                                               isA(String.class),
                                               isA(String.class)))
            .andReturn(snapshotTaskClient);
    }

    private void setupEndpoint() {
        expect(this.bridgeConfig.getDuracloudUsername()).andReturn("username").anyTimes();
        expect(this.bridgeConfig.getDuracloudPassword()).andReturn("password").anyTimes();
        expect(snapshot.getSource()).andReturn(endPointConfig).atLeastOnce();
    }

    @Test
    public void testFinalizeSnapshots() throws SnapshotException, ContentStoreException {

        List<Snapshot> snapshots = new ArrayList<>();
        snapshots.add(snapshot);
        expect(this.snapshotRepo.findByStatusOrderBySnapshotDateAsc(eq(SnapshotStatus.CLEANING_UP)))
            .andReturn(snapshots);

        expect(this.snapshot.getName()).andReturn("snapshot-id");
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

        String[] stringArray = new String[] {adminEmail};
        expect(bridgeConfig.getDuracloudEmailAddresses()).andReturn(stringArray);

        expect(snapshotRepo.saveAndFlush(isA(Snapshot.class))).andReturn(snapshot);
        eventLog.logSnapshotUpdate(snapshot);
        expectLastCall();

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

    @Test
    public void testFinalizeSnapshotsUnsuccessfulAfter3Days() throws SnapshotException, ContentStoreException {

        List<Snapshot> snapshots = new ArrayList<>();
        snapshots.add(snapshot);
        expect(this.snapshotRepo.findByStatusOrderBySnapshotDateAsc(eq(SnapshotStatus.CLEANING_UP)))
            .andReturn(snapshots).times(3);

        expect(this.snapshot.getName()).andReturn("snapshot-id").times(3);

        ContentStore contentStore = createMock(ContentStore.class);

        expect(storeClientHelper.create(isA(DuracloudEndPointConfig.class),
                                        isA(String.class),
                                        isA(String.class))).andReturn(contentStore).times(3);

        Iterator<String> it = Arrays.asList("test").iterator();

        expect(contentStore.getSpaceContents(isA(String.class))).andReturn(it).times(3);

        setupEndpoint();

        String spaceId = "space-id";
        expect(this.endPointConfig.getSpaceId()).andReturn(spaceId).times(3);

        String adminEmail = "admin-email";

        Calendar c = Calendar.getInstance();
        c.add(Calendar.DATE, -1 * (SnapshotManagerImpl.MAX_DAYS_IN_CLEANUP + 1));
        expect(snapshot.getModified()).andReturn(c.getTime()).times(3);

        String[] stringArray = new String[] {adminEmail};
        expect(bridgeConfig.getDuracloudEmailAddresses()).andReturn(stringArray).times(2);

        notificationManager.sendNotification(isA(NotificationType.class),
                                             isA(String.class),
                                             isA(String.class),
                                             eq(adminEmail));
        expectLastCall().times(2);

        int seconds = 1;
        this.manager.setSecondsBetweenCleanupFailureNotifications(seconds);

        replayAll();

        //first time email is sent
        this.manager.finalizeSnapshots();
        //second time no email because the seconds between would not have been reached.
        this.manager.finalizeSnapshots();
        WaitUtil.wait(seconds);
        //third time send email again.
        this.manager.finalizeSnapshots();

    }

}

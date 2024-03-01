/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.isA;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.duracloud.client.ContentStore;
import org.duracloud.common.notification.NotificationManager;
import org.duracloud.common.notification.NotificationType;
import org.duracloud.snapshot.SnapshotException;
import org.duracloud.snapshot.SnapshotNotFoundException;
import org.duracloud.snapshot.common.test.SnapshotTestBase;
import org.duracloud.snapshot.db.model.DuracloudEndPointConfig;
import org.duracloud.snapshot.db.model.Restoration;
import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.db.repo.RestoreRepo;
import org.duracloud.snapshot.db.repo.SnapshotRepo;
import org.duracloud.snapshot.dto.RestoreStatus;
import org.duracloud.snapshot.dto.SnapshotStatus;
import org.duracloud.snapshot.service.BridgeConfiguration;
import org.duracloud.snapshot.service.EventLog;
import org.duracloud.snapshot.service.RestorationNotFoundException;
import org.duracloud.snapshot.service.RestoreManagerConfig;
import org.duracloud.snapshot.service.SnapshotJobManager;
import org.duracloud.snapshot.service.SnapshotManager;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.easymock.Mock;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.batch.core.BatchStatus;

/**
 * @author Daniel Bernstein
 * Date: Jul 16, 2014
 */
public class RestoreManagerImplTest extends SnapshotTestBase {

    @Mock
    private SnapshotJobManager jobManager;

    @Mock
    private NotificationManager notificationManager;

    @Mock
    private RestoreRepo restoreRepo;

    @Mock
    private SnapshotRepo snapshotRepo;

    private RestoreManagerImpl manager;

    @Mock
    private DuracloudEndPointConfig destination;

    @Mock
    private Snapshot snapshot;

    @Mock
    private Restoration restoration;

    @Mock
    private BridgeConfiguration bridgeConfig;

    @Mock
    private StoreClientHelper storeClientHelper;

    @Mock
    private ContentStore contentStore;

    @Mock
    private SnapshotManager snapshotManager;

    @Mock
    private EventLog eventLog;

    private String snapshotName = "snapshot-name";

    private String restorationId = "restoration-id";

    private String userEmail = "user-email";

    private String duracloudEmail = "duracloud-email";

    /* (non-Javadoc)
     * @see org.duracloud.snapshot.common.test.SnapshotTestBase#setup()
     */
    @Override
    public void setup() throws Exception {
        super.setup();
        setupManager();
    }

    /**
     * /**
     * Test method for
     * {@link org.duracloud.snapshot.service.impl.RestoreManagerImpl#restoreSnapshot(String, org.duracloud.snapshot.db.model.DuracloudEndPointConfig, String)}.
     *
     * @throws SnapshotException
     */
    @Test
    public void testRestoreSnapshot() throws SnapshotException {
        expect(snapshotRepo.findByName(snapshotName)).andReturn(snapshot);
        expect(snapshot.getStatus()).andReturn(SnapshotStatus.SNAPSHOT_COMPLETE);
        DuracloudEndPointConfig source = createMock(DuracloudEndPointConfig.class);
        expect(source.getHost()).andReturn("host.duracloud.org");
        expect(source.getStoreId()).andReturn("store-id");
        expect(source.getSpaceId()).andReturn("space-id");
        expect(snapshot.getSource()).andReturn(source);

        expect(restoreRepo.saveAndFlush(isA(Restoration.class))).andReturn(restoration);
        eventLog.logRestoreUpdate(isA(Restoration.class));
        expectLastCall();

        Capture<String> emailBodyCapture = Capture.newInstance(CaptureType.FIRST);
        notificationManager.sendNotification(isA(NotificationType.class),
                                             isA(String.class),
                                             capture(emailBodyCapture),
                                             isA(String.class),
                                             isA(String.class));
        expectLastCall();

        expect(restoration.getRestorationId()).andReturn(restorationId);
        replayAll();
        Restoration restoration = manager.restoreSnapshot(snapshotName, destination, userEmail);
        Assert.assertNotNull(restoration);

        String emailBody = emailBodyCapture.getValue();
        Assert.assertTrue("Expecting snapshot ID in email body",
                          emailBody.contains(snapshotName));
        Assert.assertTrue("Expecting restore ID in email body",
                          emailBody.contains(restorationId));
    }

    @Test
    public void testRequestRestoreSnapshot() throws SnapshotException {
        expect(snapshotRepo.findByName(snapshotName)).andReturn(snapshot);
        String host = "host.duracloud.org";
        int port = 100;
        String storeId = "store-id";

        expect(destination.getHost()).andReturn(host);
        expect(destination.getPort()).andReturn(port);
        expect(destination.getStoreId()).andReturn(storeId);

        Capture<String> emailBodyCapture = Capture.newInstance(CaptureType.FIRST);
        notificationManager.sendNotification(isA(NotificationType.class),
                                             isA(String.class),
                                             capture(emailBodyCapture),
                                             isA(String.class));
        expectLastCall();

        replayAll();
        manager.requestRestoreSnapshot(snapshotName, destination, userEmail);
        Assert.assertNotNull(restoration);

        String emailBody = emailBodyCapture.getValue();
        Assert.assertTrue("Expecting snapshot ID in email body",
                          emailBody.contains(snapshotName));
        Assert.assertTrue("Expecting host in email body",
                          emailBody.contains(host));
        Assert.assertTrue("Expecting port in email body",
                          emailBody.contains(String.valueOf(port)));
        Assert.assertTrue("Expecting storeId ID in email body",
                          emailBody.contains(storeId));
        Assert.assertTrue("Expecting user email  body",
                          emailBody.contains(userEmail));

    }

    @Test
    public void testExtractAccountId() {
        replayAll();
        Assert.assertEquals("host", manager.extractAccountId("host.duracloud.org"));
        Assert.assertEquals("localhost", manager.extractAccountId("localhost"));

    }

    /**
     * Test method for {@link org.duracloud.snapshot.service.impl.RestoreManagerImpl#getRestoration(java.lang.String)}.
     *
     * @throws SnapshotException
     * @throws SnapshotNotFoundException
     */
    @Test
    public void testGetRestoreStatus() throws SnapshotException {
        expect(restoreRepo.findByRestorationId(restorationId)).andReturn(null);
        replayAll();

        try {
            this.manager.getRestoration(restorationId);
            Assert.fail();
        } catch (RestorationNotFoundException ex) {
            Assert.assertTrue(true);
        }
    }

    /**
     *
     */
    private void setupManager() {
        manager =
            new RestoreManagerImpl();
        RestoreManagerConfig config = new RestoreManagerConfig();
        config.setTargetStoreEmailAddresses(new String[] {"a"});
        config.setDuracloudEmailAddresses(new String[] {duracloudEmail});
        config.setRestorationRootDir(System.getProperty("java.io.tmpdir")
                                     + File.separator + System.currentTimeMillis());
        manager.setStoreClientHelper(storeClientHelper);
        manager.setSnapshotRepo(snapshotRepo);
        manager.setRestoreRepo(restoreRepo);
        manager.setNotificationManager(notificationManager);
        manager.setBridgeConfig(bridgeConfig);
        manager.setSnapshotManager(snapshotManager);
        manager.setEventLog(eventLog);
        manager.init(config, jobManager);
    }

    /**
     * Test method for
     * {@link org.duracloud.snapshot.service.impl.RestoreManagerImpl#restoreCompleted(java.lang.String)}.
     */
    @Test
    public void testRestoreComplete() throws Exception {

        expect(restoreRepo.saveAndFlush(isA(Restoration.class))).andReturn(restoration);
        eventLog.logRestoreUpdate(restoration);
        expectLastCall();

        expect(this.jobManager.executeRestoration(isA(String.class)))
            .andReturn(BatchStatus.UNKNOWN);

        setupGetRestoreStatus();
        expect(restoration.getStatus()).andReturn(RestoreStatus.INITIALIZED);
        expect(restoration.getRestorationId()).andReturn(restorationId);

        restoration.setStatusText(isA(String.class));
        expectLastCall();
        restoration.setStatus(RestoreStatus.STORAGE_RETRIEVAL_COMPLETE);
        expectLastCall();

        replayAll();

        restoration = manager.restoreCompleted(restorationId);
        Assert.assertNotNull(restoration);
        Thread.sleep(1000);

    }

    /**
     *
     */
    private void setupGetRestoreStatus() {
        expect(this.restoreRepo.findByRestorationId(restorationId)).andReturn(restoration);
        expect(restoration.getStatus()).andReturn(RestoreStatus.RETRIEVING_FROM_STORAGE);
    }

    @Test
    public void testGet() throws Exception {

        Restoration restoration = createMock(Restoration.class);
        expect(restoreRepo.findByRestorationId(restorationId)).andReturn(restoration);
        replayAll();

        Restoration output = this.manager.get(restorationId);

        Assert.assertNotNull(output);
    }

    @Test
    public void testFinalizeRestores() throws Exception {
        String dcUser = "dcUser";
        String dcPass = "dcPass";
        String host = "host";
        String spaceId = "spaceId";
        String restorationId = "restorationId";

        List<Restoration> restorationList = new ArrayList<>();
        restorationList.add(restoration);
        expect(restoreRepo.findByStatus(RestoreStatus.RESTORATION_COMPLETE))
            .andReturn(restorationList);

        Date fiveMinutesAgo = new Date(System.currentTimeMillis() - 300000);
        expect(restoration.getExpirationDate()).andReturn(fiveMinutesAgo);

        expect(restoration.getDestination()).andReturn(destination);

        expect(bridgeConfig.getDuracloudUsername()).andReturn(dcUser);
        expect(bridgeConfig.getDuracloudPassword()).andReturn(dcPass);
        expect(storeClientHelper.create(destination, dcUser, dcPass))
            .andReturn(contentStore);

        expect(destination.getSpaceId()).andReturn(spaceId);
        expect(destination.getHost()).andReturn(host);
        expect(contentStore.spaceExists(spaceId)).andReturn(true);
        expect(contentStore.getSpaceContents(spaceId))
            .andReturn(Collections.<String>emptyList().iterator());

        contentStore.deleteSpace(spaceId);
        expectLastCall();

        expect(restoration.getStatus()).andReturn(RestoreStatus.RESTORATION_COMPLETE);
        restoration.setStatus(RestoreStatus.RESTORATION_EXPIRED);
        expectLastCall();
        restoration.setStatusText(isA(String.class));
        expectLastCall();
        expect(restoration.getRestorationId()).andReturn(restorationId).times(2);

        expect(restoreRepo.saveAndFlush(restoration)).andReturn(restoration);
        eventLog.logRestoreUpdate(restoration);
        expectLastCall();
        expect(restoration.getSnapshot()).andReturn(snapshot);

        Capture<String> historyCapture = Capture.newInstance(CaptureType.FIRST);
        expect(snapshotManager.updateHistory(EasyMock.eq(snapshot),
                                             EasyMock.capture(historyCapture)))
            .andReturn(snapshot);

        replayAll();

        manager.finalizeRestores();

        String history = historyCapture.getValue();
        String expectedHistory =
            "[{'restore-action':'RESTORE_EXPIRED'}," +
            "{'restore-id':'" + restorationId + "'}]";
        assertEquals(expectedHistory, history.replaceAll("\\s", ""));
    }

    @Test
    public void testCancel() throws Exception {
        this.jobManager.cancelRestore(restorationId);
        expectLastCall();
        this.restoreRepo.deleteByRestorationId(restorationId);
        expectLastCall();
        replayAll();
        this.manager.cancelRestore(restorationId);
    }

    @Test
    public void testRestart() throws Exception {
        expect(restoreRepo.save(restoration)).andReturn(restoration);
        expect(restoreRepo.saveAndFlush(restoration)).andReturn(restoration);
        eventLog.logRestoreUpdate(restoration);
        expectLastCall().times(2);
        restoration.setEndDate(null);
        restoration.setStatus(RestoreStatus.RETRIEVING_FROM_STORAGE);
        expectLastCall();

        expect(restoration.getStatus()).andReturn(RestoreStatus.RETRIEVING_FROM_STORAGE).times(2);
        restoration.setStatus(RestoreStatus.STORAGE_RETRIEVAL_COMPLETE);
        expectLastCall();
        restoration.setStatusText(isA(String.class));
        expectLastCall();

        expect(restoration.getRestorationId()).andReturn(restorationId);
        expect(this.jobManager.stopRestore(restorationId)).andReturn(restoration);
        expect(this.jobManager.executeRestoration(restorationId)).andReturn(BatchStatus.STARTED);
        replayAll();
        this.manager.restartRestore(restorationId);
        Thread.sleep(1000);
    }

}

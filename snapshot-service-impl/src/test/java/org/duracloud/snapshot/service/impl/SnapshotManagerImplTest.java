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
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
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
import org.duracloud.snapshot.service.BridgeConfiguration;
import org.duracloud.snapshot.service.SnapshotManagerException;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.Mock;
import org.easymock.TestSubject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

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

    /**
     * @throws java.lang.Exception
     */
    @Before
    @Override
    public void setup() {
        super.setup();
        manager =
            new SnapshotManagerImpl();
        manager.setBridgeConfig(bridgeConfig);
        manager.setNotificationManager(notificationManager);
        manager.setSnapshotContentItemRepo(snapshotContentItemRepo);
        manager.setSnapshotRepo(snapshotRepo);
        manager.setSnapshotTaskClientHelper(snapshotTaskClientHelper);
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
        EasyMock.expect(this.snapshotContentItemRepo.save(EasyMock.capture(contentItemCapture)))
                .andReturn(createMock(SnapshotContentItem.class));
        replayAll();
        manager.addContentItem(snapshot, contentId, props);

        SnapshotContentItem item = contentItemCapture.getValue();

        Assert.assertEquals(contentId, item.getContentId());
        Assert.assertTrue(item.getMetadata().contains("\"key\""));
        Assert.assertTrue(item.getMetadata().contains("\"value\""));
        Assert.assertNotNull(item.getContentIdHash());

    }

    @Test
    public void testTransferToDpnNodeComplete()
        throws SnapshotException,
            ContentStoreException {
        String snapshotId = "snapshot-name";

        EasyMock.expect(snapshotRepo.findByName(snapshotId))
                .andReturn(snapshot);
        snapshot.setStatus(SnapshotStatus.CLEANING_UP);
        EasyMock.expectLastCall();


        EasyMock.expect(snapshot.getName())
        .andReturn(snapshotId);

        
        File root = new File(System.getProperty("java.io.tmpdir")
                             + File.separator
                             + System.currentTimeMillis());
        File dir =
            new File(ContentDirUtils.getDestinationPath(snapshotId,
                                                        root));
        dir.mkdirs();
        Assert.assertTrue(dir.exists());

        EasyMock.expect(this.bridgeConfig.getDuracloudUsername()).andReturn("username");
        EasyMock.expect(this.bridgeConfig.getDuracloudPassword()).andReturn("password");
        EasyMock.expect(this.bridgeConfig.getContentRootDir()).andReturn(root);

        EasyMock.expect(snapshot.getSource()).andReturn(endPointConfig);
        EasyMock.expect(snapshotTaskClientHelper.create(EasyMock.eq(endPointConfig),
                                                        EasyMock.isA(String.class),
                                                        EasyMock.isA(String.class)))
                .andReturn(snapshotTaskClient);
        EasyMock.expect(snapshotTaskClient.cleanupSnapshot(snapshotId))
                .andReturn(new CleanupSnapshotTaskResult());
        EasyMock.expect(snapshotRepo.saveAndFlush(EasyMock.isA(Snapshot.class)))
                .andReturn(snapshot);

        EasyMock.expectLastCall();
        replayAll();

        Snapshot snapshot = this.manager.transferToDpnNodeComplete(snapshotId);
        Assert.assertNotNull(snapshot);
        Assert.assertFalse(new File(dir.getAbsolutePath()).exists());

        try {
            FileUtils.deleteDirectory(root);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Test
    public void testCleanupComplete() throws SnapshotException {
        String snapshotId = "snapshot-name";

        EasyMock.expect(snapshotRepo.findByName(snapshotId))
                .andReturn(snapshot);
        snapshot.setStatus(SnapshotStatus.SNAPSHOT_COMPLETE);
        EasyMock.expectLastCall();
        snapshot.setEndDate(EasyMock.isA(Date.class));
        EasyMock.expectLastCall();
        String adminEmail = "admin-email";
        String userEmail = "email";

        EasyMock.expect(snapshot.getUserEmail()).andReturn(userEmail);

        String[] stringArray = new String[] { adminEmail };
        EasyMock.expect(bridgeConfig.getDuracloudEmailAddresses())
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

        Snapshot snapshot = this.manager.cleanupComplete(snapshotId);
        Assert.assertNotNull(snapshot);

    }

}

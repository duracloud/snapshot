/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.rest;

import org.duracloud.common.notification.NotificationManager;
import org.duracloud.common.notification.NotificationType;
import org.duracloud.snapshot.bridge.rest.SnapshotRequestParams;
import org.duracloud.snapshot.bridge.rest.SnapshotResource;
import org.duracloud.snapshot.bridge.service.BridgeConfiguration;
import org.duracloud.snapshot.common.test.SnapshotTestBase;
import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.db.model.SnapshotStatus;
import org.duracloud.snapshot.db.repo.SnapshotRepo;
import org.duracloud.snapshot.manager.SnapshotException;
import org.duracloud.snapshot.manager.SnapshotJobManager;
import org.easymock.EasyMock;
import org.easymock.Mock;
import org.easymock.TestSubject;
import org.junit.Test;
import org.springframework.batch.core.BatchStatus;

/**
 * @author Daniel Bernstein Date: Feb 4, 2014
 */

public class SnapshotResourceTest extends SnapshotTestBase {

    @Mock
    private SnapshotJobManager manager;

    @Mock
    private SnapshotRepo snapshotRepo;

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
                                 notificationManager,
                                 bridgeConfiguration);
    }

    @Test
    public void testGetStatusSuccess() throws SnapshotException {

        EasyMock.expect(snapshotRepo.findByName("snapshotName"))
                .andReturn(snapshot);
        EasyMock.expect(snapshot.getStatus())
                .andReturn(SnapshotStatus.SNAPSHOT_COMPLETE);
        EasyMock.expect(snapshot.getStatusText())
        .andReturn("status text");

        replayAll();
        resource.getStatus("snapshotName");
    }

    @Test
    public void testGetStatusNotFound() throws SnapshotException {
        EasyMock.expect(snapshotRepo.findByName("snapshotName")).andReturn(null);
        replayAll();
        resource.getStatus("snapshotName");
    }

    @Test
    public void testCreate() throws SnapshotException {
        String host = "host";
        String port = "444";
        String storeId = "storeId";
        String spaceId = "spaceId";
        String snapshotName = "snapshot-name";
        String description = "description";
        String email = "email";

        EasyMock.expect(manager.executeSnapshot(snapshotName))
                .andReturn(BatchStatus.UNKNOWN);

        EasyMock.expect(snapshotRepo.findByName(snapshotName)).andReturn(null);

        EasyMock.expect(snapshotRepo.saveAndFlush(EasyMock.isA(Snapshot.class)))
                .andReturn(snapshot);

        replayAll();

        resource.create(snapshotName, new SnapshotRequestParams(host,
                                                              port,
                                                              storeId,
                                                              spaceId,
                                                              description,
                                                              email));

    }

    @Test
    public void testComplete() throws SnapshotException {
        String snapshotId = "snapshot-name";

        EasyMock.expect(snapshotRepo.findByName(snapshotId))
                .andReturn(snapshot);
        snapshot.setStatus(SnapshotStatus.SNAPSHOT_COMPLETE);
        String adminEmail = "admin-email";
        String userEmail = "email";

        EasyMock.expect(snapshot.getUserEmail()).andReturn(userEmail);
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

}

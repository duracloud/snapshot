/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import static org.easymock.EasyMock.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.duracloud.client.ContentStore;
import org.duracloud.common.notification.NotificationManager;
import org.duracloud.common.notification.NotificationType;
import org.duracloud.snapshot.SnapshotException;
import org.duracloud.snapshot.SnapshotInProcessException;
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
import org.duracloud.snapshot.service.RestorationNotFoundException;
import org.duracloud.snapshot.service.RestoreManagerConfig;
import org.duracloud.snapshot.service.SnapshotJobManager;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.Mock;
import org.easymock.TestSubject;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.batch.core.BatchStatus;

/**
 * @author Daniel Bernstein
 *         Date: Jul 16, 2014
 */
public class RestoreManagerImplTest  extends SnapshotTestBase {

    @Mock
    private SnapshotJobManager jobManager;
    
    @Mock
    private NotificationManager notificationManager;

    @Mock
    private RestoreRepo restoreRepo;
    
    @Mock
    private SnapshotRepo snapshotRepo;

    @TestSubject
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

    private String snapshotName = "snapshot-name";

    private String restorationId = "restoration-id";

    private String userEmail = "user-email";

    /* (non-Javadoc)
     * @see org.duracloud.snapshot.common.test.SnapshotTestBase#setup()
     */
    @Override
    public void setup() {
        super.setup();
        setupManager();
    }
    /**

    /**
     * Test method for {@link org.duracloud.snapshot.service.impl.RestoreManagerImpl#restoreSnapshot(String, org.duracloud.snapshot.db.model.DuracloudEndPointConfig, String)}.
     * @throws SnapshotException 
     */
    @Test
    public void testRestoreSnapshot() throws SnapshotException {
        expect(snapshotRepo.findByName(snapshotName)).andReturn(snapshot);
        expect(snapshot.getStatus()).andReturn(SnapshotStatus.SNAPSHOT_COMPLETE);
        DuracloudEndPointConfig source = createMock(DuracloudEndPointConfig.class);
        expect(source.getHost()).andReturn("host");
        expect(source.getStoreId()).andReturn("store-id");
        expect(source.getSpaceId()).andReturn("space-id");
        expect(snapshot.getSource()).andReturn(source);

        expect(restoreRepo.saveAndFlush(isA(Restoration.class))).andReturn(restoration);
        Capture<String> emailBodyCapture = new Capture<>();
        notificationManager.sendNotification(isA(NotificationType.class),
                                             isA(String.class),
                                             capture(emailBodyCapture),
                                             isA(String.class),
                                             isA(String.class));
        expectLastCall();
        
        expect(restoration.getRestorationId()).andReturn(restorationId);
        replayAll();
        Restoration restoration = manager.restoreSnapshot(snapshotName, destination, userEmail );
        Assert.assertNotNull(restoration);

        String emailBody = emailBodyCapture.getValue();
        Assert.assertTrue("Expecting snapshot ID in email body",
                          emailBody.contains(snapshotName));
        Assert.assertTrue("Expecting restore ID in email body",
                          emailBody.contains(restorationId));
    }

    /**
     * Test method for {@link org.duracloud.snapshot.service.impl.RestoreManagerImpl#getRestoration(java.lang.String)}.
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
        }catch(RestorationNotFoundException ex){
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
        config.setDpnEmailAddresses(new String[] {"a"});
        config.setDuracloudEmailAddresses(new String[]{"b"});
        config.setRestorationRootDir(System.getProperty("java.io.tmpdir")
            + File.separator + System.currentTimeMillis());
        manager.init(config, jobManager);
    }

    /**
     * Test method for {@link org.duracloud.snapshot.service.impl.RestoreManagerImpl#restoreCompleted(java.lang.String)}.
     */
    @Test
    public void testRestoreComplete() throws SnapshotException{

        expect(restoreRepo.saveAndFlush(isA(Restoration.class))).andReturn(restoration);

        expect(this.jobManager.executeRestoration(isA(String.class)))
                .andReturn(BatchStatus.UNKNOWN);
        
        setupGetRestoreStatus();
        expect(restoration.getStatus()).andReturn(RestoreStatus.INITIALIZED);

        restoration.setStatusText(isA(String.class));
        expectLastCall();
        restoration.setStatus(RestoreStatus.DPN_TRANSFER_COMPLETE);
        expectLastCall();
        expect(restoreRepo.findByRestorationId(restorationId)).andReturn(restoration);

        replayAll();
        
        restoration = manager.restoreCompleted(restorationId);
        Assert.assertNotNull(restoration);

    }
    /**
     * 
     */
    private void setupGetRestoreStatus() {
        expect(this.restoreRepo.findByRestorationId(restorationId)).andReturn(restoration);
        expect(restoration.getStatus()).andReturn(RestoreStatus.WAITING_FOR_DPN);
    }
    
    @Test
    public void testGet() throws Exception{
        
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
        expect(contentStore.getSpaceContents(spaceId))
            .andReturn(Collections.<String>emptyList().iterator());

        contentStore.deleteSpace(spaceId);
        expectLastCall();

        expect(restoration.getStatus()).andReturn(RestoreStatus.RESTORATION_COMPLETE);
        restoration.setStatus(RestoreStatus.RESTORATION_EXPIRED);
        expectLastCall();
        restoration.setStatusText(isA(String.class));
        expectLastCall();
        expect(restoration.getRestorationId()).andReturn(restorationId);

        expect(restoreRepo.saveAndFlush(restoration)).andReturn(restoration);

        replayAll();

        manager.finalizeRestores();
    }

}

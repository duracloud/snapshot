/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.restoration;

import static org.easymock.EasyMock.isA;

import java.io.File;

import org.duracloud.common.notification.NotificationManager;
import org.duracloud.common.notification.NotificationType;
import org.duracloud.snapshot.common.test.SnapshotTestBase;
import org.duracloud.snapshot.db.model.DuracloudEndPointConfig;
import org.duracloud.snapshot.db.model.Restoration;
import org.duracloud.snapshot.db.model.RestorationStatus;
import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.db.model.SnapshotStatus;
import org.duracloud.snapshot.db.repo.RestorationRepo;
import org.duracloud.snapshot.db.repo.SnapshotRepo;
import org.duracloud.snapshot.manager.SnapshotException;
import org.duracloud.snapshot.manager.SnapshotInProcessException;
import org.duracloud.snapshot.manager.SnapshotJobManager;
import org.duracloud.snapshot.manager.SnapshotNotFoundException;
import org.duracloud.snapshot.service.RestorationManagerConfig;
import org.duracloud.snapshot.service.RestorationManagerImpl;
import org.duracloud.snapshot.service.RestorationNotFoundException;
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
public class RestorationManagerTest  extends SnapshotTestBase {

    @Mock
    private SnapshotJobManager jobManager;
    
    @Mock
    private NotificationManager notificationManager;

    @Mock
    private RestorationRepo restorationRepo;
    
    @Mock
    private SnapshotRepo snapshotRepo;

    @TestSubject
    private RestorationManagerImpl manager;
    
    @Mock
    private DuracloudEndPointConfig destination;
    
    @Mock
    private Snapshot snapshot;

    @Mock
    private Restoration restoration;

    private String snapshotName = "snapshot-name";

    private Long restorationId = 1000l;

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
     * Test method for {@link org.duracloud.snapshot.service.RestorationManagerImpl#restoreSnapshot(java.lang.String)}.
     * @throws SnapshotException 
     * @throws SnapshotInProcessException 
     * @throws SnapshotNotFoundException 
     */
    @Test
    public void testRestoreSnapshot() throws SnapshotNotFoundException, SnapshotInProcessException, SnapshotException {
        setupRestoreCall();
        replayAll();
        Restoration restoration = manager.restoreSnapshot(snapshotName, destination);
        Assert.assertNotNull(restoration);
        Assert.assertEquals(restoration.getStatus(), RestorationStatus.WAITING_FOR_DPN);
    }
    /**
     * @throws SnapshotNotFoundException
     * @throws SnapshotException
     */
    private void setupRestoreCall()
        throws SnapshotNotFoundException,
            SnapshotException {
        notificationManager.sendNotification(isA(NotificationType.class),
                                             isA(String.class),
                                             isA(String.class),
                                             EasyMock.isA(String.class),
                                             EasyMock.isA(String.class));
        EasyMock.expect(snapshotRepo.findByName(snapshotName)).andReturn(snapshot);
        EasyMock.expect(snapshot.getStatus()).andReturn(SnapshotStatus.SNAPSHOT_COMPLETE);
        EasyMock.expect(restorationRepo.save(EasyMock.isA(Restoration.class))).andReturn(restoration).times(2);
        
    }

    /**
     * Test method for {@link org.duracloud.snapshot.service.RestorationManagerImpl#getRestoration(java.lang.String)}.
     * @throws SnapshotException 
     * @throws SnapshotNotFoundException 
     */
    @Test
    public void testGetRestoreStatus() throws SnapshotException {
        EasyMock.expect(restorationRepo.getOne(EasyMock.isA(Long.class))).andReturn(null);
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
            new RestorationManagerImpl(jobManager,
                                       notificationManager,
                                       restorationRepo,
                                       snapshotRepo);
        RestorationManagerConfig config = new RestorationManagerConfig();
        config.setDpnEmailAddresses(new String[] {"a"});
        config.setDuracloudEmailAddresses(new String[]{"b"});
        config.setRestorationRootDir(System.getProperty("java.io.tmpdir")
            + File.separator + System.currentTimeMillis());
        manager.init(config);
    }

    /**
     * Test method for {@link org.duracloud.snapshot.service.RestorationManagerImpl#restorationCompleted(java.lang.String)}.
     */
    @Test
    public void testSnapshotRestorationCompleted() throws SnapshotException{
        setupRestoreCall();
        
        EasyMock.expect(this.jobManager.executeRestoration(EasyMock.isA(Long.class)))
                .andReturn(BatchStatus.UNKNOWN);
        
        setupGetRestoreStatus();
        restoration.setMemo(EasyMock.isA(String.class));
        EasyMock.expectLastCall();
        restoration.setStatus(RestorationStatus.DPN_TRANSFER_COMPLETE);
        EasyMock.expectLastCall();
        EasyMock.expect(restorationRepo.save(EasyMock.isA(Restoration.class))).andReturn(restoration);
        EasyMock.expect(restoration.getId()).andReturn(restorationId);
        
        replayAll();
        
        Restoration restoration = manager.restoreSnapshot(snapshotName, destination);
        Assert.assertNotNull(restoration);
        Assert.assertEquals(restoration.getStatus(), RestorationStatus.WAITING_FOR_DPN);

        restoration = manager.restorationCompleted(restorationId);
        Assert.assertNotNull(restoration);
        
    }
    /**
     * 
     */
    private void setupGetRestoreStatus() {
        EasyMock.expect(this.restorationRepo.getOne(restorationId)).andReturn(restoration);
        EasyMock.expect(restoration.getStatus()).andReturn(RestorationStatus.WAITING_FOR_DPN);
    }

}

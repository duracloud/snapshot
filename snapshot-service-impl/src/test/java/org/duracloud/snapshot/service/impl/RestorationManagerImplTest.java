/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import static org.easymock.EasyMock.isA;

import java.io.File;

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
import org.duracloud.snapshot.service.RestorationNotFoundException;
import org.duracloud.snapshot.service.RestoreManagerConfig;
import org.duracloud.snapshot.service.SnapshotJobManager;
import org.duracloud.snapshot.service.impl.RestoreManagerImpl;
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
public class RestorationManagerImplTest  extends SnapshotTestBase {

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

    private String snapshotName = "snapshot-name";

    private Long restorationId = 1000l;

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
     * Test method for {@link org.duracloud.snapshot.service.impl.RestoreManagerImpl#restoreSnapshot(java.lang.String)}.
     * @throws SnapshotException 
     * @throws SnapshotInProcessException 
     * @throws SnapshotNotFoundException 
     */
    @Test
    public void testRestoreSnapshot() throws SnapshotNotFoundException, SnapshotInProcessException, SnapshotException {
        EasyMock.expect(snapshotRepo.findByName(snapshotName)).andReturn(snapshot);
        EasyMock.expect(snapshot.getStatus()).andReturn(SnapshotStatus.SNAPSHOT_COMPLETE);

        EasyMock.expect(restoreRepo.save(EasyMock.isA(Restoration.class))).andReturn(restoration);
        notificationManager.sendNotification(isA(NotificationType.class),
                                             isA(String.class),
                                             isA(String.class),
                                             EasyMock.isA(String.class),
                                             EasyMock.isA(String.class));
        EasyMock.expectLastCall();
        
        EasyMock.expect(restoration.getId()).andReturn(restorationId);
        replayAll();
        Restoration restoration = manager.restoreSnapshot(snapshotName, destination, userEmail );
        Assert.assertNotNull(restoration);
    }
 

    /**
     * Test method for {@link org.duracloud.snapshot.service.impl.RestoreManagerImpl#getRestoration(java.lang.String)}.
     * @throws SnapshotException 
     * @throws SnapshotNotFoundException 
     */
    @Test
    public void testGetRestoreStatus() throws SnapshotException {
        EasyMock.expect(restoreRepo.findOne(EasyMock.isA(Long.class))).andReturn(null);
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
            new RestoreManagerImpl(jobManager,
                                       notificationManager,
                                       restoreRepo,
                                       snapshotRepo);
        RestoreManagerConfig config = new RestoreManagerConfig();
        config.setDpnEmailAddresses(new String[] {"a"});
        config.setDuracloudEmailAddresses(new String[]{"b"});
        config.setRestorationRootDir(System.getProperty("java.io.tmpdir")
            + File.separator + System.currentTimeMillis());
        manager.init(config);
    }

    /**
     * Test method for {@link org.duracloud.snapshot.service.impl.RestoreManagerImpl#restorationCompleted(java.lang.String)}.
     */
    @Test
    public void testRestoreComplete() throws SnapshotException{

        EasyMock.expect(restoreRepo.save(EasyMock.isA(Restoration.class))).andReturn(restoration);

        EasyMock.expect(this.jobManager.executeRestoration(EasyMock.isA(Long.class)))
                .andReturn(BatchStatus.UNKNOWN);
        
        setupGetRestoreStatus();
        EasyMock.expect(restoration.getStatus()).andReturn(RestoreStatus.INITIALIZED);

        restoration.setStatusText(EasyMock.isA(String.class));
        EasyMock.expectLastCall();
        restoration.setStatus(RestoreStatus.DPN_TRANSFER_COMPLETE);
        EasyMock.expectLastCall();
        EasyMock.expect(restoreRepo.findOne(restorationId)).andReturn(restoration);

        replayAll();
        
        restoration = manager.restorationCompleted(restorationId);
        Assert.assertNotNull(restoration);
        
    }
    /**
     * 
     */
    private void setupGetRestoreStatus() {
        EasyMock.expect(this.restoreRepo.findOne(restorationId)).andReturn(restoration);
        EasyMock.expect(restoration.getStatus()).andReturn(RestoreStatus.WAITING_FOR_DPN);
    }
    
    @Test
    public void testGet() throws Exception{
        
        Restoration restoration = createMock(Restoration.class);
        EasyMock.expect(restoreRepo.findOne(EasyMock.anyLong())).andReturn(restoration);
        replayAll();
        
        Restoration output = this.manager.get(1000l);
        
        Assert.assertNotNull(output);
        
    }

}

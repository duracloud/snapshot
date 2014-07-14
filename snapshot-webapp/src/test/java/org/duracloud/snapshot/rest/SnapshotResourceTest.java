/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.rest;

import java.util.concurrent.Future;

import org.duracloud.snapshot.common.test.SnapshotTestBase;
import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.db.model.SnapshotStatus;
import org.duracloud.snapshot.db.repo.SnapshotRepo;
import org.duracloud.snapshot.manager.SnapshotException;
import org.duracloud.snapshot.manager.SnapshotJobManager;
import org.duracloud.snapshot.manager.SnapshotNotFoundException;
import org.easymock.EasyMock;
import org.easymock.Mock;
import org.easymock.TestSubject;
import org.junit.Test;
import org.springframework.batch.core.BatchStatus;

/**
 * @author Daniel Bernstein
 *         Date: Feb 4, 2014
 */

public class SnapshotResourceTest extends SnapshotTestBase {
    
    @Mock
    private SnapshotJobManager manager;
    
    @Mock 
    private Future<BatchStatus> future;
    
    @Mock
    private SnapshotRepo snapshotRepo;
    
    @TestSubject
    private SnapshotResource resource;

    @Mock
    private Snapshot snapshot;
    
    /* (non-Javadoc)
     * @see org.duracloud.snapshot.common.test.EasyMockTestBase#setup()
     */
    @Override
    public void setup() {
        super.setup();
        resource = new SnapshotResource(manager, snapshotRepo);
    }
    
    @Test
    public void testGetStatusSuccess() throws SnapshotException {
        
        EasyMock.expect(snapshotRepo.findByName("snapshotId")).andReturn(snapshot);
        EasyMock.expect(snapshot.getStatus()).andReturn(SnapshotStatus.SNAPSHOT_COMPLETE);
        
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

        EasyMock.expect(manager.executeSnapshot(snapshotId))
                .andReturn(BatchStatus.UNKNOWN);

        EasyMock.expect(snapshotRepo.findByName(snapshotId)).andReturn(null);

        EasyMock.expect(snapshotRepo.saveAndFlush(EasyMock.isA(Snapshot.class)))
                .andReturn(snapshot);

        replayAll();

        resource.create(snapshotId, new SnapshotRequestParams(host,
                                                              port,
                                                              storeId,
                                                              spaceId,
                                                              description));

    }


}

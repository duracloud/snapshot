/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.rest;

import org.duracloud.snapshot.spring.batch.driver.SnapshotConfig;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Daniel Bernstein
 *         Date: Feb 4, 2014
 */
public class SnapshotResourceTest extends EasyMockTestBase {
    
    private SnapshotJobManager manager;
    private SnapshotResource resource;
    
    @Before
    public void setup() {
        manager = createMock(SnapshotJobManager.class);
        resource = new SnapshotResource(manager);
    }

    @Test
    public void testGetStatusSuccess() throws SnapshotException {
        EasyMock.expect(manager.getStatus(EasyMock.isA(String.class)))
                .andReturn(new SnapshotStatus("snapshotId", "testStatus"));
        replay();
        resource.getStatus("snapshotId");
    }

    @Test
    public void testGetStatusNotFound() throws SnapshotException {
        EasyMock.expect(manager.getStatus(EasyMock.isA(String.class)))
                .andThrow(new SnapshotNotFoundException("test"));
        replay();
        resource.getStatus("snapshotId");
    }

    @Test
    public void testCreate() throws SnapshotException {
        replay();
        EasyMock.expect(manager.executeSnapshot(EasyMock.isA(SnapshotConfig.class))).andReturn(new SnapshotStatus("test","test"));
        resource.create("host", "444", "storeId", "spaceId", "snapshotId");
    }
}

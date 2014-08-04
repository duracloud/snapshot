/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import java.util.HashMap;
import java.util.Map;

import org.duracloud.snapshot.common.test.SnapshotTestBase;
import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.db.model.SnapshotContentItem;
import org.duracloud.snapshot.db.repo.SnapshotContentItemRepo;
import org.duracloud.snapshot.service.SnapshotManagerException;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.Mock;
import org.easymock.TestSubject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Daniel Bernstein
 *         Date: Jul 31, 2014
 */
public class SnapshotManagerImplTest extends SnapshotTestBase{

    @TestSubject
    private SnapshotManagerImpl manager;
    
    @Mock
    private SnapshotContentItemRepo snapshotContentItemRepo;
    
    @Mock 
    private Snapshot snapshot;
    /**
     * @throws java.lang.Exception
     */
    @Before
    @Override
    public void setup() {
        super.setup();
        manager = new SnapshotManagerImpl();
    }

     /**
     * Test method for {@link org.duracloud.snapshot.service.impl.SnapshotManagerImpl#addContentItem(java.lang.String, org.duracloud.common.model.ContentItem, java.util.Map)}.
     * @throws SnapshotManagerException 
     */
    @Test
    public void testAddContentItem() throws SnapshotManagerException {
        Map<String,String> props = new HashMap<>();
        props.put("key", "value");
        String contentId = "content-id";
        Capture<SnapshotContentItem> contentItemCapture = new Capture<>();
        EasyMock.expect(this.snapshotContentItemRepo.save(EasyMock.capture(contentItemCapture))).andReturn(createMock(SnapshotContentItem.class));
        replayAll();
        manager.addContentItem(snapshot, contentId, props);
        
        SnapshotContentItem item = contentItemCapture.getValue();
        
        Assert.assertEquals(contentId, item.getContentId());
        Assert.assertTrue(item.getMetadata().contains("\"key\""));
        Assert.assertTrue(item.getMetadata().contains("\"value\""));
        Assert.assertNotNull(item.getContentIdHash());
               
        
    }

}

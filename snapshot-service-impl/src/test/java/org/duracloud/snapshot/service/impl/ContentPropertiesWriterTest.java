/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.duracloud.client.ContentStore;
import org.duracloud.snapshot.common.test.SnapshotTestBase;
import org.easymock.EasyMock;
import org.easymock.Mock;
import org.junit.Test;

/**
 * @author Daniel Bernstein
 *         Date: Aug 26, 2014
 */

public class ContentPropertiesWriterTest extends SnapshotTestBase{

    @Mock
    private ContentStore contentStore;
    /**
     * Test method for {@link org.duracloud.snapshot.service.impl.ContentPropertiesWriter#write(java.util.List)}.
     * @throws Exception 
     */
    @Test
    public void testWrite() throws Exception {
        String destinationSpaceId = "space-id";
        String contentId = "content-id";
        Map<String,String> props = new HashMap<>();
        EasyMock.expect(contentStore.getStoreId()).andReturn("store-id");
        EasyMock.expect(contentStore.getStorageProviderType()).andReturn("store-type");

        contentStore.setContentProperties(EasyMock.eq(destinationSpaceId), EasyMock.eq(contentId), EasyMock.eq(props));
        EasyMock.expectLastCall();
        ContentProperties properties = new ContentProperties(contentId, props);
        List<ContentProperties> list = new ArrayList<>();
        list.add(properties);
        replayAll();
        ContentPropertiesWriter writer = new ContentPropertiesWriter(contentStore, destinationSpaceId);
        writer.write(list);
        
        
    }

}

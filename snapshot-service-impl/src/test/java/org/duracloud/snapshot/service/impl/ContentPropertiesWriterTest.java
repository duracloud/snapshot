/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.duracloud.chunk.manifest.ChunksManifest;
import org.duracloud.client.ContentStore;
import org.duracloud.error.ContentStoreException;
import org.duracloud.snapshot.common.test.SnapshotTestBase;
import org.easymock.Mock;
import org.junit.Test;

/**
 * @author Daniel Bernstein
 * Date: Aug 26, 2014
 */

public class ContentPropertiesWriterTest extends SnapshotTestBase {

    @Mock
    private ContentStore contentStore;

    /**
     * Test method for {@link org.duracloud.snapshot.service.impl.ContentPropertiesWriter#write(java.util.List)}.
     *
     * @throws Exception
     */
    @Test
    public void testWrite() throws Exception {
        String destinationSpaceId = "space-id";
        String contentId = "content-id";
        Map<String, String> props = new HashMap<>();
        props.put("content-type", "stitched-file-value");
        props.put("non-system-prop", "value");

        Map<String, String> manifestProps = new HashMap<>();
        manifestProps.put("content-type", "original-manifest-value");

        Map<String, String> combinedProps = new HashMap<>();
        combinedProps.putAll(props);
        combinedProps.putAll(manifestProps);

        expect(contentStore.getStoreId()).andReturn("store-id");
        expect(contentStore.getStorageProviderType()).andReturn("store-type");

        contentStore.setContentProperties(eq(destinationSpaceId), eq(contentId), eq(props));
        expectLastCall().andThrow(new ContentStoreException("test"));

        expect(contentStore.getContentProperties(eq(destinationSpaceId),
                                                 eq(contentId + ChunksManifest.manifestSuffix)))
            .andReturn(manifestProps);

        contentStore.setContentProperties(eq(destinationSpaceId),
                                          eq(contentId + ChunksManifest.manifestSuffix),
                                          eq(combinedProps));
        expectLastCall();

        ContentProperties properties = new ContentProperties(contentId, props);
        List<ContentProperties> list = new ArrayList<>();
        list.add(properties);
        replayAll();
        ContentPropertiesWriter writer = new ContentPropertiesWriter(contentStore, destinationSpaceId);
        writer.write(list);

    }

}

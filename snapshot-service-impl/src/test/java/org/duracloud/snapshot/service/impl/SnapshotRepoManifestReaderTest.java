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
import static org.easymock.EasyMock.isA;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.LinkedList;
import java.util.List;

import org.duracloud.snapshot.db.model.SnapshotContentItem;
import org.duracloud.snapshot.db.repo.SnapshotContentItemRepo;
import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.easymock.Mock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * @author Daniel Bernstein Date: Aug 3, 2015
 */
@RunWith(EasyMockRunner.class)
public class SnapshotRepoManifestReaderTest extends EasyMockSupport {

    @Mock
    private Page page;

    @Mock
    private SnapshotContentItemRepo repo;

    private String snapshotName = "snapsphot-name";

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown() throws Exception {
        verifyAll();
    }

    /**
     * Test method for
     * {@link org.duracloud.snapshot.service.impl.SnapshotRepoManifestReader#read()}
     * .
     */
    @Test
    public void testRead() throws Exception {

        int count = 3;
        setupRepo(count);

        replayAll();
        SnapshotRepoManifestReader reader = new SnapshotRepoManifestReader(repo, snapshotName) {
            protected long getItemsRead() {
                return 0;
            }
        };

        for (int i = 0; i < count; i++) {
            assertNotNull(reader.read());
        }

        assertNull(reader.read());
    }

    /**
     *
     */
    private void setupRepo(int count) {
        List<SnapshotContentItem> items = new LinkedList<>();
        for (int i = 0; i < count; i++) {
            items.add(new SnapshotContentItem());
        }

        expect(page.getTotalPages()).andReturn(1);
        expect(page.getContent()).andReturn(items);
        expect(repo.findBySnapshotName(eq(snapshotName), isA(Pageable.class))).andReturn(page);
    }

}

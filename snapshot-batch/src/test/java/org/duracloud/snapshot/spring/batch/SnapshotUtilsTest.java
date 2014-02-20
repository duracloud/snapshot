/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.spring.batch;

import java.io.File;

import org.duracloud.snapshot.common.test.SnapshotTestBase;
import org.duracloud.snapshot.spring.batch.config.SnapshotConfig;
import org.duracloud.snapshot.spring.batch.config.SnapshotJobManagerConfig;
import org.easymock.EasyMock;
import org.easymock.Mock;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Daniel Bernstein
 *         Date: Feb 20, 2014
 */
public class SnapshotUtilsTest extends SnapshotTestBase{
    String snapshotId = "test-id";

    
    @Mock
    private SnapshotConfig snapshotConfig;
    
    @Mock
    private SnapshotJobManagerConfig snapshotJobManagerConfig;

    @Test
    public void testResolveContentDirIllegalArguments() {
        EasyMock.expect(snapshotConfig.getContentDir()).andReturn(null);
        EasyMock.expect(snapshotJobManagerConfig.getContentRootDir()).andReturn(null);
        replayAll();
        try {
            SnapshotUtils.resolveContentDir(snapshotConfig, snapshotJobManagerConfig);
            Assert.fail();
        }catch(IllegalArgumentException ex){}
    }

    @Test
    public void testResolveContentDirNullContentDir() {
        EasyMock.expect(snapshotConfig.getContentDir()).andReturn(null);
        File contentRootDir = setupContentRoot();
        setupSnapshotId();
        replayAll();
        File contentDir = SnapshotUtils.resolveContentDir(snapshotConfig, snapshotJobManagerConfig);
        Assert.assertNotNull(contentDir);
        Assert.assertEquals(new File(contentRootDir, snapshotId).getAbsolutePath(), contentDir.getAbsolutePath());
    }

    @Test
    public void testResolveContentDirNullContentRootDir() {
        File expectedDir = getTempDir();
        EasyMock.expect(snapshotConfig.getContentDir()).andReturn(expectedDir);
        replayAll();
        File contentDir = SnapshotUtils.resolveContentDir(snapshotConfig, snapshotJobManagerConfig);
        Assert.assertNotNull(contentDir);
        Assert.assertEquals(expectedDir, contentDir);
    }

    /**
     * 
     */
    private void setupSnapshotId() {
        EasyMock.expect(snapshotConfig.getSnapshotId()).andReturn(snapshotId);
    }

    /**
     * @return
     */
    private File setupContentRoot() {
        File contentRootDir = getTempDir();
        EasyMock.expect(snapshotJobManagerConfig.getContentRootDir()).andReturn(contentRootDir);
        return contentRootDir;
    }

}

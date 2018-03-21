/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.common.test;

import java.io.File;

import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

/**
 * A handy base class to simplify the writing of unit tests with easymock.
 *
 * @author Daniel Bernstein
 * Date: Feb 12, 2014
 */
@RunWith(EasyMockRunner.class)
public class SnapshotTestBase extends EasyMockSupport {

    @BeforeClass
    public static void beforeClass() {
        String bridgeLogPropKey = "bridge.log.dir";
        File logDir = new File(System.getProperty("java.io.tmpdir") + File.separator + "duracloud-bridge");
        logDir.mkdirs();
        System.setProperty(bridgeLogPropKey, logDir.getAbsolutePath());
    }

    @Before
    public void setup() throws Exception {
    }

    @After
    public void tearDown() {
        verifyAll();
    }

    protected File getTempDir() {
        File tempdir = new File(System.getProperty("java.io.tmpdir"), System.currentTimeMillis() + "");
        tempdir.deleteOnExit();
        return tempdir;
    }
}

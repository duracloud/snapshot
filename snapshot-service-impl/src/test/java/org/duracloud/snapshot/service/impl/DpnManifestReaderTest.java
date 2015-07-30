/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import static org.junit.Assert.*;

import java.io.File;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Daniel Bernstein
 *         Date: Jul 29, 2015
 */
public class DpnManifestReaderTest {

    
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
    }

    /**
     * Test method for {@link org.duracloud.snapshot.service.impl.DpnManifestReader#read()}.
     */
    @Test
    public void testRead() throws Exception {
        File manifestFile = File.createTempFile("test", "txt");
        
        List<ManifestEntry> list = ManifestTestHelper.setupManifestFile(manifestFile);
        DpnManifestReader reader = new DpnManifestReader(manifestFile);
        ManifestEntry entry = null;
        int index = 0;

        while((entry = reader.read()) != null){
            assertEquals(list.get(index), entry);
            index++;
        }
    }
}

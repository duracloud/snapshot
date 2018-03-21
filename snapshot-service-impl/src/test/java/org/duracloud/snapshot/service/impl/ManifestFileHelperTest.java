/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.text.ParseException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Daniel Bernstein
 * Date: Jul 31, 2015
 */
public class ManifestFileHelperTest {

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

    @Test
    public void testParseManifestEntry() throws ParseException {
        // space
        verifySuccess("checksum", "contentId", ManifestFileHelper.parseManifestEntry("checksum data/contentId"));
        // spaces
        verifySuccess("checksum", "contentId", ManifestFileHelper.parseManifestEntry("checksum  data/contentId"));
        // tabs and spaces
        verifySuccess("checksum", "contentId",
                      ManifestFileHelper.parseManifestEntry("checksum          data/contentId"));
    }

    @Test
    public void testFailure() throws ParseException {
        try {
            ManifestFileHelper.parseManifestEntry("checksumcontentId");
            fail("unexpected failure");
        } catch (ParseException ex) {
            assertTrue("expected failure", true);
        }
    }

    @Test
    public void testFailureWithNewLine() throws ParseException {
        try {
            ManifestFileHelper.parseManifestEntry("checksum\ncontentId");
            fail("unexpected failure");
        } catch (ParseException ex) {
            assertTrue("expected failure", true);
        }
    }

    /**
     * @param string
     * @param string2
     */
    private void verifySuccess(String checksum, String contentId, ManifestEntry entry) {
        assertEquals(checksum, entry.getChecksum());
        assertEquals(contentId, entry.getContentId());
    }

    /**
     * @param string
     * @param string2
     */
    private void verifyFailure(String checksum, String contentId, ManifestEntry entry) {
        assertEquals(checksum, entry.getChecksum());
        assertEquals(contentId, entry.getContentId());
    }

}

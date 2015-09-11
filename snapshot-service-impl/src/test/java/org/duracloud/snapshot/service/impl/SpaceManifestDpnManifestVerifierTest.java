/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.util.LinkedList;
import java.util.List;

import org.duracloud.common.constant.Constants;
import org.duracloud.common.constant.ManifestFormat;
import org.duracloud.common.model.ContentItem;
import org.duracloud.manifest.impl.TsvManifestFormatter;
import org.duracloud.manifeststitch.StitchedManifestGenerator;
import org.duracloud.mill.db.model.ManifestItem;
import org.duracloud.snapshot.common.test.SnapshotTestBase;
import org.easymock.Mock;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Daniel Bernstein Date: Sep 11, 2015
 */
public class SpaceManifestDpnManifestVerifierTest extends SnapshotTestBase {

    private String spaceId = "space-id";
    private String correctChecksum = "correct";
    private String incorrectChecksum = "incorrect";
    private String contentIdPrefix = "content-id-";
    private File md5Manifest;
    private int count = 5;
    @Mock
    private StitchedManifestGenerator generator;

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
        md5Manifest = File.createTempFile("manifest", "tmp");
        md5Manifest.deleteOnExit();

    }

    private List<ContentItem> createContentItems(int count, String contentIdPrefix) {
        List<ContentItem> items = new LinkedList<>();
        for (int i = 0; i < count; i++) {
            items.add(new ContentItem(spaceId, contentIdPrefix + 1));
        }

        return items;
    }

    @Test
    public void testSuccess() throws Exception {
        List<ContentItem> items = createContentItems(count, contentIdPrefix);
        expect(generator.generate(spaceId, ManifestFormat.TSV)).andReturn(createManifestInputStream(items));
        ManifestTestHelper.setupManifestFile(md5Manifest, items.size(), correctChecksum, contentIdPrefix);
        replayAll();
        SpaceManifestDpnManifestVerifier verifier = setupVerifier();

        assertTrue(verifier.verify());
        assertTrue(verifier.getErrors().isEmpty());
    }

    private InputStream createManifestInputStream(List<ContentItem> items) throws Exception {
        File manifestFile = File.createTempFile("manifest", "tsv");
        manifestFile.deleteOnExit();
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(manifestFile)));
        TsvManifestFormatter formatter = new TsvManifestFormatter();
        writer.write(formatter.getHeader() + "\n");
        for (int i = 0; i < items.size(); i++) {
            ContentItem item = items.get(i);
            if (item.getContentId().equals(Constants.SNAPSHOT_PROPS_FILENAME)) {
                continue;
            }
            ManifestItem manifestItem = new ManifestItem();
            manifestItem.setContentId(item.getContentId());
            manifestItem.setContentChecksum(correctChecksum);
            writer.write(formatter.formatLine(manifestItem) + "\n");
        }

        writer.close();

        return new FileInputStream(manifestFile);
    }

    @Test
    public void testFailureIncorrectChecksum() throws Exception {
        List<ContentItem> items = createContentItems(count, contentIdPrefix);
        expect(generator.generate(spaceId, ManifestFormat.TSV)).andReturn(createManifestInputStream(items));
        ManifestTestHelper.setupManifestFile(md5Manifest, items.size(), incorrectChecksum, contentIdPrefix);
        replayAll();
        SpaceManifestDpnManifestVerifier verifier = setupVerifier();
        assertFalse(verifier.verify());
        assertTrue(!verifier.getErrors().isEmpty());
    }

    @Test
    public void testFailureSpaceManifestEntryMissing() throws Exception {
        List<ContentItem> items = createContentItems(count, contentIdPrefix);
        expect(generator.generate(spaceId, ManifestFormat.TSV)).andReturn(createManifestInputStream(items));
        ManifestTestHelper.setupManifestFile(md5Manifest, items.size() + 1, correctChecksum, contentIdPrefix);
        replayAll();
        SpaceManifestDpnManifestVerifier verifier = setupVerifier();

        assertFalse(verifier.verify());
        assertTrue(!verifier.getErrors().isEmpty());

    }

    /**
     * @return
     */
    private SpaceManifestDpnManifestVerifier setupVerifier() {
        SpaceManifestDpnManifestVerifier verifier =
            new SpaceManifestDpnManifestVerifier(md5Manifest, generator, spaceId);
        return verifier;
    }

}

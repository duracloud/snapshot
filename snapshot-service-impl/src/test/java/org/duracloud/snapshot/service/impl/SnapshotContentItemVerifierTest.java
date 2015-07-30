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
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.duracloud.client.ContentStore;
import org.duracloud.snapshot.db.model.SnapshotContentItem;
import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.easymock.Mock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;

/**
 * @author Daniel Bernstein
 *         Date: Jul 29, 2015
 */
@RunWith(EasyMockRunner.class)
public class SnapshotContentItemVerifierTest extends EasyMockSupport  {

    private SnapshotContentItemVerifier verifier;
    @Mock
    private StepExecution stepExecution;
    
    private String snapshotName = "snapshot-name";

    private File manifestFile;
    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
        this.manifestFile = File.createTempFile("manifest", "txt");
    }
    

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown() throws Exception {
        verifyAll();
    }

    @Test
    public void testSuccessfulRun() throws Exception {
        setupStepExecution();
        List<ManifestEntry> list = setupManifestFile();
        List<SnapshotContentItem> snapshotContentItems = setupSnapshotContentItems(list);
        replayAll();
        this.verifier = new SnapshotContentItemVerifier(manifestFile, snapshotName);
        simulateStepExecution(ExitStatus.COMPLETED, snapshotContentItems);
    }
    
    

    @Test
    public void testFailureMissingManifestItem() throws Exception {
        setupStepExecution();
        List<ManifestEntry> list = setupManifestFile();
        List<SnapshotContentItem> snapshotContentItems = setupSnapshotContentItems(list);
        snapshotContentItems.add(createSnapshotContentItem("missing-content", "checksum"));
        replayAll();
        this.verifier = new SnapshotContentItemVerifier(manifestFile, snapshotName);
        simulateStepExecution(ExitStatus.FAILED, snapshotContentItems);
    }

    @Test
    public void testFailureChecksumMismatch() throws Exception {
        setupStepExecution();
        List<ManifestEntry> list = setupManifestFile();
        List<SnapshotContentItem> snapshotContentItems = setupSnapshotContentItems(list);
        //replace the checksum of last item with bad checksum.
        snapshotContentItems.get(snapshotContentItems.size()-1).setMetadata(getMetadata("badchecksum"));
        replayAll();
        this.verifier = new SnapshotContentItemVerifier(manifestFile, snapshotName);
        simulateStepExecution(ExitStatus.FAILED, snapshotContentItems);
    }

    @Test
    public void testFailureMissingSnapshotItem() throws Exception {
        setupStepExecution();
        List<ManifestEntry> list = setupManifestFile();
        List<SnapshotContentItem> snapshotContentItems = setupSnapshotContentItems(list);
        //remove a snapshot item 
        snapshotContentItems.remove(0);
        replayAll();
        this.verifier = new SnapshotContentItemVerifier(manifestFile, snapshotName);
        simulateStepExecution(ExitStatus.FAILED, snapshotContentItems);
    }

    /**
     * @param list
     * @return
     */
    private List<SnapshotContentItem> setupSnapshotContentItems(List<ManifestEntry> list) {
        List<SnapshotContentItem> snapshotContentItems = new ArrayList<>();
        for(ManifestEntry e : list){
            String contentId = e.getContentId();
            String checksum = e.getChecksum();
            SnapshotContentItem c = createSnapshotContentItem(contentId, checksum);
            snapshotContentItems.add(c);
        }
        return snapshotContentItems;
    }


    /**
     * @param contentId
     * @param checksum
     * @return
     */
    private SnapshotContentItem createSnapshotContentItem(String contentId, String checksum) {
        SnapshotContentItem c = new SnapshotContentItem();
        c.setContentId(contentId);
        c.setMetadata(getMetadata(checksum));
        return c;
    }


    /**
     * @param checksum
     * @return
     */
    private String getMetadata(String checksum) {
        Map<String,String> props = new HashMap<>();
        props.put(ContentStore.CONTENT_CHECKSUM, checksum);
        return PropertiesSerializer.serialize(props);
    }


    /**
     * @param manifestFile
     * @return
     * @throws IOException
     */
    private List<ManifestEntry> setupManifestFile() throws IOException {
        return ManifestTestHelper.setupManifestFile(this.manifestFile);
    }
    
    
    private void setupStepExecution() {
        expect(stepExecution.getId()).andReturn(1000l).atLeastOnce();
        expect(stepExecution.getJobExecutionId()).andReturn(1001l).atLeastOnce();
    }
    
    /**
     * @param expectedStatus
     * @throws Exception
     */
    private void simulateStepExecution(ExitStatus expectedStatus, List<SnapshotContentItem> items) throws Exception {
        verifier.beforeStep(stepExecution);
        verifier.beforeWrite(items);
        verifier.write(items);
        verifier.afterWrite(items);
        assertEquals(expectedStatus, verifier.afterStep(stepExecution));
    }
}

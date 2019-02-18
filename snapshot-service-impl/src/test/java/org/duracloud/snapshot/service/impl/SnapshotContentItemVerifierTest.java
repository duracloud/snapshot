/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import static org.easymock.EasyMock.anyLong;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.isA;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.duracloud.client.ContentStore;
import org.duracloud.snapshot.db.model.Restoration;
import org.duracloud.snapshot.db.model.SnapshotContentItem;
import org.duracloud.snapshot.dto.RestoreStatus;
import org.duracloud.snapshot.service.RestoreManager;
import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.easymock.Mock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.item.ExecutionContext;

/**
 * @author Daniel Bernstein
 * Date: Jul 29, 2015
 */
@RunWith(EasyMockRunner.class)
public class SnapshotContentItemVerifierTest extends EasyMockSupport {

    private SnapshotContentItemVerifier verifier;
    @Mock
    private StepExecution stepExecution;

    @Mock
    private RestoreManager restoreManager;

    private String snapshotName = "snapshot-name";

    private File manifestFile;

    private String restoreId = "restore-id";

    private int itemCount = 100;

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
        createVerifier();
        simulateStepExecution(ExitStatus.COMPLETED, snapshotContentItems);
    }

    /**
     *
     */
    private void createVerifier() {
        this.verifier = new SnapshotContentItemVerifier(this.restoreId, manifestFile, snapshotName, restoreManager);
    }

    @Test
    public void testFailureMissingManifestItem() throws Exception {
        setupStepExecution(1, itemCount);
        setupStepExecutionFailure();
        List<ManifestEntry> list = setupManifestFile();
        List<SnapshotContentItem> snapshotContentItems = setupSnapshotContentItems(list);
        snapshotContentItems.add(createSnapshotContentItem("missing-content", "checksum"));
        replayAll();
        createVerifier();
        simulateStepExecution(ExitStatus.FAILED, snapshotContentItems);
    }

    private void setupStepExecutionFailure() {
        stepExecution.setTerminateOnly();
        expectLastCall();
        stepExecution.upgradeStatus(BatchStatus.FAILED);
        expectLastCall();
    }

    @Test
    public void testFailureChecksumMismatch() throws Exception {
        setupStepExecution(1, itemCount);
        setupStepExecutionFailure();

        List<ManifestEntry> list = setupManifestFile();
        List<SnapshotContentItem> snapshotContentItems = setupSnapshotContentItems(list);
        //replace the checksum of last item with bad checksum.
        snapshotContentItems.get(snapshotContentItems.size() - 1).setMetadata(getMetadata("badchecksum"));
        replayAll();
        createVerifier();
        simulateStepExecution(ExitStatus.FAILED, snapshotContentItems);
    }

    @Test
    public void testFailureMissingSnapshotItem() throws Exception {
        setupStepExecution(1, itemCount - 1);
        setupStepExecutionFailure();

        List<ManifestEntry> list = setupManifestFile();
        List<SnapshotContentItem> snapshotContentItems = setupSnapshotContentItems(list);
        //remove a snapshot item
        snapshotContentItems.remove(0);
        replayAll();
        createVerifier();
        simulateStepExecution(ExitStatus.FAILED, snapshotContentItems);
    }

    /**
     * @param list
     * @return
     */
    private List<SnapshotContentItem> setupSnapshotContentItems(List<ManifestEntry> list) {
        List<SnapshotContentItem> snapshotContentItems = new ArrayList<>();
        for (ManifestEntry e : list) {
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
        Map<String, String> props = new HashMap<>();
        props.put(ContentStore.CONTENT_CHECKSUM, checksum);
        return PropertiesSerializer.serialize(props);
    }

    /**
     * @param manifestFile
     * @return
     * @throws IOException
     */
    private List<ManifestEntry> setupManifestFile() throws IOException {
        return ManifestTestHelper.setupManifestFile(this.manifestFile, itemCount, "checksum", "contentid");
    }

    private void setupStepExecution() throws Exception {
        setupStepExecution(0, itemCount);
    }

    private void setupStepExecution(int errorCount, int itemCount) throws Exception {
        expect(stepExecution.getExitStatus()).andReturn(ExitStatus.COMPLETED);
        expect(stepExecution.getId()).andReturn(1000l).atLeastOnce();
        expect(stepExecution.getJobExecutionId()).andReturn(1001l).atLeastOnce();

        expect(restoreManager.transitionRestoreStatus(eq(restoreId),
                                                      eq(RestoreStatus.VERIFYING_SNAPSHOT_REPO_AGAINST_MANIFEST),
                                                      eq(""))).andReturn(EasyMock.createMock(Restoration.class));

        ExecutionContext context = createMock(ExecutionContext.class);
        expect(context.getLong(isA(String.class), anyLong())).andReturn((long) itemCount).anyTimes();

        context.putLong(isA(String.class), anyLong());
        expectLastCall().atLeastOnce();

        List<String> errors = new LinkedList<>();
        expect(context.get(eq(StepExecutionSupport.ERRORS_KEY))).andReturn(errors).atLeastOnce();
        context.put(eq(StepExecutionSupport.ERRORS_KEY), eq(new LinkedList<>()));
        expectLastCall();
        expect(stepExecution.getExecutionContext()).andReturn(context).atLeastOnce();

        if (errorCount > 0) {
            context.put(isA(String.class), eq(errors));
            expectLastCall().times(errorCount);
            context.put(eq(StepExecutionSupport.ERRORS_KEY), eq(new LinkedList<>()));
            expectLastCall();
        }
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
        assertEquals(expectedStatus.getExitCode(), verifier.afterStep(stepExecution).getExitCode());
    }
}

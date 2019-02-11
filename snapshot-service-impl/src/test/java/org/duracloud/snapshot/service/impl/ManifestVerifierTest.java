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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.duracloud.common.util.ChecksumUtil;
import org.duracloud.common.util.ChecksumUtil.Algorithm;
import org.duracloud.snapshot.db.model.Restoration;
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
 * @author Daniel Bernstein Date: Jul 29, 2015
 */
@RunWith(EasyMockRunner.class)
public class ManifestVerifierTest extends EasyMockSupport {

    private ManifestVerifier verifier;
    @Mock
    private StepExecution stepExecution;

    @Mock
    private RestoreManager restoreManager;

    private File manifestFile;

    private File restoreDir;

    private String restoreId = "restore-id";

    private int itemCount = 100;

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
        this.manifestFile = File.createTempFile("manifest", "txt");
        this.restoreDir = new File(FileUtils.getTempDirectory(), System.currentTimeMillis() + "");
        this.restoreDir.mkdirs();
        this.restoreDir.deleteOnExit();
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
        List<ManifestEntry> list = setupManifestFileAndContentDir();

        replayAll();
        createVerifier();
        simulateStepExecution(ExitStatus.COMPLETED, list);
    }

    /**
     *
     */
    private void createVerifier() {
        this.verifier = new ManifestVerifier(restoreId, restoreDir, restoreManager);
    }

    @Test
    public void testBadChecksum() throws Exception {
        setupStepExecution(1, itemCount);
        setupStepExecutionFailure();
        List<ManifestEntry> list = setupManifestFileAndContentDir();
        list.get(list.size() - 1).setChecksum("badChecksum");
        replayAll();
        createVerifier();
        simulateStepExecution(ExitStatus.FAILED, list);
    }

    @Test
    public void testContentInManifestButNotOnDisk() throws Exception {
        setupStepExecution(1, itemCount);
        setupStepExecutionFailure();

        List<ManifestEntry> list = setupManifestFileAndContentDir();
        String contentId = list.get(list.size() - 1).getContentId();
        File file = new File(restoreDir.getAbsolutePath() + File.separator + contentId);
        file.delete();
        replayAll();
        createVerifier();
        simulateStepExecution(ExitStatus.FAILED, list);
    }

    private void setupStepExecutionFailure() {
        stepExecution.setTerminateOnly();
        expectLastCall();
        stepExecution.upgradeStatus(BatchStatus.FAILED);
        expectLastCall();
    }

    /**
     * @return
     */
    private List<ManifestEntry> setupManifestFileAndContentDir() throws IOException {
        Writer writer = new BufferedWriter(new FileWriter(manifestFile));

        List<ManifestEntry> list = new ArrayList<>(itemCount);
        ChecksumUtil util = new ChecksumUtil(Algorithm.MD5);

        for (int i = 0; i < itemCount; i++) {
            String contentId = "contentid" + i;
            String content = "content-" + i;
            File contentFile = new File(this.restoreDir, contentId);
            FileWriter contentWriter = new FileWriter(contentFile);
            contentWriter.write(content);
            contentWriter.close();
            String checksum = util.generateChecksum(contentFile);
            ManifestEntry entry = new ManifestEntry(checksum, contentId);
            list.add(entry);
            ManifestFileHelper.writeManifestEntry(writer, entry.getContentId(), entry.getChecksum());
        }

        writer.close();
        return list;
    }

    private void setupStepExecution() throws Exception {
        setupStepExecution(0, itemCount);
    }

    private void setupStepExecution(int errorCount, int itemCount) throws Exception {
        expect(stepExecution.getExitStatus()).andReturn(ExitStatus.EXECUTING);
        expect(stepExecution.getId()).andReturn(1000l).atLeastOnce();
        expect(stepExecution.getJobExecutionId()).andReturn(1001l).atLeastOnce();

        expect(restoreManager.transitionRestoreStatus(eq(restoreId),
                                                      eq(RestoreStatus.VERIFYING_RETRIEVED_CONTENT),
                                                      eq(""))).andReturn(EasyMock.createMock(Restoration.class));

        ExecutionContext context = createMock(ExecutionContext.class);
        expect(context.getLong(isA(String.class), anyLong())).andReturn((long) itemCount).anyTimes();

        context.putLong(isA(String.class), anyLong());
        expectLastCall().atLeastOnce();

        List<String> errors = new LinkedList<>();
        expect(context.get(eq(StepExecutionSupport.ERRORS_KEY))).andReturn(errors).atLeastOnce();
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
    private void simulateStepExecution(ExitStatus expectedStatus, List<ManifestEntry> items) throws Exception {
        verifier.beforeStep(stepExecution);
        verifier.beforeWrite(items);
        verifier.write(items);
        verifier.afterWrite(items);
        assertEquals(expectedStatus.getExitCode(), verifier.afterStep(stepExecution).getExitCode());
    }

}

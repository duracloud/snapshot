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
import static org.easymock.EasyMock.isA;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

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
public class SpaceVerifierTest extends EasyMockSupport {

    private SpaceVerifier verifier;
    private String correctChecksum = "correct-checksum";
    private String spaceId = "spaceId";
    private String contentId = "contentId";
    @Mock
    private StepExecution stepExecution;

    @Mock
    private RestoreManager restoreManager;

    @Mock
    private SpaceManifestSnapshotManifestVerifier spaceManifestVerifier;

    private String restoreId = "restore-id";

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
    }

    /**
     *
     */
    private void setupTestSubject() {
        this.verifier = new SpaceVerifier(restoreId, spaceManifestVerifier, spaceId, restoreManager);
        this.verifier.setIsTest();
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
        expect(this.spaceManifestVerifier.verify()).andReturn(true);
        expect(this.spaceManifestVerifier.getSpaceId()).andReturn(spaceId);

        replayAll();
        setupTestSubject();
        simulateStepExecution(ExitStatus.COMPLETED);
    }

    @Test
    public void testFailedRun() throws Exception {
        setupStepExecution(2);
        setupStepExecutionFailure();
        expect(this.spaceManifestVerifier.verify()).andReturn(false);
        expect(this.spaceManifestVerifier.getSpaceId()).andReturn(spaceId);
        expect(this.spaceManifestVerifier.getErrors()).andReturn(Arrays.asList("error"));
        replayAll();
        setupTestSubject();
        simulateStepExecution(ExitStatus.FAILED);
    }

    /**
     * @param expectedStatus
     * @throws Exception
     */
    private void simulateStepExecution(ExitStatus expectedStatus) throws Exception {
        List<ManifestEntry> items = Arrays.asList(new ManifestEntry(correctChecksum, contentId));
        verifier.beforeStep(stepExecution);
        verifier.beforeWrite(items);
        try {
            verifier.write(items);
        } catch (Exception ex) {
            verifier.onWriteError(ex, items);
        }
        verifier.afterWrite(items);
        assertEquals(expectedStatus.getExitCode(), verifier.afterStep(stepExecution).getExitCode());
    }

    private void setupStepExecution() throws Exception {
        setupStepExecution(0);
    }

    private void setupStepExecution(int errorCount) throws Exception {
        ExecutionContext context = createMock(ExecutionContext.class);
        List<String> errors = new LinkedList<>();
        expect(context.get(eq(StepExecutionSupport.ERRORS_KEY))).andReturn(errors).atLeastOnce();
        context.put(eq(StepExecutionSupport.ERRORS_KEY), eq(new LinkedList<>()));
        expectLastCall();
        expect(stepExecution.getExecutionContext()).andReturn(context).atLeastOnce();

        if (errorCount > 0) {
            context.put(isA(String.class), eq(errors));
            expectLastCall().times(errorCount);
        }

        expect(stepExecution.getExitStatus()).andReturn(ExitStatus.EXECUTING);
        expect(stepExecution.getId()).andReturn(1000l).atLeastOnce();
        expect(stepExecution.getJobExecutionId()).andReturn(1001l).atLeastOnce();

        expect(restoreManager.transitionRestoreStatus(eq(restoreId),
                                                      eq(RestoreStatus.VERIFYING_TRANSFERRED_CONTENT),
                                                      eq(""))).andReturn(EasyMock.createMock(Restoration.class));

    }

    private void setupStepExecutionFailure() {
        stepExecution.setTerminateOnly();
        expectLastCall();
        stepExecution.upgradeStatus(BatchStatus.FAILED);
        expectLastCall();
    }
}

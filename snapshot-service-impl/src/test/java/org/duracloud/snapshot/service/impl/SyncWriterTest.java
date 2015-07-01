/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.duracloud.client.ContentStore;
import org.duracloud.domain.Space;
import org.duracloud.error.NotFoundException;
import org.duracloud.snapshot.common.test.SnapshotTestBase;
import org.duracloud.snapshot.db.model.Restoration;
import org.duracloud.snapshot.dto.RestoreStatus;
import org.duracloud.snapshot.service.InvalidStateTransitionException;
import org.duracloud.snapshot.service.RestorationNotFoundException;
import org.duracloud.snapshot.service.RestoreManager;
import org.duracloud.sync.endpoint.MonitoredFile;
import org.duracloud.sync.endpoint.SyncEndpoint;
import org.duracloud.sync.endpoint.SyncResultType;
import org.easymock.EasyMock;
import org.easymock.Mock;
import org.easymock.TestSubject;
import org.junit.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;

/**
 * @author Daniel Bernstein
 *         Date: Jul 17, 2014
 */
public class SyncWriterTest extends SnapshotTestBase{

    @TestSubject 
    private SyncWriter writer;
    
    @Mock 
    private SyncEndpoint endpoint;
    
    @Mock 
    private ContentStore contentStore;
    
    @Mock
    private StepExecution stepExecution;
    
    private File watchDir;
    
    @Mock
    private RestoreManager restoreManager;


    @Mock
    private Restoration restoration;
    
    private String restorationId = "restoration-id";
    

    /* (non-Javadoc)
     * @see org.duracloud.snapshot.common.test.SnapshotTestBase#setup()
     */
    @Override
    public void setup() {
        super.setup();
        watchDir = new File(System.getProperty("java.io.tmpdir") + File.separator + System.currentTimeMillis());
        watchDir.mkdirs();
        watchDir.deleteOnExit();
        writer = new SyncWriter(restorationId, watchDir, endpoint, contentStore, "spaceId", restoreManager); 
    }
    
    /* (non-Javadoc)
     * @see org.duracloud.snapshot.common.test.SnapshotTestBase#teartDown()
     */
    @Override
    public void tearDown() {
        super.tearDown();
        for(File file: watchDir.listFiles()) {
            file.delete();
        }
        
        watchDir.delete();
    }


    @Test
    public void testWrite() throws Exception{
        List<File> files = new ArrayList<>();
        int count = 3;
        for(int i = 0; i < count; i++){
            File file = new File(watchDir, "");
            files.add(file);
        }

        
        EasyMock.expect(endpoint.syncFileAndReturnDetailedResult(EasyMock.isA(MonitoredFile.class),
                                                                 EasyMock.isA(File.class)))
                .andReturn(SyncResultType.ADDED)
                .times(count);
        replayAll();
        
        this.writer.write(files);
    }
    
    @Test
    public void testBeforeStep() throws Exception {
        EasyMock.expect(contentStore.getSpace(EasyMock.isA(String.class),
                                              EasyMock.isNull(String.class),
                                              EasyMock.anyInt(),
                                              EasyMock.isNull(String.class)))
                .andThrow(new NotFoundException("not found"));
        
        contentStore.createSpace(EasyMock.isA(String.class));
        EasyMock.expectLastCall();

        setupBeforeTransition();
        replayAll();
        
        this.writer.beforeStep(stepExecution);
        
    }
    
    
    @Test
    public void testAfterStep() throws Exception {
        
        EasyMock.expect(this.restoreManager.transitionRestoreStatus(EasyMock.eq(restorationId),
                                                                    EasyMock.eq(RestoreStatus.TRANSFER_TO_DURACLOUD_COMPLETE),
                                                                    EasyMock.isA(String.class)))
                .andReturn(restoration);
        
        EasyMock.expect(stepExecution.getExitStatus()).andReturn(ExitStatus.COMPLETED);
        replayAll();
        
        this.writer.afterStep(stepExecution);
        
    }

    /**
     * @throws InvalidStateTransitionException
     * @throws RestorationNotFoundException
     */
    private void setupBeforeTransition()
        throws InvalidStateTransitionException,
            RestorationNotFoundException {
        EasyMock.expect(this.restoreManager.transitionRestoreStatus(EasyMock.eq(restorationId),
                                                                    EasyMock.eq(RestoreStatus.TRANSFERRING_TO_DURACLOUD),
                                                                    EasyMock.isA(String.class)))
                .andReturn(restoration);
    }
    
    @Test
    public void testBeforeStepSpaceAlreadyExistsEmpty() throws Exception {
        setupBeforeTransition();

        Space space = new Space();
        space.setContentIds(new ArrayList<String>());

        EasyMock.expect(contentStore.getSpace(EasyMock.isA(String.class),
                                              EasyMock.isNull(String.class),
                                              EasyMock.anyInt(),
                                              EasyMock.isNull(String.class)))
                .andReturn(space);
        
        
        replayAll();
        
        this.writer.beforeStep(stepExecution);
        
    }

    @Test
    public void testBeforeStepSpaceAlreadyExistsNotEmpty() throws Exception {
        setupBeforeTransition();

        Space space = new Space();
        space.setContentIds(new ArrayList<String>(Arrays.asList(new String[]{"test"})));
        
        EasyMock.expect(contentStore.getSpace(EasyMock.isA(String.class),
                                              EasyMock.isNull(String.class),
                                              EasyMock.anyInt(),
                                              EasyMock.isNull(String.class)))
                .andReturn(space);
        
        stepExecution.addFailureException(EasyMock.isA(Throwable.class));
        
        replayAll();
        
        
        this.writer.beforeStep(stepExecution);
        
    }

}

/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.manager.spring.batch;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.duracloud.client.ContentStore;
import org.duracloud.domain.Space;
import org.duracloud.error.NotFoundException;
import org.duracloud.snapshot.common.test.SnapshotTestBase;
import org.duracloud.sync.endpoint.MonitoredFile;
import org.duracloud.sync.endpoint.SyncEndpoint;
import org.duracloud.sync.endpoint.SyncResultType;
import org.easymock.EasyMock;
import org.easymock.Mock;
import org.easymock.TestSubject;
import org.junit.Test;
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
    

    /* (non-Javadoc)
     * @see org.duracloud.snapshot.common.test.SnapshotTestBase#setup()
     */
    @Override
    public void setup() {
        super.setup();
        watchDir = new File(System.getProperty("java.io.tmpdir") + File.separator + System.currentTimeMillis());
        watchDir.mkdirs();
        watchDir.deleteOnExit();
        writer = new SyncWriter(watchDir, endpoint, contentStore, "spaceId"); 
    }
    
    /* (non-Javadoc)
     * @see org.duracloud.snapshot.common.test.SnapshotTestBase#teartDown()
     */
    @Override
    public void teartDown() {
        super.teartDown();
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
        
        replayAll();
        
        this.writer.beforeStep(stepExecution);
        
    }
    
    @Test
    public void testBeforeStepSpaceAlreadyExistsEmpty() throws Exception {
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

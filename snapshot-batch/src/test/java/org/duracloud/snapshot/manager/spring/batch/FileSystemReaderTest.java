/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.manager.spring.batch;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.duracloud.snapshot.common.test.SnapshotTestBase;
import org.duracloud.snapshot.manager.spring.batch.FileSystemReader;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Daniel Bernstein
 *         Date: Jul 16, 2014
 */
public class FileSystemReaderTest extends SnapshotTestBase {

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
        super.setup();
    }


    /**
     * Test method for {@link org.duracloud.snapshot.manager.spring.batch.FileSystemReader#read()}.
     * @throws IOException 
     */
    @Test
    public void testRead() throws IOException {
        File rootDirectory = new File(System.getProperty("java.io.tmpdir")+File.separator + "FileSystemReaderTest"+ System.currentTimeMillis());
        rootDirectory.mkdirs();
        rootDirectory.deleteOnExit();
        
        Set<File> files = new HashSet<>();
        Set<File> results = new HashSet<>();

        for(int i = 0; i < 10; i++){
            File f = File.createTempFile("test-"+i, ".txt", rootDirectory);
            f.createNewFile();
            f.deleteOnExit();
            files.add(f);
        }
        
        FileSystemReader reader = new FileSystemReader(rootDirectory);
        while(true){
            try {
                File file = reader.read();
                if(file == null){
                    break;
                }
                results.add(file);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Assert.assertEquals(files.size(), results.size());
        Assert.assertTrue(files.containsAll(results));
        
    }

}

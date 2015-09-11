/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.duracloud.common.model.ContentItem;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * This test was added because of a bug which caused the sha256 checksums
 * that were added to the sha256 manifest file were incorrect values. This
 * was caused by the fact that the ChecksumUtil in the SpaceItemWriter was
 * being used my multiple threads at the same time. This test runs multiple
 * threads at once and checks to see if the checksum values come out correct.
 *
 * @author Bill Branan
 *         Date: 10/23/14
 */
public class SpaceItemWriterThreadTest {

    private File localFile;
    private String emptyFileSha = "e3b0c44298fc1c149afbf4c8996fb92427ae" +
                                  "41e4649b934ca495991b7852b855";

    @Before
    public void setup() throws Exception {
        localFile = File.createTempFile("space-item-writer", "test");
        localFile.deleteOnExit();
    }

    @After
    public void teardown() throws Exception {
        try {
            localFile.delete();
        } catch(Exception e) {
        }
    }

    @Test
    public void testWriteManifestThreads() throws Exception {
        StringWriter stringWriter = new StringWriter(100);
        BufferedWriter sha256Writer = new BufferedWriter(stringWriter);
        SpaceItemWriter itemWriter =
            new SpaceItemWriter(null, null, null, null, null, null, sha256Writer, null, null);

        ExecutorService execService = Executors.newFixedThreadPool(20);

        for(int i=0; i<100; i++) {
            ContentItem item = new ContentItem("spaceId", "contentId" + i);
            WriteHandler handler = new WriteHandler(item, itemWriter);
            execService.execute(handler);
        }

        while(stringWriter.toString().equals("")) {
            Thread.sleep(1000);
        }

        String manifest = stringWriter.toString();
        BufferedReader manifestReader =
            new BufferedReader(new StringReader(manifest));
        String line = manifestReader.readLine();
        do {
            String checksum = line.split(" ")[0];
            assertEquals(emptyFileSha, checksum);
            line = manifestReader.readLine();
        }
        while(line != null);
    }

    private class WriteHandler implements Runnable {

        ContentItem contentItem;
        SpaceItemWriter itemWriter;

        public WriteHandler(ContentItem contentItem,
                            SpaceItemWriter itemWriter) {
            this.contentItem = contentItem;
            this.itemWriter = itemWriter;
        }

        public void run() {
            try {
                itemWriter.writeSHA256Checksum(contentItem, localFile);
            } catch(Exception e) {
                fail("Exception writing checksum: " + e.getMessage());
            }
        }
    }

}

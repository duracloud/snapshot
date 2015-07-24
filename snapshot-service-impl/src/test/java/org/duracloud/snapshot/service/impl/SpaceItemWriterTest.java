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
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.FileUtils;
import org.duracloud.common.model.ContentItem;
import org.duracloud.common.util.ChecksumUtil;
import org.duracloud.common.util.ChecksumUtil.Algorithm;
import org.duracloud.common.util.DateUtil;
import org.duracloud.common.util.DateUtil.DateFormat;
import org.duracloud.retrieval.mgmt.CSVFileOutputWriter;
import org.duracloud.retrieval.mgmt.OutputWriter;
import org.duracloud.retrieval.source.ContentStream;
import org.duracloud.retrieval.source.RetrievalSource;
import org.duracloud.snapshot.SnapshotException;
import org.duracloud.snapshot.common.test.SnapshotTestBase;
import org.duracloud.snapshot.db.ContentDirUtils;
import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.service.SnapshotManager;
import org.duracloud.storage.provider.StorageProvider;
import org.easymock.Mock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;

/**
 * @author Daniel Bernstein 
 *         Date: Oct 23, 2014
 */
public class SpaceItemWriterTest extends SnapshotTestBase {
    
    private static final String SHA256_MANIFEST_TXT_FILE_NAME = "sha256-manifest.txt";

    private static final String MD5_MANIFEST_TXT_FILE_NAME = "md5-manifest.txt";

    private static Logger log = LoggerFactory.getLogger(SpaceItemWriterTest.class);
    
    private SpaceItemWriter writer;

    @Mock
    private Snapshot snapshot;

    @Mock
    private RetrievalSource retrievalSource;

    @Mock
    private SnapshotManager snapshotManager;

    @Mock
    private StepExecution stepExecution;

 
    private OutputWriter outputWriter;
    private BufferedWriter propsWriter;
    private BufferedWriter md5Writer;
    private BufferedWriter sha256Writer;

    private File contentDir;
    private File workDir;

    /*
     * (non-Javadoc)
     * 
     * @see org.duracloud.snapshot.common.test.SnapshotTestBase#setup()
     */
    @Before
    @Override
    public void setup() {
        super.setup();
        contentDir = createDirectory("content" + System.currentTimeMillis());
        workDir = createDirectory("work" + System.currentTimeMillis());

    }

    /*
     * (non-Javadoc)
     * 
     * @see org.duracloud.snapshot.common.test.SnapshotTestBase#teartDown()
     */
    @After
    @Override
    public void tearDown() {
        super.tearDown();
        try {
            FileUtils.deleteDirectory(contentDir);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            FileUtils.deleteDirectory(workDir);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Test
    public void testSingleThreaded() throws IOException, SnapshotException {
        test(1);
    }

    @Test
    public void testMultiThreaded() throws IOException, SnapshotException {
        test(10);
    }

    
    private void test(int threads) throws IOException, SnapshotException {
        outputWriter = new CSVFileOutputWriter(workDir);
        BufferedWriter propsWriter =
            createWriter(contentDir, "properties.json");
        BufferedWriter md5Writer = createWriter(contentDir, MD5_MANIFEST_TXT_FILE_NAME);
        BufferedWriter sha256Writer =
            createWriter(contentDir, SHA256_MANIFEST_TXT_FILE_NAME);

        String spaceId = "space-id";
        String contentId = "content-id";
        List<ContentItem> items = new ArrayList<>();
        
        List<File> sourceFiles = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            sourceFiles.add(setupContentItem(items, spaceId, contentId + i));
        }

        Collections.sort(sourceFiles, new Comparator<File>(){
            /* (non-Javadoc)
             * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
             */
            @Override
            public int compare(File o1, File o2) {
                 return o1.getName().compareTo(o2.getName());
            }
        });

        
        Collections.sort(items, new Comparator<ContentItem>(){
            /* (non-Javadoc)
             * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
             */
            @Override
            public int compare(ContentItem o1, ContentItem o2) {
                 return o1.getContentId().compareTo(o2.getContentId());
            }
        });

        expect(stepExecution.getExitStatus()).andReturn(ExitStatus.COMPLETED)
                                             .times(2);
        replayAll();
        writer =
            new SpaceItemWriter(snapshot,
                                retrievalSource,
                                contentDir,
                                outputWriter,
                                propsWriter,
                                md5Writer,
                                sha256Writer,
                                snapshotManager);

        writer.beforeStep(stepExecution);
        writeItems(items, threads);
        writer.afterStep(stepExecution);

        List<String> md5Lines = getLines(MD5_MANIFEST_TXT_FILE_NAME);
        
        for (int i = 0; i < sourceFiles.size(); i++) {
            File file = sourceFiles.get(i);
            ContentItem content = items.get(i);
            ChecksumUtil md5 = new ChecksumUtil(Algorithm.MD5);
            String md5Checksum = md5.generateChecksum(file);
            String md5Line = md5Lines.get(i);
            String contentId2 = content.getContentId();
            log.debug("md5 line: \"{}\", md5Checksum={}, filename={}, contentId={}",
                      md5Line,
                      md5Checksum,
                      file.getName(),
                      contentId2);
            assertTrue(md5Line.contains(contentId2));
            assertTrue(md5Line.contains(md5Checksum));
        }

        List<String> shaLines = getLines(SHA256_MANIFEST_TXT_FILE_NAME);

        for (int i = 0; i < sourceFiles.size(); i++) {
            File file = sourceFiles.get(i);
            ContentItem content = items.get(i);
            ChecksumUtil sha = new ChecksumUtil(Algorithm.SHA_256);
            String shaChecksum = sha.generateChecksum(file);
            String shaLine = shaLines.get(i);
            String contentId3 = content.getContentId();
            log.debug("sha256 line: \"{}\", md5Checksum={}, filename={}, contentId={}",
                      shaLine,
                      shaChecksum,
                      file.getName(),
                      contentId3);

            assertTrue(shaLine.endsWith(contentId3));
            assertTrue(shaLine.contains(shaChecksum));
        }

    }

    /**
     * @param string
     * @return
     * @throws IOException 
     */
    private List<String> getLines(String filename) throws IOException {
        List<String> lines = Files.readAllLines(ContentDirUtils.getPath(contentDir, filename), StandardCharsets.UTF_8);
        Collections.sort(lines, new Comparator<String>() {
            /* (non-Javadoc)
             * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
             */
            @Override
            public int compare(String o1, String o2) {
                String path1 = getPath(o1);
                String path2 = getPath(o2);

                return path1.compareTo(path2);
            }

            private String getPath(String string) {
                int index = string.indexOf(" ");
                return string.substring(index+1);
            }
        });
        return lines;
    }

    /**
     * Distributes the items into sublists and runs them in separate threads.
     * @param items
     * @param threads
     * @throws IOException
     */
    private void writeItems(List<ContentItem> items, int threads) throws IOException {
        int itemCount = items.size();
        final CountDownLatch countdownLatch = new CountDownLatch(threads);

        int bottomIndex = 0;
        
        int itemsPerThread = itemCount/threads;
        
        int remainder = itemCount % threads; 
        int thread = 0;
        final AtomicInteger processed = new AtomicInteger(0);

        while(bottomIndex < itemCount){
            thread++;
            final int fromIndex = bottomIndex;
            
            int topIndex = fromIndex + itemsPerThread;
            
            if(thread == threads){
                topIndex += remainder;
            }
            
            final int toIndex = topIndex;
            final List<ContentItem> contents = items.subList(fromIndex, toIndex);
            new Thread(new Runnable(){
                /* (non-Javadoc)
                 * @see java.lang.Runnable#run()
                 */
                @Override
                public void run() {
                    try {
                        writer.beforeWrite(contents);
                        writer.write(contents);
                        writer.afterWrite(contents);
                        processed.addAndGet(contents.size());
                        countdownLatch.countDown();
                    } catch (IOException e) {
                       throw new RuntimeException(e);
                    }

                }
            }).start();
            
            bottomIndex = topIndex;
        }

        
        try {
            assertTrue(countdownLatch.await(20, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        assertEquals(items.size(), processed.get());
    }

    /**
     * @param spaceId
     * @param string
     * @throws SnapshotException
     */
    private File setupContentItem(List<ContentItem> items,
                                  String spaceId,
                                  String contentId)
        throws IOException,
            SnapshotException {
        int size = 1024 * 100;
        File content = createUniqueTempFile(size, contentId);
        ChecksumUtil util = new ChecksumUtil(Algorithm.MD5);
        assertEquals(size, content.length());
        String md5 = util.generateChecksum(content);
        InputStream is = new FileInputStream(content);
        Map<String, String> map = new HashMap<>();
        map.put(StorageProvider.PROPERTIES_CONTENT_CHECKSUM, md5);

        String date =
            DateUtil.convertToString(System.currentTimeMillis(),
                                     DateFormat.LONG_FORMAT);
        log.debug("date=" + date);
        map.put(StorageProvider.PROPERTIES_CONTENT_FILE_CREATED, date);
        map.put(StorageProvider.PROPERTIES_CONTENT_FILE_LAST_ACCESSED, date);
        map.put(StorageProvider.PROPERTIES_CONTENT_FILE_MODIFIED, date);
        ContentStream contentStream = new ContentStream(is, map);
        ContentItem item = new ContentItem(spaceId, contentId);
        expect(retrievalSource.getSourceContent(item)).andReturn(contentStream);

        items.add(item);
        this.snapshotManager.addContentItem(eq(snapshot),
                                            eq(contentId),
                                            eq(map));
        return content;
    }

    private File createUniqueTempFile(int size, String filename) throws IOException {
        
        File file = File.createTempFile(filename + "-", ".txt");
        FileOutputStream os = new FileOutputStream(file);
        int block = 10 * 1024;
        int remainder = size % block;
        int blocks = size / block;
        byte[] data = new byte[block];
        
        byte[] uniqueBytes = filename.getBytes();
        for(int i = 0; i < uniqueBytes.length; i++){
            data[i] = uniqueBytes[i];
        }
        
        for (int i = 0; i < blocks; i++) {
            os.write(data);
        }

        if (remainder > 0) {
            os.write(new byte[remainder]);
        }
        os.close();
        file.deleteOnExit();
        return file;
    }

    private BufferedWriter createWriter(File contentDir, String name)
        throws IOException {
        Path propsPath = ContentDirUtils.getPath(contentDir, name);
        BufferedWriter propsWriter =
            Files.newBufferedWriter(propsPath, StandardCharsets.UTF_8);
        return propsWriter;
    }

    /**
     * @param name
     * @return
     */
    private File createDirectory(String name) {
        File dir = new File(getTempDir(), name);
        assertTrue(dir.mkdirs());
        return dir;
    }

}

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
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.duracloud.common.constant.Constants;
import org.duracloud.common.model.ContentItem;
import org.duracloud.common.util.ChecksumUtil;
import org.duracloud.common.util.ChecksumUtil.Algorithm;
import org.duracloud.common.util.DateUtil;
import org.duracloud.common.util.DateUtil.DateFormat;
import org.duracloud.retrieval.mgmt.CSVFileOutputWriter;
import org.duracloud.retrieval.mgmt.OutputWriter;
import org.duracloud.retrieval.mgmt.RetrievalListener;
import org.duracloud.retrieval.source.ContentStream;
import org.duracloud.retrieval.source.RetrievalSource;
import org.duracloud.snapshot.SnapshotException;
import org.duracloud.snapshot.common.test.SnapshotTestBase;
import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.service.SnapshotManager;
import org.duracloud.storage.provider.StorageProvider;
import org.easymock.Mock;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;

/**
 * @author Daniel Bernstein
 * Date: Oct 23, 2014
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

    private File contentDir;
    private File workDir;

    private String snapshotName = "snapshot-name";

    private File propsFile;
    private File md5File;
    private File sha256File;

    private String spaceId = "space-id";
    private String contentId = "content-id";

    private String propertyFilePath = "C:\\test\\file.txt";

    /*
     * (non-Javadoc)
     *
     * @see org.duracloud.snapshot.common.test.SnapshotTestBase#setup()
     */
    @Before
    @Override
    public void setup() throws Exception {
        super.setup();
        contentDir = createDirectory("content" + System.currentTimeMillis());
        workDir = createDirectory("work" + System.currentTimeMillis());
        propsFile = new File(contentDir, "properties.json");
        md5File = new File(contentDir, MD5_MANIFEST_TXT_FILE_NAME);
        sha256File = new File(contentDir, SHA256_MANIFEST_TXT_FILE_NAME);

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
            contentDir.deleteOnExit();
            FileUtils.deleteDirectory(contentDir);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            workDir.deleteOnExit();
            FileUtils.deleteDirectory(workDir);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Test
    public void testSingleThreaded() throws Exception {
        test(1);
    }

    @Test
    public void testMultiThreaded() throws Exception {
        test(10);
    }

    @Test
    public void testManifestVerificationFailure() throws Exception {
        test(10, false);
    }

    private void test(int threads) throws Exception {
        test(threads, true);
    }

    private void test(int threads, boolean manifestVerificationSuccessful) throws Exception {
        outputWriter = new CSVFileOutputWriter(workDir);

        List<ContentItem> items = new ArrayList<>();

        List<File> sourceFiles = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            sourceFiles.add(setupContentItem(items, spaceId, contentId + String.format("%05d", i), 1));
        }

        setupContentItem(items, spaceId, Constants.SNAPSHOT_PROPS_FILENAME, 1);

        sortSourceFilesAndItems(items, sourceFiles);

        expect(stepExecution.getExitStatus()).andReturn(ExitStatus.COMPLETED)
                                             .times(2);

        expect(snapshot.getName()).andReturn(snapshotName).times(2);

        SpaceManifestSnapshotManifestVerifier spaceManifestVerifier = createMock(
            SpaceManifestSnapshotManifestVerifier.class);

        expect(spaceManifestVerifier.verify()).andReturn(manifestVerificationSuccessful);
        expect(spaceManifestVerifier.getSpaceId()).andReturn(spaceId);

        if (!manifestVerificationSuccessful) {
            expect(stepExecution.getId()).andReturn(1l);
            expect(stepExecution.getJobExecutionId()).andReturn(1l);
            expect(spaceManifestVerifier.getErrors()).andReturn(Arrays.asList("error"));
            stepExecution.upgradeStatus(BatchStatus.FAILED);
            expectLastCall();
        }
        replayAll();
        writer =
            new SpaceItemWriter(snapshot,
                                retrievalSource,
                                contentDir,
                                outputWriter,
                                propsFile,
                                md5File,
                                sha256File,
                                snapshotManager,
                                spaceManifestVerifier);
        writer.closeDatabase();
        writer.deleteDatabase();
        writer.setIsTest();
        writer.beforeStep(stepExecution);
        writeItems(items, threads);
        ExitStatus status = writer.afterStep(stepExecution);

        //verifyMd5Manifest(items, sourceFiles);
        verifySha256Manifest(items, sourceFiles);

        verifyPropsFile(propsFile);

        if (manifestVerificationSuccessful) {
            assertEquals(ExitStatus.COMPLETED.getExitCode(), status.getExitCode());
        } else {
            assertEquals(ExitStatus.FAILED.getExitCode(), status.getExitCode());
        }
    }

    private void sortSourceFilesAndItems(List<ContentItem> items, List<File> sourceFiles) {
        Collections.sort(sourceFiles, new Comparator<File>() {
            /* (non-Javadoc)
             * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
             */
            @Override
            public int compare(File o1, File o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });

        Collections.sort(items, new Comparator<ContentItem>() {
            /* (non-Javadoc)
             * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
             */
            @Override
            public int compare(ContentItem o1, ContentItem o2) {
                if (o1.getContentId().equals(Constants.SNAPSHOT_PROPS_FILENAME)) {
                    return 1;
                }
                return o1.getContentId().compareTo(o2.getContentId());
            }
        });
    }

    @Test
    public void testRestartDuringTransfer() throws Exception {
        outputWriter = new CSVFileOutputWriter(workDir);
        List<ContentItem> items = new ArrayList<>();

        List<File> sourceFiles = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            sourceFiles.add(setupContentItem(items, spaceId, contentId + String.format("%05d", i), 2));
        }

        setupContentItem(items, spaceId, Constants.SNAPSHOT_PROPS_FILENAME, 1);

        sortSourceFilesAndItems(items, sourceFiles);

        expect(stepExecution.getExitStatus()).andReturn(ExitStatus.COMPLETED)
                                             .times(2);

        expect(snapshot.getName()).andReturn(snapshotName).times(3);

        SpaceManifestSnapshotManifestVerifier spaceManifestVerifier =
            createMock(SpaceManifestSnapshotManifestVerifier.class);

        expect(spaceManifestVerifier.verify()).andReturn(true);
        expect(spaceManifestVerifier.getSpaceId()).andReturn(spaceId);

        replayAll();
        writer =
            new SpaceItemWriter(snapshot,
                                retrievalSource,
                                contentDir,
                                outputWriter,
                                propsFile,
                                md5File,
                                sha256File,
                                snapshotManager,
                                spaceManifestVerifier);
        writer.setIsTest();
        writer.beforeStep(stepExecution);
        writeItems(items, 1);

        assertEquals("total checksum performed should be one less than the number of content items",
                     items.size() - 1, writer.getTotalChecksumsPerformed());
        //close the database using protected method
        //in order to release exclusive file lock
        //by the mapdb instance.
        writer.closeDatabase();

        //use a new writer simulating a step restart.
        writer =
            new SpaceItemWriter(snapshot,
                                retrievalSource,
                                contentDir,
                                outputWriter,
                                propsFile,
                                md5File,
                                sha256File,
                                snapshotManager,
                                spaceManifestVerifier);

        //simulate restart
        writer.beforeStep(stepExecution);
        writeItems(items, 1);

        ExitStatus status = writer.afterStep(stepExecution);

        assertEquals("total checksums should be 0",
                     0, writer.getTotalChecksumsPerformed());

        verifyMd5Manifest(items, sourceFiles);

        verifySha256Manifest(items, sourceFiles);

        assertEquals(ExitStatus.COMPLETED.getExitCode(), status.getExitCode());
    }

    @Test
    public void testRestartWithEmptyCacheButNonEmptyManifestFiles() throws Exception {
        outputWriter = new CSVFileOutputWriter(workDir);
        List<ContentItem> items = new ArrayList<>();

        List<File> sourceFiles = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            sourceFiles.add(setupContentItem(items, spaceId, contentId + String.format("%05d", i), 2));
        }

        for (ContentItem item : items) {
            expect(this.retrievalSource.getSourceProperties(item))
                .andReturn(createContentProperties("md5"));
        }

        setupContentItem(items, spaceId, Constants.SNAPSHOT_PROPS_FILENAME, 1);

        sortSourceFilesAndItems(items, sourceFiles);

        expect(stepExecution.getExitStatus()).andReturn(ExitStatus.COMPLETED)
                                             .times(2);

        expect(snapshot.getName()).andReturn(snapshotName).times(3);

        SpaceManifestSnapshotManifestVerifier spaceManifestVerifier = createMock(
            SpaceManifestSnapshotManifestVerifier.class);

        expect(spaceManifestVerifier.verify()).andReturn(true);
        expect(spaceManifestVerifier.getSpaceId()).andReturn(spaceId);

        replayAll();
        writer =
            new SpaceItemWriter(snapshot,
                                retrievalSource,
                                contentDir,
                                outputWriter,
                                propsFile,
                                md5File,
                                sha256File,
                                snapshotManager,
                                spaceManifestVerifier);
        writer.setIsTest();
        writer.beforeStep(stepExecution);
        writeItems(items, 1);
        assertEquals("total checksum performed should be one less than the number of content items",
                     items.size() - 1, writer.getTotalChecksumsPerformed());

        //reset the database to ensure that cache is empty
        writer.closeDatabase();
        writer.deleteDatabase();

        //use a new writer simulating a step restart.
        writer =
            new SpaceItemWriter(snapshot,
                                retrievalSource,
                                contentDir,
                                outputWriter,
                                propsFile,
                                md5File,
                                sha256File,
                                snapshotManager,
                                spaceManifestVerifier);

        //simulate restart
        writer.beforeStep(stepExecution);
        writeItems(items, 1);

        ExitStatus status = writer.afterStep(stepExecution);

        assertEquals("total checksums performed should be 0",
                     0, writer.getTotalChecksumsPerformed());

        verifyMd5Manifest(items, sourceFiles);
        verifySha256Manifest(items, sourceFiles);

        assertEquals(ExitStatus.COMPLETED.getExitCode(), status.getExitCode());
    }

    private void verifyMd5Manifest(List<ContentItem> items, List<File> sourceFiles) throws IOException {
        List<String> md5Lines = getLines(MD5_MANIFEST_TXT_FILE_NAME);

        for (int i = 0; i < sourceFiles.size(); i++) {
            File file = sourceFiles.get(i);
            ContentItem content = items.get(i);
            ChecksumUtil md5 = new ChecksumUtil(Algorithm.MD5);
            String md5Checksum = md5.generateChecksum(file);
            String md5Line = md5Lines.get(i);
            String contentId2 = content.getContentId();
            if (contentId2.equals(Constants.SNAPSHOT_PROPS_FILENAME)) {
                continue;
            }
            log.debug("md5 line: \"{}\", md5Checksum={}, filename={}, contentId={}",
                      md5Line,
                      md5Checksum,
                      file.getName(),
                      contentId2);
            assertTrue("\"" + md5Line + "\" does not contain " + contentId2, md5Line.contains(contentId2));
            assertTrue(md5Line.contains(md5Checksum));
        }
    }

    private void verifySha256Manifest(List<ContentItem> items, List<File> sourceFiles) throws IOException {
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

    private void verifyPropsFile(File propsFile) throws IOException {
        ObjectMapper jsonMapper = new ObjectMapper();
        List<Map> propsContent = Arrays.asList(jsonMapper.readValue(propsFile, Map[].class));
        for (Map contentItem : propsContent) {
            String contentItemPropsJson = contentItem.toString();
            assertTrue("Properties JSON file is missing expected file path " +
                       propertyFilePath + ". Full JSON file contents: " + contentItemPropsJson,
                       contentItemPropsJson.contains(StorageProvider.PROPERTIES_CONTENT_FILE_PATH +
                                                     "=" + propertyFilePath));
        }
    }

    /**
     * @param filename
     * @return
     * @throws IOException
     */
    private List<String> getLines(String filename) throws IOException {
        List<String> lines = Files.readAllLines(getPath(contentDir, filename), StandardCharsets.UTF_8);
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
                return string.substring(index + 1);
            }
        });
        return lines;
    }

    private Path getPath(File dir, String filename) {
        Path path =
            FileSystems.getDefault().getPath(dir.getAbsolutePath(),
                                             filename);
        return path;
    }

    /**
     * Distributes the items into sublists and runs them in separate threads.
     *
     * @param items
     * @param threads
     * @throws IOException
     */
    private void writeItems(List<ContentItem> items, int threads) throws IOException {
        int itemCount = items.size();
        final CountDownLatch countdownLatch = new CountDownLatch(threads);

        int bottomIndex = 0;

        int itemsPerThread = itemCount / threads;

        int remainder = itemCount % threads;
        int thread = 0;
        final AtomicInteger processed = new AtomicInteger(0);

        while (bottomIndex < itemCount) {
            thread++;
            final int fromIndex = bottomIndex;

            int topIndex = fromIndex + itemsPerThread;

            if (thread == threads) {
                topIndex += remainder;
            }

            final int toIndex = topIndex;
            final List<ContentItem> contents = items.subList(fromIndex, toIndex);
            new Thread(new Runnable() {
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
            assertTrue(countdownLatch.await(60, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        assertEquals(items.size(), processed.get());
    }

    /**
     * @param spaceId
     * @param contentId
     * @throws SnapshotException
     */
    private File setupContentItem(List<ContentItem> items,
                                  String spaceId,
                                  String contentId,
                                  int times)
        throws IOException, SnapshotException {
        int size = 1024 * 100;
        File content = createUniqueTempFile(size, contentId);
        ChecksumUtil util = new ChecksumUtil(Algorithm.MD5);
        assertEquals(size, content.length());
        String md5 = util.generateChecksum(content);
        InputStream is = new FileInputStream(content);
        Map<String, String> map = createContentProperties(md5);
        ContentStream contentStream = new ContentStream(is, map);
        ContentItem item = new ContentItem(spaceId, contentId);

        expect(retrievalSource.getSourceContent(eq(item), isA(RetrievalListener.class)))
            .andReturn(contentStream);

        items.add(item);
        this.snapshotManager.addContentItem(eq(snapshot),
                                            eq(contentId),
                                            isA(Map.class));
        expectLastCall().times(times);
        return content;
    }

    @NotNull
    private Map<String, String> createContentProperties(String md5) {
        Map<String, String> map = new HashMap<>();
        map.put(StorageProvider.PROPERTIES_CONTENT_CHECKSUM, md5);

        String date =
            DateUtil.convertToString(System.currentTimeMillis(),
                                     DateFormat.LONG_FORMAT);
        log.debug("date=" + date);
        map.put(StorageProvider.PROPERTIES_CONTENT_FILE_CREATED, date);
        map.put(StorageProvider.PROPERTIES_CONTENT_FILE_LAST_ACCESSED, date);
        map.put(StorageProvider.PROPERTIES_CONTENT_FILE_MODIFIED, date);
        map.put(StorageProvider.PROPERTIES_CONTENT_FILE_PATH, propertyFilePath);
        return map;
    }

    private File createUniqueTempFile(int size, String filename) throws IOException {

        File file = File.createTempFile(filename + "-", ".txt");
        FileOutputStream os = new FileOutputStream(file);
        int block = 10 * 1024;
        int remainder = size % block;
        int blocks = size / block;
        byte[] data = new byte[block];

        byte[] uniqueBytes = filename.getBytes();
        for (int i = 0; i < uniqueBytes.length; i++) {
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

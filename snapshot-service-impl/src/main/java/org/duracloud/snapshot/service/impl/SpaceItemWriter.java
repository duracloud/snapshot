/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.ParseException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.duracloud.chunk.util.ChunkUtil;
import org.duracloud.client.ContentStore;
import org.duracloud.common.constant.Constants;
import org.duracloud.common.error.DuraCloudRuntimeException;
import org.duracloud.common.model.ContentItem;
import org.duracloud.common.retry.Retriable;
import org.duracloud.common.retry.Retrier;
import org.duracloud.common.util.ChecksumUtil;
import org.duracloud.retrieval.mgmt.OutputWriter;
import org.duracloud.retrieval.mgmt.RetrievalListener;
import org.duracloud.retrieval.mgmt.RetrievalWorker;
import org.duracloud.retrieval.source.RetrievalSource;
import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.service.SnapshotManager;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.ItemWriteListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.item.ItemWriter;

/**
 * This class is responsible for reading the contents and properties of a duracloud content item,
 * writing it to disk,  appending its md5 and sha256 to separate text files, appending
 * the item properties to a json file, and writing the item to the snapshot content repo.
 *
 * @author Erik Paulsson
 * Date: 2/7/14
 */
public class SpaceItemWriter extends StepExecutionSupport implements ItemWriter<ContentItem>,
                                                                     ItemWriteListener<ContentItem> {

    private static final Logger log =
        LoggerFactory.getLogger(SpaceItemWriter.class);

    private RetrievalSource retrievalSource;
    private File contentDir;
    private OutputWriter outputWriter;
    private BufferedWriter propsWriter;
    private BufferedWriter md5Writer;
    private BufferedWriter sha256Writer;
    private ContentItem snapshotPropsContentItem;
    private SnapshotManager snapshotManager;
    private Snapshot snapshot;
    private List<String> errors = new LinkedList<String>();
    private SpaceManifestSnapshotManifestVerifier spaceManifestSnapshotManifestVerifier;
    private ChunkUtil chunkUtil = new ChunkUtil();
    private Map<String, String> md5Cache = new HashMap<>();
    private Map<String, String> sha256Cache = new HashMap<>();
    private Map<String, String> propsCache = new HashMap<>();
    private File md5ManifestFile;
    private File sha256ManifestFile;
    private File propsFile;
    private DB db;
    private File dbFile;
    private int totalChecksumsPerformed = 0;

    /**
     * @param snapshot
     * @param retrievalSource
     * @param contentDir
     * @param outputWriter
     * @param propsFile
     * @param md5ManifestFile
     * @param sha256ManifestFile
     * @param snapshotManager
     * @param spaceManifestSnapshotManifestVerifier
     */
    public SpaceItemWriter(Snapshot snapshot,
                           RetrievalSource retrievalSource,
                           File contentDir,
                           OutputWriter outputWriter,
                           File propsFile,
                           File md5ManifestFile,
                           File sha256ManifestFile,
                           SnapshotManager snapshotManager,
                           SpaceManifestSnapshotManifestVerifier spaceManifestSnapshotManifestVerifier) {
        super();
        this.snapshot = snapshot;
        this.retrievalSource = retrievalSource;
        this.contentDir = contentDir;
        this.outputWriter = outputWriter;
        this.md5ManifestFile = md5ManifestFile;
        this.sha256ManifestFile = sha256ManifestFile;
        this.snapshotManager = snapshotManager;
        this.spaceManifestSnapshotManifestVerifier = spaceManifestSnapshotManifestVerifier;
        this.dbFile = new File(contentDir, snapshot.getName() + ".db");
        this.propsFile = propsFile;
    }

    private DB makeDatabase() {
        return DBMaker.fileDB(this.dbFile).transactionEnable().closeOnJvmShutdown().make();
    }

    protected void closeDatabase() {
        if (this.db != null) {
            this.db.close();
        }
    }

    protected void deleteDatabase() {
        closeDatabase();
        this.dbFile.delete();
    }

    private BufferedWriter createWriter(File file) throws IOException {
        BufferedWriter writer =
            Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8);
        return writer;
    }

    @Override
    public void write(List<? extends ContentItem> items) throws IOException {
        for (ContentItem contentItem : items) {
            String contentId = contentItem.getContentId();
            log.debug("writing: {}", contentId);

            if (!contentId.equals(Constants.SNAPSHOT_PROPS_FILENAME)) {
                File dataDir = getDataDir();
                retrieveFile(contentItem, dataDir);
            } else {
                // Cache the snapshot properties ContentItem so we can
                // retrieve it last in the 'afterStep' method.
                snapshotPropsContentItem = contentItem;
            }
        }
    }

    private File getDataDir() {
        return new File(contentDir, "data");
    }

    protected void retrieveFile(ContentItem contentItem, File directory)
        throws IOException {
        retrieveFile(contentItem, directory, true, false);
    }

    private void cacheValue(Map<String, String> cache, String key, String value) {
        cache.put(key, value);
        db.commit();
    }

    protected void retrieveFile(ContentItem contentItem, File directory,
                                boolean writeChecksums, boolean lastItem)
        throws IOException {

        String contentId = chunkUtil.preChunkedContentId(contentItem.getContentId());

        //retrieve cached data
        String md5Checksum = md5Cache.get(contentId);
        String sha256 = sha256Cache.get(contentId);

        Map<String, String> props = null;

        RetrievalWorker retrievalWorker =
            new RetrievalWorker(contentItem, retrievalSource, directory,
                                true, outputWriter, false, true);

        File localFile = retrievalWorker.getLocalFile();

        if (md5Checksum == null) { // File is not in MD5 cache
            StopWatch sw = new StopWatch();
            sw.start();

            props = retrievalWorker.retrieveFile(new RetrievalListener() {
                @Override
                public void chunkRetrieved(String chunk) {
                    getStepExecution().getExecutionContext().put("last-chunk-retrieved-" +
                                                                 Thread.currentThread().getName(),
                                                                 chunk);
                }
            });

            sw.stop();

            if (null == props) { // Transfer failed
                throw new IOException("Failed to retrieve " + contentId + " after " +
                                      sw.getTime() / 1000 + " seconds");
            }

            log.info("Finished retrieving content: contentId={}, " +
                     " fileSize={}, file path={}, elapsedTimeMs={}, transferRateMbps={}",
                     contentId,
                     localFile.length(),
                     localFile.getAbsolutePath(),
                     sw.getTime(),
                     (localFile.length() * 0.008) / sw.getTime());

            // cache props
            cacheValue(propsCache, contentId, PropertiesSerializer.serialize(props));

            // cache md5
            md5Checksum = props.get(ContentStore.CONTENT_CHECKSUM);
            cacheValue(md5Cache, contentId, md5Checksum);

            log.info("Retrieved item {} from space {} with MD5 checksum {}",
                     contentItem.getContentId(),
                     contentItem.getSpaceId(),
                     md5Checksum);
        } else {
            log.info("MD5 for contentId {} is already cached." +
                     " No need to download and reverify.",
                     contentId);

            // Get the props from cache, otherwise retrieve them.
            String propsStr = propsCache.get(contentId);
            if (propsStr != null) {
                props = PropertiesSerializer.deserialize(propsStr);
                log.info("Props found in cache for {}.", contentId);
            } else {
                log.info("Props not found in cache for {}.", contentId);
                props = retrievalSource.getSourceProperties(contentItem);
                cacheValue(propsCache, contentId, PropertiesSerializer.serialize(props));
                log.info("Retrieved and cached props for {}.", contentId);
            }
        }

        if (localFile.exists() && md5Checksum != null) {
            try {
                if (writeChecksums) {
                    writeMD5Checksum(contentId, md5Checksum);
                    if (sha256 == null) {
                        ChecksumUtil sha256ChecksumUtil =
                            new ChecksumUtil(ChecksumUtil.Algorithm.SHA_256);
                        StopWatch sw = new StopWatch();

                        log.info("Starting sha256 checksum generation for contentId={}; file path={}",
                                 contentId,
                                 localFile.getAbsolutePath());

                        sw.start();
                        sha256 = sha256ChecksumUtil.generateChecksum(localFile);
                        totalChecksumsPerformed++;
                        sw.stop();

                        log.info("Finished sha256 checksum generation for contentId={};" +
                                 " fileSize={}, file path={}; elapsedTimeMs={}",
                                 contentId,
                                 localFile.length(),
                                 localFile.getAbsolutePath(),
                                 sw.getTime());

                        //cache the result
                        cacheValue(sha256Cache, contentId, sha256);
                    } else {
                        log.info("SHA-256 checksum for contentId {} is already cached, " +
                                 "no need to recompute", contentId);
                    }

                    writeSHA256Checksum(contentId, sha256);
                }

                writeToSnapshotManager(contentId, props);
                writeContentProperties(contentId, props, lastItem);
            } catch (IOException ioe) {
                log.error("Error writing snapshot details: " + ioe.getMessage());
                throw ioe;
            }
        } else {
            // There was a problem! Throw a meaningful exception:
            String baseError = "Retrieved item " + contentItem.getContentId() +
                               " from space " + contentItem.getSpaceId() + " could not " +
                               "be processed due to: ";
            if (!localFile.exists()) {
                String error = baseError + "The local file at path " +
                               localFile.getAbsolutePath() + " could not be found.";
                log.error(error);
                throw new IOException(error);
            } else {
                String error = baseError + "MD5 checksum for retrieved file was null";
                log.error(error);
                throw new IOException(error);
            }
        }
    }

    protected int getTotalChecksumsPerformed() {
        return totalChecksumsPerformed;
    }

    /**
     * @param contentId
     * @param props
     */
    private void writeToSnapshotManager(final String contentId,
                                        final Map<String, String> props) throws IOException {
        try {
            new Retrier().execute(new Retriable() {
                @Override
                public Object retry() throws Exception {
                    snapshotManager.addContentItem(snapshot, contentId, props);
                    return null;
                }

            });
        } catch (Exception e) {
            log.error("Failed to add snapshot content item: " + contentId +
                      " to snapshot " + snapshot + ": " + e.getMessage(), e);
            throw new IOException(e);
        }
    }

    protected void writeMD5Checksum(String contentId,
                                    String md5Checksum) throws IOException {
        synchronized (md5Writer) {
            ManifestFileHelper.writeManifestEntry(md5Writer, contentId, md5Checksum);
        }
    }

    protected void writeSHA256Checksum(String contentId,
                                       String sha256Checksum) throws IOException {

        synchronized (sha256Writer) {
            ManifestFileHelper.writeManifestEntry(sha256Writer, contentId, sha256Checksum);
        }
    }

    protected void writeContentProperties(String contentId,
                                          Map<String, String> props,
                                          boolean lastItem)
        throws IOException {
        Set<String> propKeys = props.keySet();
        StringBuffer sb = new StringBuffer(100);
        sb.append("{\n  \"" + contentId + "\": {\n");
        for (String propKey : propKeys) {
            String propValue = props.get(propKey);
            // Escape special characters
            propValue = propValue.replace("\\", "\\\\");
            propValue = propValue.replace("\"", "\\\"");

            sb.append("    \"" + propKey + "\": \"" + propValue + "\",\n");
        }
        sb.deleteCharAt(sb.length() - 2); // delete comma after last key/value pair

        sb.append("  }\n}");
        if (!lastItem) {
            sb.append(",");
        }
        sb.append("\n");

        synchronized (propsWriter) {
            propsWriter.write(sb.toString());
            propsWriter.flush();
        }
    }

    protected void retrieveSnapshotProperties() {
        if (snapshotPropsContentItem != null) {
            try {
                retrieveFile(snapshotPropsContentItem, contentDir, false, true);
                log.info("Snapshot properties retrieved");
            } catch (IOException ioe) {
                log.error("Error retrieving the snapshot properties file: " +
                          ioe.getMessage(), ioe);
            }
        } else {
            String message = "No snapshot properties file found. (" +
                             Constants.SNAPSHOT_PROPS_FILENAME + ")";
            log.error(message);
            errors.add(message);
        }
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        ExitStatus status = super.afterStep(stepExecution);
        log.info("Step complete with status: {}", stepExecution.getExitStatus());
        close("md5 writer", md5Writer);
        close("sh256 writer", sha256Writer);
        close("output writer", outputWriter);

        retrieveSnapshotProperties();
        closePropsWriter();

        if (errors.size() == 0) {
            log.info("No errors in retrieval of snapshot {}; " +
                     "Proceeding with space manifest - snapshot manifest verification...",
                     snapshot.getName());
            errors.addAll(verifySpace(spaceManifestSnapshotManifestVerifier));
        }

        if (errors.size() > 0) {
            stepExecution.upgradeStatus(BatchStatus.FAILED);
            status = status.and(ExitStatus.FAILED);
            for (String error : errors) {
                status = status.addExitDescription(error);
            }
            log.error("Space item writer failed due to the following error(s): " +
                      status.getExitDescription());
        }

        deleteDatabase();
        return status;
    }

    private void closePropsWriter() {
        try {
            synchronized (propsWriter) {
                propsWriter.write("]\n");
                propsWriter.flush();
            }

            log.debug("Closed props writer");
        } catch (IOException ioe) {
            String message = "Error writing end of content property manifest: " + ioe.getMessage();
            errors.add(message);
            log.error(message, ioe);
        } finally {
            IOUtils.closeQuietly(propsWriter);
        }
    }

    private void close(String writerName, Object writer) {
        try {

            if (writer instanceof Closeable) {
                ((Closeable) writer).close();
            } else if (writer instanceof OutputWriter) {
                ((OutputWriter) writer).close();
            } else {
                throw new DuraCloudRuntimeException(writerName +
                                                    " is not a supported parameter type for this method.");
            }
            log.info("closed {}", writerName);
        } catch (IOException ioe) {
            String message = "Error closing " + writerName + " BufferedWriter: " + ioe.getMessage();
            errors.add(message);
            log.error(message, ioe);
        }
    }

    private void loadCacheFromFile(Map<String, String> cache,
                                   File file,
                                   Function<String, Boolean> isValidChecksum) throws IOException {
        //if the cache is empty check if there is are md5 and sha256 manifests
        //that can be used to prepopulate the cache.
        if (cache.isEmpty() && file.exists()) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
                String line = null;
                while ((line = reader.readLine()) != null) {
                    try {
                        ManifestEntry entry = ManifestFileHelper.parseManifestEntry(line);
                        String contentId = entry.getContentId();
                        String checksum = entry.getChecksum();
                        if (isValidChecksum.apply(checksum)) {
                            cacheValue(cache, contentId, checksum);
                        } else {
                            log.info("Checksum {} in manifest file {} was not a valid checksum: skipping.",
                                     checksum, file.getAbsolutePath());
                        }
                    } catch (ParseException ex) {
                        log.info("Unable to parse line in manifest file {}. message={}. skipping: line={}",
                                 file.getAbsolutePath(), ex.getMessage(), line);
                    }
                }
            }
        }
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        super.beforeStep(stepExecution);
        log.info("Starting step {}", stepExecution);
        try {
            this.db = makeDatabase();

            md5Cache = db.treeMap("md5Cache", Serializer.STRING, Serializer.STRING)
                         .createOrOpen();
            sha256Cache = db.treeMap("sha256Cache", Serializer.STRING, Serializer.STRING)
                            .createOrOpen();
            propsCache = db.treeMap("propsCache", Serializer.STRING, Serializer.STRING)
                           .createOrOpen();

            //load caches from files left from previously unsuccessful run.
            loadCacheFromFile(
                this.md5Cache,
                this.md5ManifestFile,
                x -> x != null && x.matches("[a-fA-F0-9]{32}"));
            loadCacheFromFile(
                this.sha256Cache,
                this.sha256ManifestFile,
                x -> x != null && x.matches("[a-fA-F0-9]{64}"));

            //initialize writers after loading cache from files.
            try {
                this.propsWriter = createWriter(propsFile);
                this.md5Writer = createWriter(this.md5ManifestFile);
                this.sha256Writer = createWriter(this.sha256ManifestFile);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }

            errors.clear();
            synchronized (propsWriter) {
                propsWriter.write("[\n");
                propsWriter.flush();
            }
        } catch (IOException ioe) {
            log.error("Error writing start of content property " +
                      "manifest: ", ioe);
        }
    }

    // Method defined in ItemWriteListener interface
    @Override
    public void onWriteError(Exception e, List<? extends ContentItem> items) {
        StringBuilder sb = new StringBuilder();
        for (ContentItem item : items) {
            sb.append(item.getContentId() + ", ");
        }

        String message = "Error writing item(s): " + e.getMessage() + ": items=" + sb.toString();
        this.errors.add(message);
        log.error(message, e);
    }

    // Method defined in ItemWriteListener interface
    @Override
    public void beforeWrite(List<? extends ContentItem> items) {
        // no-op impl
    }

    // Method defined in ItemWriteListener interface
    @Override
    public void afterWrite(List<? extends ContentItem> items) {
        // no-op impl
    }
}

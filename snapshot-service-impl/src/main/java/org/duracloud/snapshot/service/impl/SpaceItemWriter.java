/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.duracloud.chunk.util.ChunkUtil;
import org.duracloud.client.ContentStore;
import org.duracloud.common.constant.Constants;
import org.duracloud.common.error.DuraCloudRuntimeException;
import org.duracloud.common.model.ContentItem;
import org.duracloud.common.retry.Retriable;
import org.duracloud.common.retry.Retrier;
import org.duracloud.common.util.ChecksumUtil;
import org.duracloud.retrieval.mgmt.OutputWriter;
import org.duracloud.retrieval.mgmt.RetrievalWorker;
import org.duracloud.retrieval.source.RetrievalSource;
import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.service.SnapshotManager;
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
 *         Date: 2/7/14
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
    private ChecksumUtil sha256ChecksumUtil;
    private ContentItem snapshotPropsContentItem;
    private SnapshotManager snapshotManager;
    private Snapshot snapshot;
    private List<String> errors = new LinkedList<String>();
    private SpaceManifestDpnManifestVerifier spaceManifestDpnManifestVerifier;
    private ChunkUtil chunkUtil = new ChunkUtil();
    
    
    public SpaceItemWriter(Snapshot snapshot, RetrievalSource retrievalSource,
                           File contentDir,
                           OutputWriter outputWriter,
                           BufferedWriter propsWriter,
                           BufferedWriter md5Writer,
                           BufferedWriter sha256Writer, 
                           SnapshotManager snapshotManager, 
                           SpaceManifestDpnManifestVerifier spaceManifestDpnManifestVerifier) {
        super();
        this.snapshot = snapshot;
        this.retrievalSource = retrievalSource;
        this.contentDir = contentDir;
        this.outputWriter = outputWriter;
        this.propsWriter = propsWriter;
        this.md5Writer = md5Writer;
        this.sha256Writer = sha256Writer;
        this.sha256ChecksumUtil =
            new ChecksumUtil(ChecksumUtil.Algorithm.SHA_256);
        this.snapshotManager = snapshotManager;
        this.spaceManifestDpnManifestVerifier = spaceManifestDpnManifestVerifier;
    }

    @Override
    public void write(List<? extends ContentItem> items) throws IOException {
        for(ContentItem contentItem: items) {
            String contentId = contentItem.getContentId();
            log.debug("writing: {}", contentId);

            if(!contentId.equals(Constants.SNAPSHOT_PROPS_FILENAME)) {
                File dataDir = getDataDir();
                retrieveFile(contentItem, dataDir);
            } else {
                // Cache the snapshot properties ContentItem so we can
                // retrieve it last in the 'afterStep' method.
                snapshotPropsContentItem = contentItem;
            }
        }
    }

    /**
     * @return
     */
    private File getDataDir() {
        return new File(contentDir, "data");
    }

    protected void retrieveFile(ContentItem contentItem, File directory)
            throws IOException {
        retrieveFile(contentItem, directory, true, false);
    }

    protected void retrieveFile(ContentItem contentItem, File directory,
                                boolean writeChecksums, boolean lastItem)
            throws IOException {
        RetrievalWorker retrievalWorker =
            new RetrievalWorker(contentItem, retrievalSource, directory,
                                true, outputWriter, false, true);
        Map<String,String> props = retrievalWorker.retrieveFile();
        File localFile = retrievalWorker.getLocalFile();

        String md5Checksum = null;
        if(null != props) {
            md5Checksum = props.get(ContentStore.CONTENT_CHECKSUM);
        }
        log.info("Retrieved item {} from space {} with MD5 checksum {}",
                 contentItem.getContentId(),
                 contentItem.getSpaceId(),
                 md5Checksum);
        String contentId = chunkUtil.preChunkedContentId(contentItem.getContentId());
        if(localFile.exists() && md5Checksum != null) {
            try {
                if (writeChecksums) {
                    writeMD5Checksum(contentId, md5Checksum);
                    writeSHA256Checksum(contentId, localFile);
                }
                writeToSnapshotManager(contentId, props);
                writeContentProperties(contentId, props, lastItem);
            }catch(IOException ioe) {
                log.error("Error writing snapshot details: " + ioe.getMessage());
                throw ioe;
            }
        } else {
            // There was a problem! Throw a meaningful exception:
            String baseError =
                String.format("Retrieved item {} from space {} could not " +
                              "be processed due to: ",
                              contentItem.getContentId(),
                              contentItem.getSpaceId());
            if(!localFile.exists()) {
                String error = baseError + "The local file at path " +
                               localFile.getAbsolutePath()+ " could not be found.";
                log.error(error);
                throw new IOException(error);
            } else {
                String error = baseError + "MD5 checksum for retrieved file was null";
                log.error(error);
                throw new IOException(error);
            }
        }
    }

    /**
     * @param contentItem
     * @param props
     */
    private void writeToSnapshotManager(final String contentId,
                                        final Map<String, String> props) throws IOException{
        try {
            new Retrier().execute(new Retriable() {
                @Override
                public Object retry() throws Exception {
                    snapshotManager.addContentItem(snapshot, contentId, props);
                    return null;
                }

            });
        } catch (Exception e) {
            log.error("failed to add snapshot content item: "
                + contentId + " to snapshot " + snapshot + ": " + e.getMessage(), e);
            throw new IOException(e);
        }
    }

    protected void writeMD5Checksum(String contentId,
                                    String md5Checksum) throws IOException {
        synchronized (md5Writer) {
            ManifestFileHelper.writeManifestEntry(md5Writer, 
                                                    contentId, 
                                                    md5Checksum);
        }
    }



    protected synchronized void writeSHA256Checksum(String contentId,
                                       File localFile) throws IOException {
        String sha256Checksum = sha256ChecksumUtil.generateChecksum(localFile);
        ManifestFileHelper.writeManifestEntry(sha256Writer, 
                                                contentId, 
                                                sha256Checksum);
    }

    protected void writeContentProperties(String contentId,
                                          Map<String,String> props,
                                          boolean lastItem)
            throws IOException {
        
        
        Set<String> propKeys = props.keySet();
        StringBuffer sb = new StringBuffer(100);
        sb.append("{\n  \"" + contentId + "\": {\n");
        for(String propKey: propKeys) {
            sb.append("    \"" + propKey + "\": \"" + props.get(propKey) + "\",\n");
        }
        sb.deleteCharAt(sb.length() - 2); // delete comma after last key/value pair

        sb.append("  }\n}");
        if(! lastItem) {
            sb.append(",");
        }
        sb.append("\n");

        synchronized (propsWriter) {
            propsWriter.write(sb.toString());
        }
    }

    protected void retrieveSnapshotProperties() {
        if(snapshotPropsContentItem != null) {
            try {
                retrieveFile(snapshotPropsContentItem, contentDir, false, true);
                log.info("snapshot properties retrieved");
            } catch (IOException ioe) {
                log.error("Error retrieving the snapshot properties file: ",
                             ioe);
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
        log.info("Step complete with status: {}",
                     stepExecution.getExitStatus());

        close("md5 writer", md5Writer);
        close("sh256 writer", sha256Writer);
        close("output writer", outputWriter);
        
        retrieveSnapshotProperties();
        closePropsWriter();
        
        if(errors.size() == 0){
           log.info("no errors - proceeding with space manifest -dpn manifest verification...");
           errors.addAll(verifySpace(spaceManifestDpnManifestVerifier));
        }
        
        if(errors.size() > 0){
            stepExecution.upgradeStatus(BatchStatus.FAILED);
            status = status.and(ExitStatus.FAILED);
            for(String error : errors){
                status = status.addExitDescription(error);
            }
            log.error("space item writer failed due to the following error(s): " + 
                       status.getExitDescription());
        }
        return status;
    }

    private void closePropsWriter() {
        try {
            synchronized (propsWriter) {
                propsWriter.write("]\n");
            }
            
            log.info("closed props writer");
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
            
            if(writer instanceof Closeable){
                ((Closeable)writer).close();
            }else if(writer instanceof OutputWriter){
                ((OutputWriter)writer).close();
            }else {
                throw new DuraCloudRuntimeException(writerName + 
                                                    " is not a supported parameter type for this method.");
            }
            log.info("closed {}", writerName);
        } catch (IOException ioe) {
            String message = "Error closing "+writerName+" BufferedWriter: " + ioe.getMessage();
            errors.add(message);
            log.error(message, ioe);
        }
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        super.beforeStep(stepExecution);
        log.info("starting step {}");
        try {
            errors.clear();
            synchronized (propsWriter) {
                propsWriter.write("[\n");
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
        for(ContentItem item: items) {
            sb.append(item.getContentId() + ", ");
        }
        
        String message = "Error writing item(s): " + e.getMessage() + ": items=" + sb.toString();
        this.errors.add(message);
        log.error(message,e);
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

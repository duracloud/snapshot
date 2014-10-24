/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import org.duracloud.client.ContentStore;
import org.duracloud.common.constant.Constants;
import org.duracloud.common.model.ContentItem;
import org.duracloud.common.util.ChecksumUtil;
import org.duracloud.retrieval.mgmt.OutputWriter;
import org.duracloud.retrieval.mgmt.RetrievalWorker;
import org.duracloud.retrieval.source.RetrievalSource;
import org.duracloud.snapshot.SnapshotException;
import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.service.SnapshotManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.ItemWriteListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.ItemWriter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Erik Paulsson
 *         Date: 2/7/14
 */
public class SpaceItemWriter implements ItemWriter<ContentItem>,
                                        StepExecutionListener,
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

    public SpaceItemWriter(Snapshot snapshot, RetrievalSource retrievalSource,
                           File contentDir,
                           OutputWriter outputWriter,
                           BufferedWriter propsWriter,
                           BufferedWriter md5Writer,
                           BufferedWriter sha256Writer, 
                           SnapshotManager snapshotManager) {
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
    }

    @Override
    public void write(List<? extends ContentItem> items) throws IOException {
        for(ContentItem contentItem: items) {
            String contentId = contentItem.getContentId();
            log.debug("writing: {}", contentId);

            if(! contentId.equals(Constants.SNAPSHOT_PROPS_FILENAME)) {
                File dataDir = new File(contentDir, "data");
                retrieveFile(contentItem, dataDir);
            } else {
                // Cache the snapshot properties ContentItem so we can
                // retrieve it last in the 'afterStep' method.
                snapshotPropsContentItem = contentItem;
            }
        }
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

        String md5Checksum = props.get(ContentStore.CONTENT_CHECKSUM);
        log.info("Retrieved item {} from space {} with MD5 checksum {}",
                 contentItem.getContentId(),
                 contentItem.getSpaceId(),
                 md5Checksum);

        if(localFile.exists() && md5Checksum != null) {
            if(writeChecksums) {
                writeMD5Checksum(contentItem, md5Checksum);
                writeSHA256Checksum(contentItem, localFile);
            }
            writeContentProperties(contentItem, props, lastItem);
            writeToSnapshotManager(contentItem, props);
        } else {
            // There was a problem! Throw a meaningful exception:
            String baseError =
                String.format("Retrieved item {} from space {} could not " +
                              "be processed due to: ",
                              contentItem.getContentId(),
                              contentItem.getSpaceId());
            if(!localFile.exists()) {
                throw new IOException(baseError + "The local file at path " +
                                      localFile.getAbsolutePath()+
                                      " could not be found.");
            } else {
                throw new IOException(baseError + "MD5 checksum for " +
                                      "retrieved file was null");
            }
        }
    }

    /**
     * @param contentItem
     * @param props
     */
    private void writeToSnapshotManager(ContentItem contentItem,
                                        Map<String, String> props) throws IOException{
        try {
            this.snapshotManager.addContentItem(snapshot, contentItem.getContentId(), props);
        } catch (SnapshotException e) {
            log.error("failed to add snapshot content item: "
                + contentItem + ": " + e.getMessage(), e);
            throw new IOException(e);
        }
    }

    protected void writeMD5Checksum(ContentItem contentItem,
                                    String md5Checksum) throws IOException {
        synchronized (md5Writer) {
            md5Writer.write(md5Checksum + "  data/" +
                               contentItem.getContentId() + "\n");
        }
    }

    protected synchronized void writeSHA256Checksum(ContentItem contentItem,
                                       File localFile) throws IOException {
        String sha256Checksum = sha256ChecksumUtil.generateChecksum(localFile);
        sha256Writer.write(sha256Checksum + "  data/" +
                           contentItem.getContentId() + "\n");
    }

    protected void writeContentProperties(ContentItem contentItem,
                                          Map<String,String> props,
                                          boolean lastItem)
            throws IOException {
        
        
        Set<String> propKeys = props.keySet();
        StringBuffer sb = new StringBuffer(100);
        sb.append("{\n  \"" + contentItem.getContentId() + "\": {\n");
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
            } catch (IOException ioe) {
                log.error("Error retrieving the snapshot properties file: ",
                             ioe);
            }
        } else {
            log.error("No snapshot properties file found. (" +
                             Constants.SNAPSHOT_PROPS_FILENAME + ")");
        }
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        log.debug("Step complete with status: {}",
                     stepExecution.getExitStatus());
        try {
            md5Writer.close();
        } catch (IOException ioe) {
            log.error("Error closing MD5 manifest BufferedWriter: ", ioe);
        }

        try {
            sha256Writer.close();
        } catch (IOException ioe) {
            log.error("Error closing SHA-256 manifest BufferedWriter: ", ioe);
        }

        retrieveSnapshotProperties();
        try {
            synchronized (propsWriter) {
                propsWriter.write("]\n");
            }
        } catch (IOException ioe) {
            log.error("Error writing end of content property " +
                             "manifest: ", ioe);
        }
        try {
            propsWriter.close();
        } catch (IOException ioe) {
            log.error("Error closing content property " +
                             "manifest BufferedWriter: ", ioe);
        }
        return stepExecution.getExitStatus();
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        try {
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
        StringBuffer sb = new StringBuffer(50);
        for(ContentItem item: items) {
            sb.append(item.getContentId() + ", ");
        }
        log.error("Error writing item(s): " + sb.toString(), e);
        // TODO: write error entry to database?
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

/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.manager.spring.batch;

import org.duracloud.client.ContentStore;
import org.duracloud.common.constant.Constants;
import org.duracloud.common.model.ContentItem;
import org.duracloud.common.util.ChecksumUtil;
import org.duracloud.retrieval.mgmt.OutputWriter;
import org.duracloud.retrieval.mgmt.RetrievalWorker;
import org.duracloud.retrieval.source.RetrievalSource;
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

    private static final Logger LOGGER =
        LoggerFactory.getLogger(SpaceItemWriter.class);

    private RetrievalSource retrievalSource;
    private File contentDir;
    private OutputWriter outputWriter;
    private BufferedWriter propsWriter;
    private BufferedWriter md5Writer;
    private BufferedWriter sha256Writer;
    private ChecksumUtil sha256ChecksumUtil;
    private ContentItem snapshotPropsContentItem;

    public SpaceItemWriter(RetrievalSource retrievalSource,
                           File contentDir,
                           OutputWriter outputWriter,
                           BufferedWriter propsWriter,
                           BufferedWriter md5Writer,
                           BufferedWriter sha256Writer) {
        this.retrievalSource = retrievalSource;
        this.contentDir = contentDir;
        this.outputWriter = outputWriter;
        this.propsWriter = propsWriter;
        this.md5Writer = md5Writer;
        this.sha256Writer = sha256Writer;
        this.sha256ChecksumUtil =
            new ChecksumUtil(ChecksumUtil.Algorithm.SHA_256);
    }

    @Override
    public void write(List<? extends ContentItem> items) throws IOException {
        for(ContentItem contentItem: items) {
            LOGGER.debug("writing: {}", contentItem.getContentId());

            if(! contentItem.getContentId().equals(Constants.SNAPSHOT_ID)) {
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
        if(localFile.exists() && md5Checksum != null) {
            if(writeChecksums) {
                writeMD5Checksum(contentItem, md5Checksum);
                writeSHA256Checksum(contentItem, localFile);
            }
            writeContentProperties(contentItem, props, lastItem);
        } else {
            // TODO: throw Exception???

        }
    }

    protected void writeMD5Checksum(ContentItem contentItem,
                                    String md5Checksum) throws IOException {
        synchronized (md5Writer) {
            md5Writer.write(md5Checksum + "  data/" +
                               contentItem.getContentId() + "\n");
        }
    }

    protected void writeSHA256Checksum(ContentItem contentItem,
                                       File localFile) throws IOException {
        String sha256Checksum =
            sha256ChecksumUtil.generateChecksum(localFile);
        synchronized (sha256Writer) {
            sha256Writer.write(sha256Checksum + "  data/" +
                                   contentItem.getContentId() + "\n");
        }
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
                LOGGER.error("Error retrieving the snapshot properties file: ",
                             ioe);
            }
        } else {
            LOGGER.error("No snapshot properties file found. (" +
                             Constants.SNAPSHOT_ID + ")");
        }
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        LOGGER.debug("Step complete with status: {}",
                     stepExecution.getExitStatus());
        try {
            md5Writer.close();
        } catch (IOException ioe) {
            LOGGER.error("Error closing MD5 manifest BufferedWriter: ", ioe);
        }

        try {
            sha256Writer.close();
        } catch (IOException ioe) {
            LOGGER.error("Error closing SHA-256 manifest BufferedWriter: ", ioe);
        }

        retrieveSnapshotProperties();
        try {
            synchronized (propsWriter) {
                propsWriter.write("]\n");
            }
        } catch (IOException ioe) {
            LOGGER.error("Error writing end of content property " +
                             "manifest: ", ioe);
        }
        try {
            propsWriter.close();
        } catch (IOException ioe) {
            LOGGER.error("Error closing content property " +
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
            LOGGER.error("Error writing start of content property " +
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
        LOGGER.error("Error writing item(s): " + sb.toString(), e);
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

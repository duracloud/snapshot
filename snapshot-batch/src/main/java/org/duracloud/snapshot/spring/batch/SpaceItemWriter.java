/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.spring.batch;

import org.duracloud.client.ContentStore;
import org.duracloud.common.model.ContentItem;
import org.duracloud.common.util.ChecksumUtil;
import org.duracloud.retrieval.mgmt.OutputWriter;
import org.duracloud.retrieval.mgmt.RetrievalWorker;
import org.duracloud.retrieval.source.RetrievalSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.ItemWriter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @author Erik Paulsson
 *         Date: 2/7/14
 */
public class SpaceItemWriter implements ItemWriter<ContentItem>, StepExecutionListener {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(SpaceItemWriter.class);

    private RetrievalSource retrievalSource;
    private File contentDir;
    private OutputWriter outputWriter;
    private BufferedWriter md5Witer;
    private BufferedWriter sha256Writer;
    private ChecksumUtil sha256ChecksumUtil;

    public SpaceItemWriter(RetrievalSource retrievalSource, File contentDir,
                           OutputWriter outputWriter, BufferedWriter md5Writer,
                           BufferedWriter sha256Writer) {
        this.retrievalSource = retrievalSource;
        this.contentDir = contentDir;
        this.outputWriter = outputWriter;
        this.md5Witer = md5Writer;
        this.sha256Writer = sha256Writer;
        this.sha256ChecksumUtil =
            new ChecksumUtil(ChecksumUtil.Algorithm.SHA_256);
    }

    public void write(List<? extends ContentItem> items) throws IOException {
        for(ContentItem contentItem: items) {
            LOGGER.debug("writing: {}", contentItem.getContentId());

            File dataDir = new File(contentDir, "data");
            RetrievalWorker retrievalWorker =
                new RetrievalWorker(contentItem, retrievalSource, dataDir,
                                    true, outputWriter, false, true);
            Map<String,String> props = retrievalWorker.retrieveFile();
            File localFile = retrievalWorker.getLocalFile();

            String md5Checksum = props.get(ContentStore.CONTENT_CHECKSUM);
            if(localFile.exists() && md5Checksum != null) {
                // write MD5 checksum to MD5 manifest
                synchronized (md5Witer) {
                    md5Witer.write(md5Checksum + "  data/" +
                                       contentItem.getContentId() + "\n");
                }

                // write SHA-256 checksum to SHA-256 manifest
                String sha256Checksum =
                    sha256ChecksumUtil.generateChecksum(localFile);
                synchronized (sha256Writer) {
                    sha256Writer.write(sha256Checksum + "  data/" +
                                           contentItem.getContentId() + "\n");
                }
            } else {
                // throw Exception???

            }

        }
    }

    public ExitStatus afterStep(StepExecution stepExecution) {
        LOGGER.debug("Step complete with status: {}", stepExecution.getExitStatus());
        try {
            md5Witer.close();
            sha256Writer.close();
        } catch (IOException ioe) {
            LOGGER.error("Error closing manifest BufferedWriter: ", ioe);
        }
        return stepExecution.getExitStatus();
    }

    public void beforeStep(StepExecution stepExecution) {

    }
}

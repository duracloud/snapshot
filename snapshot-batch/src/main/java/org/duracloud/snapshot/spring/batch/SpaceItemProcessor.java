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
import org.duracloud.retrieval.mgmt.OutputWriter;
import org.duracloud.retrieval.mgmt.RetrievalWorker;
import org.duracloud.retrieval.source.RetrievalSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;

import java.io.File;
import java.util.Map;

/**
 * @author Erik Paulsson
 *         Date: 1/31/14
 */
public class SpaceItemProcessor
    implements ItemProcessor<ContentItem, File> {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(SpaceItemProcessor.class);

    private RetrievalSource retrievalSource;
    private File contentDir;
    private OutputWriter outputWriter;

    public SpaceItemProcessor(RetrievalSource retrievalSource, File contentDir,
                              OutputWriter outputWriter) {
        this.retrievalSource = retrievalSource;
        this.contentDir = contentDir;
        this.outputWriter = outputWriter;
    }

    public File process(ContentItem contentItem) {
        RetrievalWorker retrievalWorker = new RetrievalWorker(contentItem,
                                                              retrievalSource,
                                                              contentDir,
                                                              false,
                                                              outputWriter,
                                                              true,
                                                              true);
        LOGGER.debug("contentItem: {}", contentItem.getContentId());
        Map<String,String> props = retrievalWorker.retrieveFile();
        File localFile = retrievalWorker.getLocalFile();

        String checksum = props.get(ContentStore.CONTENT_CHECKSUM);
        if(localFile.exists() && checksum != null) {
            // write MD5 checksum to MD5 manifest

        } else {
            // throw Exception???
            localFile = null;
        }
        return localFile;
    }
}

/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.manager.spring.batch;

import org.duracloud.common.model.ContentItem;
import org.duracloud.retrieval.source.RetrievalSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.NonTransientResourceException;
import org.springframework.batch.item.ParseException;
import org.springframework.batch.item.UnexpectedInputException;

/**
 * @author Erik Paulsson
 *         Date: 1/31/14
 */
public class SpaceItemReader implements ItemReader<ContentItem> {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(SpaceItemReader.class);

    private RetrievalSource retrievalSource;

    public SpaceItemReader(RetrievalSource retrievalSource) {
        this.retrievalSource = retrievalSource;
    }

    @Override
    public ContentItem read() throws Exception, UnexpectedInputException, ParseException,
                                     NonTransientResourceException {
        ContentItem contentItem = retrievalSource.getNextContentItem();
        if(contentItem != null) {
            LOGGER.debug("contentItem: {}", contentItem.getContentId());
        } else {
            LOGGER.debug("contentItem is null");
        }
        return contentItem;
    }
}

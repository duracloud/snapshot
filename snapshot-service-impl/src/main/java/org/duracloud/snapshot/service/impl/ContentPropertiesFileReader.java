/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.NonTransientResourceException;
import org.springframework.batch.item.ParseException;
import org.springframework.batch.item.UnexpectedInputException;

/**
 * @author Daniel Bernstein
 * Date: Jul 16, 2014
 */
public class ContentPropertiesFileReader extends StepExecutionSupport implements ItemReader<ContentProperties> {
    private final Logger log =
        LoggerFactory.getLogger(ContentPropertiesFileReader.class);

    private File propertiesFile;

    private JsonParser jParser;

    public ContentPropertiesFileReader(File propertiesFile) {
        this.propertiesFile = propertiesFile;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.springframework.batch.item.ItemReader#read()
     */
    @Override
    public synchronized ContentProperties read()
        throws Exception, UnexpectedInputException, ParseException, NonTransientResourceException {

        if (jParser == null) {
            JsonFactory jfactory = new JsonFactory();
            jParser = jfactory.createJsonParser(this.propertiesFile);
            jParser.nextToken(); //skips the first [

            //skip properties already read.
            int itemsRead = (int) getItemsRead();
            for (int i = 0; i < itemsRead; i++) {
                doRead();
            }
        }

        return doRead();
    }

    /**
     * @return
     * @throws IOException
     */
    private ContentProperties doRead() throws IOException {
        //once parser is closed, always return null.
        if (jParser.isClosed()) {
            return null;
        }

        try {
            while (jParser.nextToken() != JsonToken.END_ARRAY &&
                   jParser.getText() != null) {
                return parseNext(jParser);
            }
        } catch (Exception e) {
            String message = "Error parsing content properties file: " + e.getMessage();
            log.error(message, e);
            throw new ParseException(message, e);
        }

        jParser.close();
        return null;
    }

    /**
     * @param jParser
     * @return
     */
    private synchronized ContentProperties parseNext(JsonParser jParser)
        throws Exception {
        String contentId = null;
        Map<String, String> properties = new HashMap<>();
        while (jParser.nextToken() != JsonToken.END_OBJECT &&
               jParser.getText() != null) {

            contentId = jParser.getCurrentName();
            jParser.nextToken();  // :
            jParser.nextToken();  // {

            while (jParser.nextToken() != JsonToken.END_OBJECT &&
                   jParser.getText() != null) {
                String key = jParser.getCurrentName();
                String value = jParser.getText();
                properties.put(key, value);
            }
        }

        return new ContentProperties(contentId, properties);
    }

}

/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import java.util.Map;

import org.apache.commons.lang3.builder.ToStringBuilder;
/**
 * 
 * @author Daniel Bernstein
 *         Date: Aug 26, 2014
 */
public class ContentProperties {
    private String contentId;
    private Map<String, String> properties;

    /**
     * @param contentId
     * @param properties
     */
    public ContentProperties(
        String contentId, Map<String, String> properties) {
        super();
        this.contentId = contentId;
        this.properties = properties;
    }

    /**
     * @return the contentId
     */
    public String getContentId() {
        return contentId;
    }

    /**
     * @return the properties
     */
    public Map<String, String> getProperties() {
        return properties;
    }
    
    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
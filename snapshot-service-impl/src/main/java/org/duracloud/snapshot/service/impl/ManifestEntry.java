/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

/**
 * @author Daniel Bernstein
 *         Date: Jul 28, 2015
 */
public class ManifestEntry {
    private String checksum;
    private String contentId;
    /**
     * @param checksum
     * @param contentId
     */
    public ManifestEntry(String checksum, String contentId) {
        super();
        this.checksum = checksum;
        this.contentId = contentId;
    }
    /**
     * @return the checksum
     */
    public String getChecksum() {
        return checksum;
    }
    /**
     * @param checksum the checksum to set
     */
    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }
    /**
     * @return the contentId
     */
    public String getContentId() {
        return contentId;
    }
    /**
     * @param contentId the contentId to set
     */
    public void setContentId(String contentId) {
        this.contentId = contentId;
    }
    
    
}

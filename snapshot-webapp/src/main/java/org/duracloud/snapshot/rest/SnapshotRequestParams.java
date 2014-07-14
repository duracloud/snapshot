/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.rest;

/**
 * @author Daniel Bernstein
 *         Date: Jul 14, 2014
 */
public class SnapshotRequestParams {
    private String host;
    private String port;
    private String storeId;
    private String spaceId;
    private String description;

    /**
     * @param host
     * @param port
     * @param storeId
     * @param spaceId
     */
    public SnapshotRequestParams(
        String host, String port, String storeId, String spaceId, String description) {
        this.host = host;
        this.port = port;
        this.storeId = storeId;
        this.spaceId = spaceId;
        this.description = description;
    }
    
    /**
     * 
     */
    public SnapshotRequestParams() {}

    /**
     * @return the host
     */
    public String getHost() {
        return host;
    }
    /**
     * @param host the host to set
     */
    public void setHost(String host) {
        this.host = host;
    }
    /**
     * @return the port
     */
    public String getPort() {
        return port;
    }
    /**
     * @param port the port to set
     */
    public void setPort(String port) {
        this.port = port;
    }
    /**
     * @return the storeId
     */
    public String getStoreId() {
        return storeId;
    }
    /**
     * @param storeId the storeId to set
     */
    public void setStoreId(String storeId) {
        this.storeId = storeId;
    }
    /**
     * @return the spaceId
     */
    public String getSpaceId() {
        return spaceId;
    }
    /**
     * @param spaceId the spaceId to set
     */
    public void setSpaceId(String spaceId) {
        this.spaceId = spaceId;
    }
    
    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }
}

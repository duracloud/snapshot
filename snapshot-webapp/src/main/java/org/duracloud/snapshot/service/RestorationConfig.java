/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service;

import org.apache.commons.lang.builder.ToStringBuilder;

/**
 * @author Daniel Bernstein
 *         Date: Jul 17, 2014
 */
public class RestorationConfig {
    private String host;
    private int port;
    private String spaceId;
    private String snapshotId;
    private String storeId;
    /**
     * @param host
     * @param port
     * @param storeId
     * @param spaceId
     * @param snapshotId
     */
    public RestorationConfig(
        String host, int port, String storeId, String spaceId, String snapshotId) {
        super();
        this.host = host;
        this.port = port;
        this.storeId = storeId;
        this.spaceId = spaceId;
        this.snapshotId = snapshotId;
    }


    /**
     * @param snapshot the snapshot to set
     */
    public void setSnapshotId(String snapshot) {
        this.snapshotId = snapshot;
    }

    /**
     * @param host the host to set
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * @param port the port to set
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * @param spaceId the spaceId to set
     */
    public void setSpaceId(String spaceId) {
        this.spaceId = spaceId;
    }

    public RestorationConfig() {}
    /**
     * @return the host
     */
    public String getHost() {
        return host;
    }
    /**
     * @return the port
     */
    public int getPort() {
        return port;
    }
    /**
     * @return the spaceId
     */
    public String getSpaceId() {
        return spaceId;
    }
    /**
     * @return the snapshot
     */
    public String getSnapshotId() {
        return snapshotId;
    }
    
    /**
     * @return
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

    
    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

}

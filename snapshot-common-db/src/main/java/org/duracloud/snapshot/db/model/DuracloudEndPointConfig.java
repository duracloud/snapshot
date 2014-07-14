/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.db.model;

import javax.persistence.Embeddable;

/**
 * @author Daniel Bernstein
 *         Date: Jul 21, 2014
 */
@Embeddable
public class DuracloudEndPointConfig {
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
    public int getPort() {
        return port;
    }
    /**
     * @param port the port to set
     */
    public void setPort(int port) {
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
    private String host;
    private int port;
    private String storeId;
    private String spaceId;

}

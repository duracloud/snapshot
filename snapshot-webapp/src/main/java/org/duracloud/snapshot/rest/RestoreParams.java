/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.rest;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.codehaus.jackson.map.annotate.JsonDeserialize;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.duracloud.snapshot.service.RestorationConfig;
import org.hibernate.validator.constraints.NotBlank;

import com.google.common.collect.Sets;

/**
 * @author Daniel Bernstein
 *         Date: Jul 17, 2014
 */
@JsonSerialize
@JsonDeserialize
public class RestoreParams {
    @NotBlank
    private String host;
    @NotBlank
    private String port;
    @NotBlank
    private String spaceId;
    @NotBlank
    private String snapshotId;
    @NotBlank
    private String storeId;

    
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
     * @return the snapshotId
     */
    public String getSnapshotId() {
        return snapshotId;
    }
    /**
     * @param snapshotId the snapshotId to set
     */
    public void setSnapshotId(String snapshotId) {
        this.snapshotId = snapshotId;
    }
    
    /**
     * @return the storeId
     */
    public String getStoreId() {
        return storeId;
    }
    
    
    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    
}
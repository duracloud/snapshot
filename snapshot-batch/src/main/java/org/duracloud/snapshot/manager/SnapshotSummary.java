/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.manager;

/**
 * @author Daniel Bernstein
 *         Date: Jul 14, 2014
 */
public class SnapshotSummary {
 
    private String snapshotId;
    private String description;
    
    
    /**
     * @param snapshotId
     */
    public SnapshotSummary(String snapshotId, String description) {
        super();
        this.snapshotId = snapshotId;
        this.setDescription(description);
    }


    /**
     * 
     */
    public SnapshotSummary() {}

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

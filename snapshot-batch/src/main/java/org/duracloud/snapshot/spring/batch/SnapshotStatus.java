/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.spring.batch;

/**
 * @author Daniel Bernstein
 *         Date: Feb 5, 2014
 */
public class SnapshotStatus {
    private String id;
    private String status;
    
    /**
     * 
     */
    public SnapshotStatus() {
    }

    
    /**
     * @param id
     * @param status
     */
    public SnapshotStatus(String id, String status) {
        super();
        this.id = id;
        this.status = status;
    }


    /**
     * @return the id
     */
    public String getId() {
        return id;
    }
    
    /**
     * @param id the id to set
     */
    public void setId(String id) {
        this.id = id;
    }
    
    /**
     * @return the status
     */
    public String getStatus() {
        return status;
    }
    
    /**
     * @param status the status to set
     */
    public void setStatus(String status) {
        this.status = status;
    }
}

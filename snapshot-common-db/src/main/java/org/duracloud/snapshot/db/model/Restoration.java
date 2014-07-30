/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.db.model;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

import org.duracloud.snapshot.dto.RestoreStatus;

/**
 * @author Daniel Bernstein
 *         Date: Jul 21, 2014
 */
@Entity
//@Table
public class Restoration extends BaseEntity {

    @ManyToOne(optional=true,targetEntity=Snapshot.class)
    @JoinColumn(name="snapshot_id", columnDefinition = "bigint(20)")
    private Snapshot snapshot;

    @Embedded
    private DuracloudEndPointConfig destination;
    private Date startDate;
    private Date endDate;
    @Enumerated(EnumType.STRING)
    private RestoreStatus status;
    @Column(length=512)
    private String statusText;
    
    private String userEmail;

    
    /**
     * @return the snapshot
     */
    public Snapshot getSnapshot() {
        return snapshot;
    }
    /**
     * @param snapshot the snapshot to set
     */
    public void setSnapshot(Snapshot snapshot) {
        this.snapshot = snapshot;
    }
    /**
     * @return the destination
     */
    public DuracloudEndPointConfig getDestination() {
        return destination;
    }
    /**
     * @param destination the destination to set
     */
    public void setDestination(DuracloudEndPointConfig destination) {
        this.destination = destination;
    }
    /**
     * @return the startDate
     */
    public Date getStartDate() {
        return startDate;
    }
    /**
     * @param startDate the startDate to set
     */
    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }
    /**
     * @return the endDate
     */
    public Date getEndDate() {
        return endDate;
    }
    /**
     * @param endDate the endDate to set
     */
    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }
    /**
     * @return the status
     */
    public RestoreStatus getStatus() {
        return status;
    }
    /**
     * @param status the status to set
     */
    public void setStatus(RestoreStatus status) {
        this.status = status;
    }

    /**
     * @return the statusText
     */
    public String getStatusText() {
        return statusText;
    }
    /**
     * @param statusText the statusText to set
     */
    public void setStatusText(String statusText) {
        this.statusText = statusText;
    }
    /**
     * @return the userEmail
     */
    public String getUserEmail() {
        return userEmail;
    }
    /**
     * @param userEmail the userEmail to set
     */
    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }
  

}

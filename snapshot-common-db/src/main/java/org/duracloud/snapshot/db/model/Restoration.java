/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.db.model;

import java.util.Comparator;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

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
    private RestorationStatus status;
    @Column(length=512)
    private String memo;
    
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
    public RestorationStatus getStatus() {
        return status;
    }
    /**
     * @param status the status to set
     */
    public void setStatus(RestorationStatus status) {
        this.status = status;
    }
    /**
     * @return the memo
     */
    public String getMemo() {
        return memo;
    }
    /**
     * @param memo the memo to set
     */
    public void setMemo(String memo) {
        this.memo = memo;
    }
  

}

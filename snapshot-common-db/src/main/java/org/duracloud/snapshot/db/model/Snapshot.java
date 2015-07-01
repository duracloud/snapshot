/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.db.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.duracloud.snapshot.dto.SnapshotStatus;

/**
 * @author Daniel Bernstein
 *         Date: Jul 21, 2014
 */
@Entity
@Table (name="snapshot", uniqueConstraints={@UniqueConstraint(columnNames={"name"})})
public class Snapshot extends BaseEntity implements Comparator<Snapshot>{
    
    @Column(name="name", nullable=false, length = 256)
    private String name;
    @Column(name="description", nullable=true, length = 512)
    private String description;
    private Date snapshotDate;
    @Embedded
    private DuracloudEndPointConfig source;
    private Date startDate;
    private Date endDate;
    @Enumerated(EnumType.STRING)
    private SnapshotStatus status;
    @Column(nullable=true, length = 512)
    private String statusText;
    private String userEmail;
    private Long totalSizeInBytes = 0l;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
          name="snapshot_alternate_ids",
          joinColumns=@JoinColumn(name="snapshot_id", columnDefinition="bigint(20)", nullable=false)
    )
    @Column(name="snapshot_alternate_id")
    private List<String> snapshotAlternateIds;

    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, mappedBy = "snapshot")
    @OrderBy("metadataDate DESC")
    private List<SnapshotMetadata> snapshotMetadata;

    /**
     * @return the snapshotName
     */
    public String getName() {
        return name;
    }
    /**
     * @param name the snapshotName to set
     */
    public void setName(String name) {
        this.name = name;
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
    /**
     * @return the source
     */
    public DuracloudEndPointConfig getSource() {
        return source;
    }
    /**
     * @param source the source to set
     */
    public void setSource(DuracloudEndPointConfig source) {
        this.source = source;
    }
    /**
     * @return the snapshotDate
     */
    public Date getSnapshotDate() {
        return snapshotDate;
    }
    /**
     * @param snapshotDate the snapshotDate to set
     */
    public void setSnapshotDate(Date snapshotDate) {
        this.snapshotDate = snapshotDate;
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
    public SnapshotStatus getStatus() {
        return status;
    }
    /**
     * @param status the status to set
     */
    public void setStatus(SnapshotStatus status) {
        this.status = status;
    }

    /**
     * @return list of metadata entries for a snapshot
     */
    public List<SnapshotMetadata> getSnapshotMetadata() {
		return snapshotMetadata;
	}

    /**
     * @param snapshotMetadata - list of metadata entries for a snapshot
     */
    public void setSnapshotMetadata(List<SnapshotMetadata> snapshotMetadata) {
		this.snapshotMetadata = snapshotMetadata;
	}

    /**
     * @return list of alternate id's for a snapshot
     */
    public List<String> getSnapshotAlternateIds() {
		return snapshotAlternateIds;
	}

    /**
     * @param snapshotAlternateIds
     * @throws JSONException
     */
    public void setSnapshotAlternateIds(List<String> snapshotAlternateIds) {
        if(snapshotAlternateIds != null) {
            // sanity check
            if(snapshotAlternateIds.size() > 0) {
                if(this.snapshotAlternateIds == null) {
                    this.snapshotAlternateIds = new ArrayList<String>();
                }
            }
            // copy all the items from JSONArray to ArrayList
            for (String snapshotAlternateId : snapshotAlternateIds) {
                this.snapshotAlternateIds.add(snapshotAlternateId);
            }
        }
    }

    /* (non-Javadoc)
     * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
     */
    @Override
    public int compare(Snapshot o1, Snapshot o2) {
        return o1.name.compareTo(o2.name);
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
    /**
     * @return the totalSizeInBytes
     */
    public Long getTotalSizeInBytes() {
        return totalSizeInBytes;
    }
    /**
     * @param totalSizeInBytes the totalSizeInBytes to set
     */
    public void setTotalSizeInBytes(Long totalSizeInBytes) {
        this.totalSizeInBytes = totalSizeInBytes;
    }
    
    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}

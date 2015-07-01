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
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * @author Gad Krumholz
 *         Date: May 19, 2015
 */
@Entity
@Table (name="snapshot_metadata", uniqueConstraints=@UniqueConstraint(columnNames={"snapshot_id","metadataDate" }))
public class SnapshotMetadata extends BaseEntity implements Comparator<SnapshotMetadata> {
    
	private Date metadataDate = new Date();
	
    @ManyToOne(optional=false,targetEntity=Snapshot.class)
    @JoinColumn(name="snapshot_id", columnDefinition = "bigint(20)", nullable=false)
    private Snapshot snapshot;
    
    @Column(length=1024)
    private String metadata;
    
    /**
     * @return the metadataDate
     */
    public Date getMetadataDate() {
        return metadataDate;
    }
    
    /**
     * @param metadataDate - the metadataDate to set
     */
    public void setMetadataDate(Date metadataDate) {
        this.metadataDate = metadataDate;
    }
    
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
     * @return the metadata
     */
    public String getMetadata() {
        return metadata;
    }

    /**
     * @param metadata the metadata to set
     */
    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }
    
    /* (non-Javadoc)
     * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
     */
    @Override
    public int compare(SnapshotMetadata o1, SnapshotMetadata o2) {
        return o1.metadata.compareTo(o2.metadata);
    }
        
    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}

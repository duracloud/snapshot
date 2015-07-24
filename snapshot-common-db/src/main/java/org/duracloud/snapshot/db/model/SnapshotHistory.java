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
@Table (name="snapshot_history", uniqueConstraints=@UniqueConstraint(columnNames={"snapshot_id","historyDate" }))
public class SnapshotHistory extends BaseEntity implements Comparator<SnapshotHistory> {
    
	private Date historyDate = new Date();
	
    @ManyToOne(optional=false,targetEntity=Snapshot.class)
    @JoinColumn(name="snapshot_id", columnDefinition = "bigint(20)", nullable=false)
    private Snapshot snapshot;
    
    @Column(length=1024)
    private String history;
    
    /**
     * @return the historyDate
     */
    public Date getHistoryDate() {
        return historyDate;
    }
    
    /**
     * @param historyDate - the historyDate to set
     */
    public void setHistoryDate(Date historyDate) {
        this.historyDate = historyDate;
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
     * @return the history
     */
    public String getHistory() {
        return history;
    }

    /**
     * @param history the history to set
     */
    public void setHistory(String history) {
        this.history = history;
    }
    
    /* (non-Javadoc)
     * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
     */
    @Override
    public int compare(SnapshotHistory o1, SnapshotHistory o2) {
        return o1.history.compareTo(o2.history);
    }
        
    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}

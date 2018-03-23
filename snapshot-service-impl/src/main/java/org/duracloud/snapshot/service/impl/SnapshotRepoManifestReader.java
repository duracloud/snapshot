/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import org.duracloud.common.collection.StreamingIterator;
import org.duracloud.common.collection.jpa.JpaIteratorSource;
import org.duracloud.snapshot.db.model.SnapshotContentItem;
import org.duracloud.snapshot.db.repo.SnapshotContentItemRepo;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.NonTransientResourceException;
import org.springframework.batch.item.ParseException;
import org.springframework.batch.item.UnexpectedInputException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * @author Daniel Bernstein
 * Date: Jul 28, 2015
 */
public class SnapshotRepoManifestReader extends StepExecutionSupport implements ItemReader<SnapshotContentItem> {

    private SnapshotContentItemRepo repo;
    private StreamingIterator<SnapshotContentItem> items;
    private String snapshotName;

    public SnapshotRepoManifestReader(SnapshotContentItemRepo repo, String snapshotName) {
        this.repo = repo;
        this.snapshotName = snapshotName;
    }

    /* (non-Javadoc)
     * @see org.springframework.batch.item.ItemReader#read()
     */
    @Override
    public synchronized SnapshotContentItem read()
        throws Exception, UnexpectedInputException, ParseException, NonTransientResourceException {

        if (this.items == null) {
            this.items =
                new StreamingIterator<>(new JpaIteratorSource<SnapshotContentItemRepo, SnapshotContentItem>(repo) {
                    @Override
                    protected Page<SnapshotContentItem> getNextPage(Pageable pageable, SnapshotContentItemRepo repo) {
                        return repo.findBySnapshotName(snapshotName, pageable);
                    }
                });
            skipLinesAlreadyRead(this.items);
        }
        return this.items.hasNext() ? this.items.next() : null;
    }

}

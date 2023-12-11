/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.db.repo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Date;
import java.util.List;

import org.duracloud.snapshot.db.JpaIntegrationTestBase;
import org.duracloud.snapshot.db.model.DuracloudEndPointConfig;
import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.dto.SnapshotStatus;
import org.junit.Test;

/**
 * @author Daniel Bernstein
 */
public class SnapshotRepoTest extends JpaIntegrationTestBase {

    @Test
    public void testFindByStatusOrderBySnapshotDateAsc() {
        SnapshotRepo repo = context.getBean(SnapshotRepo.class);
        long time = System.currentTimeMillis();
        //use unordered offset to ensure that they are entered out of sequence
        int[] offsets = {1, 10, 2, 9, 3, 8, 4, 7, 5, 6};

        for (int i = 0; i < offsets.length; i++) {
            Snapshot s = new Snapshot();
            s.setDescription("test");
            s.setSnapshotDate(new Date(time - (offsets[i] * 1000)));
            s.setName("test-" + i);
            s.setModified(new Date(time - i));
            final DuracloudEndPointConfig source = new DuracloudEndPointConfig();
            s.setSource(source);
            s.setStatus(SnapshotStatus.TRANSFERRING_FROM_DURACLOUD);
            repo.save(s);
        }

        final List<Snapshot> list = repo.findByStatusOrderBySnapshotDateAsc(SnapshotStatus.TRANSFERRING_FROM_DURACLOUD);

        assertEquals(offsets.length, list.size());

        Date date = null;

        int count = 0;
        for (Snapshot s : list) {
            if (date != null) {
                assertTrue(s.getSnapshotDate().after(date));
                count++;
            }
            date = s.getSnapshotDate();
        }
        assertEquals(offsets.length - 1, count);
    }
}

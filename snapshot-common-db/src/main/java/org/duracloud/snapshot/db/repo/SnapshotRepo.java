/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.db.repo;

import java.util.List;

import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.dto.SnapshotStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * @author Daniel Bernstein
 * Date: Jul 21, 2014
 */
@Repository(value = "snapshotRepo")
public interface SnapshotRepo extends JpaRepository<Snapshot, Long> {

    /**
     * @return all snapshots
     */
    public List<Snapshot> findAll();

    /**
     * @param host where snapshot originated
     * @return all snapshots with the given host
     */
    public List<Snapshot> findBySourceHost(String host);

    /**
     * @param storeId storage provider ID
     * @return all snapshots with the given store ID
     */
    public List<Snapshot> findBySourceStoreId(String storeId);

    /**
     * @param host    where snapshot originated
     * @param storeId storage provider ID
     * @return all snapshots with the given host and store ID
     */
    public List<Snapshot> findBySourceHostAndSourceStoreId(String host, String storeId);

    /**
     * @param status current snapshot status
     * @return all snapshots with the given status
     */
    public List<Snapshot> findByStatusOrderBySnapshotDateAsc(SnapshotStatus status);

    /**
     * @param host   where snapshot originated
     * @param status current snapshot status
     * @return all snapshots with the given host and status
     */
    public List<Snapshot> findBySourceHostAndStatus(String host, SnapshotStatus status);

    /**
     * @param storeId storage provider ID
     * @param status  current snapshot status
     * @return all snapshots with the given store ID and status
     */
    public List<Snapshot> findBySourceStoreIdAndStatus(String storeId,
                                                       SnapshotStatus status);

    /**
     * @param host    where snapshot originated
     * @param storeId storage provider ID
     * @param status  current snapshot status
     * @return all snapshots with the given host, store ID, and status
     */
    public List<Snapshot> findBySourceHostAndSourceStoreIdAndStatus(String host,
                                                                    String storeId,
                                                                    SnapshotStatus status);

    /**
     * @param snapshotId ID of snapshot
     * @return snapshot with the given ID
     */
    public Snapshot findByName(String snapshotId);

    /**
     * @param alternateId alternate snapshot ID (i.e. bag ID)
     * @return snapshot with the given alternate ID
     */
    public Snapshot findBySnapshotAlternateIds(String alternateId);

    /**
     * @return count of snapshots
     */
    public long count();

    /**
     * @param host where snapshot originated
     * @return count of snapshots with the given host
     */
    public long countBySourceHost(String host);

    /**
     * @param storeId storage provider ID
     * @return count of snapshots with the given store ID
     */
    public long countBySourceStoreId(String storeId);

    /**
     * @param host    where snapshot originated
     * @param storeId storage provider ID
     * @return count of snapshots with the given host and store ID
     */
    public long countBySourceHostAndSourceStoreId(String host, String storeId);

    /**
     * @param status current snapshot status
     * @return count of snapshots with the given status
     */
    public long countByStatusOrderBySnapshotDateAsc(SnapshotStatus status);

    /**
     * @param host   where snapshot originated
     * @param status current snapshot status
     * @return count of snapshots with the given host and status
     */
    public long countBySourceHostAndStatus(String host, SnapshotStatus status);

    /**
     * @param storeId storage provider ID
     * @param status  current snapshot status
     * @return count of snapshots with the given store ID and status
     */
    public long countBySourceStoreIdAndStatus(String storeId,
                                                       SnapshotStatus status);

    /**
     * @param host    where snapshot originated
     * @param storeId storage provider ID
     * @param status  current snapshot status
     * @return count of snapshots with the given host, store ID, and status
     */
    public long countBySourceHostAndSourceStoreIdAndStatus(String host,
                                                                    String storeId,
                                                                    SnapshotStatus status);

    /**
     * @param snapshotId ID of snapshot
     */
    public void deleteByName(String snapshotId);
}

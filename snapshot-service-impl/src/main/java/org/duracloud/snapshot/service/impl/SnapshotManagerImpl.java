/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.service.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.duracloud.client.ContentStore;
import org.duracloud.client.task.SnapshotTaskClient;
import org.duracloud.common.constant.Constants;
import org.duracloud.common.notification.NotificationManager;
import org.duracloud.common.notification.NotificationType;
import org.duracloud.common.retry.Retriable;
import org.duracloud.common.retry.Retrier;
import org.duracloud.common.util.ChecksumUtil;
import org.duracloud.common.util.ChecksumUtil.Algorithm;
import org.duracloud.common.util.IOUtil;
import org.duracloud.error.ContentStoreException;
import org.duracloud.error.NotFoundException;
import org.duracloud.snapshot.SnapshotException;
import org.duracloud.snapshot.SnapshotNotFoundException;
import org.duracloud.snapshot.common.SnapshotServiceConstants;
import org.duracloud.snapshot.db.ContentDirUtils;
import org.duracloud.snapshot.db.model.DuracloudEndPointConfig;
import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.db.model.SnapshotContentItem;
import org.duracloud.snapshot.db.model.SnapshotHistory;
import org.duracloud.snapshot.db.repo.SnapshotContentItemRepo;
import org.duracloud.snapshot.db.repo.SnapshotRepo;
import org.duracloud.snapshot.dto.SnapshotStatus;
import org.duracloud.snapshot.dto.task.CompleteSnapshotTaskResult;
import org.duracloud.snapshot.service.AlternateIdAlreadyExistsException;
import org.duracloud.snapshot.service.BridgeConfiguration;
import org.duracloud.snapshot.service.EventLog;
import org.duracloud.snapshot.service.SnapshotManager;
import org.duracloud.snapshot.service.SnapshotManagerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Daniel Bernstein Date: Jul 31, 2014
 */
@Component
public class SnapshotManagerImpl implements SnapshotManager {
    public static final int MAX_DAYS_IN_CLEANUP = 3;

    private static Logger log = LoggerFactory.getLogger(SnapshotManagerImpl.class);

    protected static String[] METADATA_FILENAMES = {Constants.SNAPSHOT_PROPS_FILENAME,
                                                    SnapshotServiceConstants.CONTENT_PROPERTIES_JSON_FILENAME,
                                                    SnapshotServiceConstants.MANIFEST_MD5_TXT_FILE_NAME,
                                                    SnapshotServiceConstants.MANIFEST_SHA256_TXT_FILE_NAME};

    private Map<String, Date> lastCleanupFailureNotificationBySnapshot = new HashMap<String, Date>();

    //by default 1 day
    private long secondsBetweenCleanupFailureNotifications = 86400;

    //NOTE: auto wiring at the field level rather than in the constructor seems to be necessary
    //      when annotating methods with @Transactional.
    @Autowired
    private SnapshotContentItemRepo snapshotContentItemRepo;

    @Autowired
    private SnapshotRepo snapshotRepo;

    @Autowired
    private NotificationManager notificationManager;

    @Autowired
    private SnapshotTaskClientHelper snapshotTaskClientHelper;

    @Autowired
    private StoreClientHelper storeClientHelper;

    @Autowired
    private BridgeConfiguration bridgeConfig;

    @Autowired
    private EventLog eventLog;

    public SnapshotManagerImpl() {
    }

    /**
     * For testing purposes only
     * @param snapshotContentItemRepo the snapshotContentItemRepo to set
     */
    protected void setSnapshotContentItemRepo(SnapshotContentItemRepo snapshotContentItemRepo) {
        this.snapshotContentItemRepo = snapshotContentItemRepo;
    }

    /**
     * For testing purposes only
     * @param snapshotRepo the snapshotRepo to set
     */
    protected void setSnapshotRepo(SnapshotRepo snapshotRepo) {
        this.snapshotRepo = snapshotRepo;
    }

    /**
     * For testing purposes only
     * @param notificationManager the notificationManager to set
     */
    protected void setNotificationManager(NotificationManager notificationManager) {
        this.notificationManager = notificationManager;
    }

    /**
     * For testing purposes only
     * @param snapshotTaskClientHelper the snapshotTaskClientHelper to set
     */
    protected void setSnapshotTaskClientHelper(SnapshotTaskClientHelper snapshotTaskClientHelper) {
        this.snapshotTaskClientHelper = snapshotTaskClientHelper;
    }

    /**
     * @param bridgeConfig the bridgeConfig to set
     */
    protected void setBridgeConfig(BridgeConfiguration bridgeConfig) {
        this.bridgeConfig = bridgeConfig;
    }

    /**
     * For testing purposes only
     * @param eventLog the event log
     */
    protected void setEventLog(final EventLog eventLog) {
        this.eventLog = eventLog;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.duracloud.snapshot.service.SnapshotManager#addContentItem(
     *  org.duracloud.snapshot.db.model.Snapshot, java.lang.String, java.util.Map)
     */
    @Override
    @Transactional
    public void addContentItem(Snapshot snapshot,
                               String contentId,
                               Map<String, String> props)
        throws SnapshotException {

        String contentIdHash = createChecksumGenerator().generateChecksum(contentId);
        try {
            if (this.snapshotContentItemRepo.findBySnapshotAndContentIdHash(snapshot, contentIdHash) != null) {
                return;
            }

            SnapshotContentItem item = new SnapshotContentItem();
            item.setContentId(contentId);
            item.setSnapshot(snapshot);
            item.setContentIdHash(contentIdHash);
            String propString = PropertiesSerializer.serialize(props);
            item.setMetadata(propString);
            this.snapshotContentItemRepo.save(item);
        } catch (Exception ex) {
            throw new SnapshotException("failed to add content item: " + ex.getMessage(), ex);
        }
    }

    @Override
    @Transactional
    public Snapshot addAlternateSnapshotIds(Snapshot snapshot, List<String> alternateIds)
        throws AlternateIdAlreadyExistsException {
        snapshot = this.snapshotRepo.findOne(snapshot.getId());
        for (String altId : alternateIds) {
            Snapshot altSnapshot = this.snapshotRepo.findBySnapshotAlternateIds(altId);
            if (altSnapshot != null && !altSnapshot.getName().equals(snapshot.getName())) {
                throw new AlternateIdAlreadyExistsException("The alternate snapshot id ("
                                                            + altId + ") already exists in another snapshot (" +
                                                            altSnapshot.getName() + ")");
            }
        }
        snapshot.addSnapshotAlternateIds(alternateIds);
        return this.snapshotRepo.saveAndFlush(snapshot);
    }

    /* (non-Javadoc)
     * @see org.duracloud.snapshot.service.SnapshotManager#transferToStorageComplete(java.lang.String)
     */
    @Override
    @Transactional
    public Snapshot transferToStorageComplete(String snapshotId)
        throws SnapshotException {
        try {
            Snapshot snapshot = getSnapshot(snapshotId);
            snapshot = changeSnapshotStatus(snapshot, SnapshotStatus.CLEANING_UP, "");

            File snapshotDir = new File(
                ContentDirUtils.getDestinationPath(snapshot.getName(),
                                                   BridgeConfiguration.getContentRootDir()));

            File zipFile = zipMetadata(snapshotId, snapshotDir);

            DuracloudEndPointConfig source = snapshot.getSource();

            ContentStore store = getContentStore(source);

            ensureMetadataSpaceExists(store);

            String zipChecksum = createChecksumGenerator().generateChecksum(zipFile);

            try {
                new Retrier(4, 1000, 2).execute(new Retriable() {
                    public Object retry() throws Exception {
                        try (FileInputStream zipStream = new FileInputStream(zipFile)) {
                            return store.addContent(Constants.SNAPSHOT_METADATA_SPACE,
                                                    zipFile.getName(),
                                                    zipStream,
                                                    zipFile.length(),
                                                    "application/zip",
                                                    zipChecksum,
                                                    null);
                        }
                    }
                });
            } catch (Exception ex) {
                log.error("failed to upload snapshot zip ("
                          + zipFile.getAbsolutePath() + ") to duracloud: " + ex.getMessage(), ex);
                throw new Exception(ex);
            } finally {
                zipFile.delete();
            }

            FileUtils.deleteDirectory(snapshotDir);

            String spaceId = source.getSpaceId();
            // Call DuraCloud to clean up snapshot
            getSnapshotTaskClient(source).cleanupSnapshot(spaceId);
            log.info("successfully initiated snapshot cleanup on DuraCloud for snapshotId = "
                     + snapshotId + "; spaceId = " + spaceId);

            return snapshot;
        } catch (Exception e) {
            String message = "failed to initiate snapshot clean up: " + e.getMessage();
            log.error(message, e);
            throw new SnapshotManagerException(e.getMessage());
        }
    }

    /**
     * @return
     */
    private ChecksumUtil createChecksumGenerator() {
        return new ChecksumUtil(Algorithm.MD5);
    }

    /* (non-Javadoc)
     * @see org.duracloud.snapshot.service.SnapshotManager#transferError(java.lang.String)
     */
    @Override
    @Transactional
    public Snapshot transferError(String snapshotId, String errorDetails)
        throws SnapshotException {
        try {
            Snapshot snapshot = getSnapshot(snapshotId);

            // Set snapshot state in the db
            snapshot = changeSnapshotStatus(snapshot, SnapshotStatus.ERROR, errorDetails);

            // Send email to duracloud administrators
            String subject = "Snapshot ERROR: " + snapshotId;
            String message = "A snapshot process has been halted and set to the " +
                             "error state.\n\nSnapshot ID: " + snapshotId +
                             "\nReported Error: " + errorDetails;
            String[] recipients = bridgeConfig.getDuracloudEmailAddresses();
            notificationManager.sendNotification(NotificationType.EMAIL,
                                                 subject,
                                                 message,
                                                 recipients);

            log.info("successfully set snapshot " + snapshotId +
                     " into error state based on the following error details: " +
                     errorDetails);

            return snapshot;
        } catch (Exception e) {
            String message = "failed to set snapshot into error state due to: " +
                             e.getMessage();
            log.error(message, e);
            throw new SnapshotManagerException(e.getMessage());
        }
    }

    /**
     * @param store
     */
    private void ensureMetadataSpaceExists(ContentStore store) throws ContentStoreException {
        String spaceId = Constants.SNAPSHOT_METADATA_SPACE;
        try {
            store.getSpace(spaceId, null, 0, null);
        } catch (NotFoundException e) {
            store.createSpace(spaceId);
        }
    }

    /**
     * @param snapshotId
     * @param snapshotDir
     * @return
     * @throws FileNotFoundException
     * @throws IOException
     */
    private File zipMetadata(String snapshotId, File snapshotDir)
        throws FileNotFoundException, IOException {

        File zipFile = new File(snapshotDir, snapshotId + ".zip");

        FileOutputStream fileOutputStream = new FileOutputStream(zipFile);
        ZipOutputStream zipOs = new ZipOutputStream(fileOutputStream);

        for (String file : METADATA_FILENAMES) {
            IOUtil.addFileToZipOutputStream(new File(snapshotDir, file), zipOs);
        }

        zipOs.close();
        return zipFile;
    }

    /**
     * @param source
     * @return
     */
    private ContentStore getContentStore(DuracloudEndPointConfig source) {
        ContentStore store =
            storeClientHelper.create(source,
                                     bridgeConfig.getDuracloudUsername(),
                                     bridgeConfig.getDuracloudPassword());
        return store;
    }

    /**
     * Build the snapshot task client - for communicating with the DuraCloud snapshot
     * provider to perform tasks.
     *
     * @param source DuraCloud connection source
     * @return task client
     */
    private SnapshotTaskClient getSnapshotTaskClient(DuracloudEndPointConfig source) {
        return this.snapshotTaskClientHelper.create(source,
                                                    bridgeConfig.getDuracloudUsername(),
                                                    bridgeConfig.getDuracloudPassword());
    }

    /**
     * @param snapshotId
     * @return
     */
    private Snapshot getSnapshot(String snapshotId) throws SnapshotException {
        Snapshot snapshot = this.snapshotRepo.findByName(snapshotId);
        if (snapshot == null) {
            throw new SnapshotNotFoundException("A snapshot with id "
                                                + snapshotId + " does not exist.");
        }
        return snapshot;
    }

    private Snapshot cleanupComplete(Snapshot snapshot)
        throws SnapshotException {
        snapshot.setEndDate(new Date());
        snapshot = changeSnapshotStatus(snapshot, SnapshotStatus.SNAPSHOT_COMPLETE, "");
        String snapshotId = snapshot.getName();
        String message = "Snapshot complete: " + snapshotId;
        List<String> recipients =
            new ArrayList<>(Arrays.asList(this.bridgeConfig.getDuracloudEmailAddresses()));
        String userEmail = snapshot.getUserEmail();
        if (userEmail != null) {
            recipients.add(userEmail);
        }

        if (recipients.size() > 0) {
            this.notificationManager.sendNotification(NotificationType.EMAIL,
                                                      message,
                                                      message,
                                                      recipients.toArray(new String[0]));
        }

        return snapshot;
    }

    /* (non-Javadoc)
     * @see org.duracloud.snapshot.service.SnapshotManager#finalizeSnapshots()
     */
    @Override
    @Transactional
    public void finalizeSnapshots() {
        log.debug("Running finalize snapshots...");
        List<Snapshot> snapshots =
            this.snapshotRepo.findByStatusOrderBySnapshotDateAsc(SnapshotStatus.CLEANING_UP);
        for (Snapshot snapshot : snapshots) {
            DuracloudEndPointConfig source = snapshot.getSource();
            ContentStore store = getContentStore(source);
            String snapshotId = snapshot.getName();
            try {
                String spaceId = source.getSpaceId();
                Iterator<String> it = store.getSpaceContents(spaceId);
                if (!it.hasNext()) {
                    // Call DuraCloud to complete snapshot
                    log.debug("notifying task provider that snapshot " +
                              "is complete for space " + spaceId);
                    CompleteSnapshotTaskResult result =
                        getSnapshotTaskClient(source).completeSnapshot(spaceId);
                    log.info("snapshot complete call to task provider performed " +
                             "for space " + spaceId + ": result = " + result.getResult());

                    //update snapshot status and notify users
                    cleanupComplete(snapshot);
                } else {

                    //if snapshot has not been in the CLEANING_UP state
                    //for more than three days, send a warning.
                    Calendar c = Calendar.getInstance();
                    int maxDays = MAX_DAYS_IN_CLEANUP;
                    c.add(Calendar.DATE, -1 * maxDays);
                    if (snapshot.getModified().before(c.getTime())) {
                        //only send a warning if a notification has not already been sent
                        //within secondsBetweenCleanupFailureNotifications
                        Date lastNotification = this.lastCleanupFailureNotificationBySnapshot.get(snapshotId);
                        Date nextNotification = new Date();
                        if (lastNotification != null) {
                            nextNotification = new Date(
                                lastNotification.getTime() + (secondsBetweenCleanupFailureNotifications * 1000));
                        }

                        if (nextNotification.getTime() <= System.currentTimeMillis()) {
                            String subject = MessageFormat.format(
                                "Snapshot cleanup has not completed in over {0} days for snapshot: {1}",
                                maxDays, snapshotId);

                            String body = subject + "\n\nSnapshot object=>" + snapshot;

                            String[] recipients = this.bridgeConfig.getDuracloudEmailAddresses();
                            log.warn(body + "  Sending notification to duracloud admins: {} ", recipients);

                            if (recipients.length > 0) {
                                this.notificationManager.sendNotification(NotificationType.EMAIL,
                                                                          subject,
                                                                          body,
                                                                          recipients);
                                this.lastCleanupFailureNotificationBySnapshot.put(snapshotId, new Date());
                            }
                        }

                    }
                }
            } catch (Exception e) {
                log.error("failed to cleanup " + source);
            }
        }
    }

    /* (non-Javadoc)
     * @see org.duracloud.snapshot.service.SnapshotManager#updateHistory()
     */
    @Override
    @Transactional
    public Snapshot updateHistory(Snapshot snapshot, String history) {
        snapshot = this.snapshotRepo.getOne(snapshot.getId());
        SnapshotHistory newHistory = new SnapshotHistory();
        newHistory.setHistory(history);
        newHistory.setSnapshot(snapshot);
        snapshot.getSnapshotHistory().add(newHistory);
        return this.snapshotRepo.save(snapshot);
    }

    /**
     * @param storeClientHelper the storeClientHelper to set
     */
    public void setStoreClientHelper(StoreClientHelper storeClientHelper) {
        this.storeClientHelper = storeClientHelper;
    }

    /* (non-Javadoc)
     * @see org.duracloud.snapshot.service.SnapshotManager#deleteSnapshot(java.lang.String)
     */
    @Override
    @Transactional
    public void deleteSnapshot(String snapshotId) {
        //delete the snapshot
        snapshotContentItemRepo.deleteBySnapshotName(snapshotId);
        snapshotRepo.deleteByName(snapshotId);
        log.info("successfully deleted snapshot: {}", snapshotId);
    }

    /**
     * @param secondsBetweenCleanupFailureNotifications the secondsBetweenCleanupFailureNotifications to set
     */
    public void setSecondsBetweenCleanupFailureNotifications(long secondsBetweenCleanupFailureNotifications) {
        this.secondsBetweenCleanupFailureNotifications = secondsBetweenCleanupFailureNotifications;
    }

    private Snapshot changeSnapshotStatus(Snapshot snapshot,
                                          SnapshotStatus status,
                                          String statusText) {
        snapshot.setStatus(status);
        snapshot.setStatusText(statusText);
        Snapshot savedSnapshot = this.snapshotRepo.saveAndFlush(snapshot);
        eventLog.logSnapshotUpdate(savedSnapshot);
        log.info("Updated status of " + snapshot + " to " + status);
        return savedSnapshot;
    }

}

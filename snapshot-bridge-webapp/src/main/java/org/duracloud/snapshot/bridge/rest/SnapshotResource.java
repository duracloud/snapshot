/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.bridge.rest;

import static org.duracloud.snapshot.common.SnapshotServiceConstants.SNAPSHOT_ACTION_COMPLETED;
import static org.duracloud.snapshot.common.SnapshotServiceConstants.SNAPSHOT_ACTION_INITIATED;
import static org.duracloud.snapshot.common.SnapshotServiceConstants.SNAPSHOT_ACTION_TITLE;
import static org.duracloud.snapshot.common.SnapshotServiceConstants.SNAPSHOT_ALT_IDS_TITLE;
import static org.duracloud.snapshot.common.SnapshotServiceConstants.SNAPSHOT_ID_TITLE;
import static org.duracloud.snapshot.common.SnapshotServiceConstants.SNAPSHOT_USER_TITLE;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.apache.http.HttpStatus;
import org.duracloud.client.ContentStore;
import org.duracloud.common.constant.Constants;
import org.duracloud.error.ContentStoreException;
import org.duracloud.snapshot.EmptySpaceException;
import org.duracloud.snapshot.SnapshotException;
import org.duracloud.snapshot.SnapshotNotFoundException;
import org.duracloud.snapshot.db.model.DuracloudEndPointConfig;
import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.db.model.SnapshotContentItem;
import org.duracloud.snapshot.db.model.SnapshotHistory;
import org.duracloud.snapshot.db.repo.SnapshotContentItemRepo;
import org.duracloud.snapshot.db.repo.SnapshotRepo;
import org.duracloud.snapshot.dto.SnapshotHistoryItem;
import org.duracloud.snapshot.dto.SnapshotStatus;
import org.duracloud.snapshot.dto.SnapshotSummary;
import org.duracloud.snapshot.dto.bridge.CancelSnapshotBridgeResult;
import org.duracloud.snapshot.dto.bridge.CompleteSnapshotBridgeParameters;
import org.duracloud.snapshot.dto.bridge.CompleteSnapshotBridgeResult;
import org.duracloud.snapshot.dto.bridge.CreateSnapshotBridgeParameters;
import org.duracloud.snapshot.dto.bridge.CreateSnapshotBridgeResult;
import org.duracloud.snapshot.dto.bridge.GetSnapshotBridgeResult;
import org.duracloud.snapshot.dto.bridge.GetSnapshotContentBridgeResult;
import org.duracloud.snapshot.dto.bridge.GetSnapshotHistoryBridgeResult;
import org.duracloud.snapshot.dto.bridge.GetSnapshotListBridgeResult;
import org.duracloud.snapshot.dto.bridge.GetSnapshotTotalCountBridgeResult;
import org.duracloud.snapshot.dto.bridge.GetSnapshotTotalFilesBridgeResult;
import org.duracloud.snapshot.dto.bridge.GetSnapshotTotalSizeBridgeResult;
import org.duracloud.snapshot.dto.bridge.GetSnapshotTotalsBridgeResult;
import org.duracloud.snapshot.dto.bridge.RestartSnapshotBridgeResult;
import org.duracloud.snapshot.dto.bridge.SnapshotErrorBridgeParameters;
import org.duracloud.snapshot.dto.bridge.SnapshotErrorBridgeResult;
import org.duracloud.snapshot.dto.bridge.UpdateSnapshotHistoryBridgeParameters;
import org.duracloud.snapshot.dto.bridge.UpdateSnapshotHistoryBridgeResult;
import org.duracloud.snapshot.id.SnapshotIdentifier;
import org.duracloud.snapshot.service.AlternateIdAlreadyExistsException;
import org.duracloud.snapshot.service.EventLog;
import org.duracloud.snapshot.service.SnapshotJobManager;
import org.duracloud.snapshot.service.SnapshotJobManagerConfig;
import org.duracloud.snapshot.service.SnapshotManager;
import org.duracloud.snapshot.service.impl.PropertiesSerializer;
import org.duracloud.snapshot.service.impl.StoreClientHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Defines the REST resource layer for interacting with the Snapshot processing
 * engine.
 *
 * @author Daniel Bernstein Date: Feb 4, 2014
 */
@Component
@Path("/snapshot")
public class SnapshotResource {

    private static Logger log = LoggerFactory.getLogger(SnapshotResource.class);

    @Context
    HttpServletRequest request;

    @Context
    HttpHeaders headers;

    @Context
    UriInfo uriInfo;

    private SnapshotJobManager jobManager;
    private SnapshotManager snapshotManager;

    private SnapshotContentItemRepo snapshotContentItemRepo;
    private SnapshotRepo snapshotRepo;

    private StoreClientHelper storeClientHelper;
    private EventLog eventLog;

    @Autowired
    public SnapshotResource(SnapshotJobManager jobManager,
                            SnapshotManager snapshotManager,
                            SnapshotRepo snapshotRepo,
                            SnapshotContentItemRepo snapshotContentItemRepo,
                            EventLog eventLog,
                            StoreClientHelper storeClientHelper) {
        this.jobManager = jobManager;
        this.snapshotManager = snapshotManager;
        this.snapshotRepo = snapshotRepo;
        this.snapshotContentItemRepo = snapshotContentItemRepo;
        this.eventLog = eventLog;
        this.storeClientHelper = storeClientHelper;
    }

    /**
     * Returns a list of snapshots.
     *
     * @return
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response list(@QueryParam("host") String host,
                         @QueryParam("storeId") String storeId,
                         @QueryParam("status") SnapshotStatus status) {
        try {
            List<Snapshot> snapshots = listSnapshots(host, storeId, status);

            List<SnapshotSummary> summaries = new ArrayList<>(snapshots.size());
            for (Snapshot snapshot : snapshots) {
                summaries.add(createSnapshotSummary(snapshot));
            }

            log.debug("returning {}", snapshots);
            return Response.ok()
                           .entity(new GetSnapshotListBridgeResult(summaries))
                           .build();
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            return Response.serverError()
                           .entity(new ResponseDetails(ex.getMessage()))
                           .build();
        }
    }

    /**
     * @param snapshot
     * @return
     */
    private SnapshotSummary createSnapshotSummary(Snapshot snapshot) {
        DuracloudEndPointConfig source = snapshot.getSource();
        SnapshotSummary summary = new SnapshotSummary(snapshot.getName(),
                                                      snapshot.getStatus(),
                                                      snapshot.getDescription(),
                                                      source.getStoreId(),
                                                      source.getSpaceId());
        return summary;
    }

    /*
     * Returns a list of snapshots. The parameters of host, store ID and status are all
     * considered optional, so depending on which are provided (i.e. not null), the
     * query used will vary.
     */
    protected List<Snapshot> listSnapshots(String host,
                                           String storeId,
                                           SnapshotStatus status) {
        if (null != host) {
            if (null != storeId) {
                if (null != status) { // Host & Store ID & Status
                    return snapshotRepo.findBySourceHostAndSourceStoreIdAndStatus(host, storeId, status);
                } else { // Host & Store ID
                    return snapshotRepo.findBySourceHostAndSourceStoreId(host, storeId);
                }
            } else if (null != status) { // Host & Status
                return snapshotRepo.findBySourceHostAndStatus(host, status);
            } else { // Host
                return snapshotRepo.findBySourceHost(host);
            }
        } else if (null != storeId) {
            if (null != status) { // Store ID & Status
                return snapshotRepo.findBySourceStoreIdAndStatus(storeId, status);
            } else { // Store ID
                return snapshotRepo.findBySourceStoreId(storeId);
            }
        } else if (null != status) { // Status
            return snapshotRepo.findByStatusOrderBySnapshotDateAsc(status);
        } else { // No filters
            return snapshotRepo.findAll();
        }
    }

    /**
     * Returns the total count of snapshots.
     *
     * @return
     */
    @Path("total/count")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response count(@QueryParam("host") String host,
                          @QueryParam("storeId") String storeId,
                          @QueryParam("status") SnapshotStatus status) {
        try {
            long totalCount = getSnapshotsCount(host, storeId, status);

            log.debug("returning {} total count of snapshots", totalCount);
            return Response.ok()
                           .entity(new GetSnapshotTotalCountBridgeResult(totalCount))
                           .build();
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            return Response.serverError()
                           .entity(new ResponseDetails(ex.getMessage()))
                           .build();
        }
    }

    /*
     * Returns a total count of snapshots. The parameters of host, store ID and status are all
     * considered optional, so depending on which are provided (i.e. not null), the
     * query used will vary.
     */
    protected long getSnapshotsCount(String host,
                                     String storeId,
                                     SnapshotStatus status) {
        if (null != host) {
            if (null != storeId) {
                if (null != status) { // Host & Store ID & Status
                    return snapshotRepo.countBySourceHostAndSourceStoreIdAndStatus(host, storeId, status);
                } else { // Host & Store ID
                    return snapshotRepo.countBySourceHostAndSourceStoreId(host, storeId);
                }
            } else if (null != status) { // Host & Status
                return snapshotRepo.countBySourceHostAndStatus(host, status);
            } else { // Host
                return snapshotRepo.countBySourceHost(host);
            }
        } else if (null != storeId) {
            if (null != status) { // Store ID & Status
                return snapshotRepo.countBySourceStoreIdAndStatus(storeId, status);
            } else { // Store ID
                return snapshotRepo.countBySourceStoreId(storeId);
            }
        } else if (null != status) { // Status
            return snapshotRepo.countByStatusOrderBySnapshotDateAsc(status);
        } else { // No filters
            return snapshotRepo.count();
        }
    }

    /**
     * Returns the total size of snapshot contents.
     *
     * @return
     */
    @Path("total/size")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response size(@QueryParam("host") String host,
                         @QueryParam("storeId") String storeId,
                         @QueryParam("status") SnapshotStatus status) {
        try {
            long totalSize = getSnapshotsSize(host, storeId, status);

            log.debug("returning {} total size in bytes of snapshots", totalSize);
            return Response.ok()
                           .entity(new GetSnapshotTotalSizeBridgeResult(totalSize))
                           .build();
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            return Response.serverError()
                           .entity(new ResponseDetails(ex.getMessage()))
                           .build();
        }
    }

    /*
     * Returns a total size of snapshots. The parameters of host, store ID and status are all
     * considered optional, so depending on which are provided (i.e. not null), the
     * query used will vary.
     */
    protected long getSnapshotsSize(String host,
                                    String storeId,
                                    SnapshotStatus status) {
        long totalSizeInBytes = 0L;
        List<Snapshot> snapshots = null;

        if (null != host) {
            if (null != storeId) {
                if (null != status) { // Host & Store ID & Status
                    snapshots = snapshotRepo.findBySourceHostAndSourceStoreIdAndStatus(host, storeId, status);
                } else { // Host & Store ID
                    snapshots = snapshotRepo.findBySourceHostAndSourceStoreId(host, storeId);
                }
            } else if (null != status) { // Host & Status
                snapshots = snapshotRepo.findBySourceHostAndStatus(host, status);
            } else { // Host
                snapshots = snapshotRepo.findBySourceHost(host);
            }
        } else if (null != storeId) {
            if (null != status) { // Store ID & Status
                snapshots = snapshotRepo.findBySourceStoreIdAndStatus(storeId, status);
            } else { // Store ID
                snapshots = snapshotRepo.findBySourceStoreId(storeId);
            }
        } else if (null != status) { // Status
            snapshots = snapshotRepo.findByStatusOrderBySnapshotDateAsc(status);
        } else { // No filters
            snapshots = snapshotRepo.findAll();
        }

        if (null != snapshots) {
            Iterator<Snapshot> snapshotsItr = snapshots.iterator();
            while (snapshotsItr.hasNext()) {
                Snapshot snapshot = snapshotsItr.next();
                totalSizeInBytes += snapshot.getTotalSizeInBytes();
            }
        }

        return totalSizeInBytes;
    }

    /**
     * Returns the total files of snapshot contents.
     *
     * @return
     */
    @Path("total/files")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response files(@QueryParam("host") String host,
                          @QueryParam("storeId") String storeId,
                          @QueryParam("status") SnapshotStatus status) {
        try {
            long totalFiles = getSnapshotsFiles(host, storeId, status);

            log.debug("returning {} total number of files in snapshots", totalFiles);
            return Response.ok()
                           .entity(new GetSnapshotTotalFilesBridgeResult(totalFiles))
                           .build();
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            return Response.serverError()
                           .entity(new ResponseDetails(ex.getMessage()))
                           .build();
        }
    }

    /*
     * Returns a total files of snapshots. The parameters of host, store ID and status are all
     * considered optional, so depending on which are provided (i.e. not null), the
     * query used will vary.
     */
    protected long getSnapshotsFiles(String host,
                                     String storeId,
                                     SnapshotStatus status) {
        long totalFiles = 0L;
        List<Snapshot> snapshots = null;

        if (null != host) {
            if (null != storeId) {
                if (null != status) { // Host & Store ID & Status
                    snapshots = snapshotRepo.findBySourceHostAndSourceStoreIdAndStatus(host, storeId, status);
                } else { // Host & Store ID
                    snapshots = snapshotRepo.findBySourceHostAndSourceStoreId(host, storeId);
                }
            } else if (null != status) { // Host & Status
                snapshots = snapshotRepo.findBySourceHostAndStatus(host, status);
            } else { // Host
                snapshots = snapshotRepo.findBySourceHost(host);
            }
        } else if (null != storeId) {
            if (null != status) { // Store ID & Status
                snapshots = snapshotRepo.findBySourceStoreIdAndStatus(storeId, status);
            } else { // Store ID
                snapshots = snapshotRepo.findBySourceStoreId(storeId);
            }
        } else if (null != status) { // Status
            snapshots = snapshotRepo.findByStatusOrderBySnapshotDateAsc(status);
        } else { // No filters
            snapshots = snapshotRepo.findAll();
        }

        if (null != snapshots) {
            Iterator<Snapshot> snapshotsItr = snapshots.iterator();
            while (snapshotsItr.hasNext()) {
                Snapshot snapshot = snapshotsItr.next();
                totalFiles += snapshotContentItemRepo.countBySnapshotId(snapshot.getId());
            }
        }

        return totalFiles;
    }

    /**
     * Returns the total count, size and files of snapshots
     *
     * @return
     */
    @Path("total")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response total(@QueryParam("host") String host,
                          @QueryParam("storeId") String storeId,
                          @QueryParam("status") SnapshotStatus status) {
        try {
            long totalCount = getSnapshotsCount(host, storeId, status);
            long totalSize = getSnapshotsSize(host, storeId, status);
            long totalFiles = getSnapshotsFiles(host, storeId, status);

            log.debug("returning {} total count of snapshots", totalCount);
            log.debug("returning {} total size in bytes of snapshots", totalSize);
            log.debug("returning {} total number of files in snapshots", totalFiles);
            return Response.ok()
                           .entity(new GetSnapshotTotalsBridgeResult(totalCount, totalSize, totalFiles))
                           .build();
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            return Response.serverError()
                           .entity(new ResponseDetails(ex.getMessage()))
                           .build();
        }
    }

    @Path("{snapshotId}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    /**
     * Returns the status of a snapshot. The fields available in the response will match
     * those in <code>SnapshotStatus</code>.
     * @param snapshotId
     * @return
     */
    public Response getSnapshot(@PathParam("snapshotId") String snapshotId) {
        try {
            Snapshot snapshot = this.snapshotRepo.findByName(snapshotId);
            if (snapshot == null) {
                throw new SnapshotNotFoundException(snapshotId);
            }

            GetSnapshotBridgeResult result = new GetSnapshotBridgeResult();
            DuracloudEndPointConfig source = snapshot.getSource();
            result.setDescription(snapshot.getDescription());
            result.setSnapshotDate(snapshot.getSnapshotDate());
            result.setSnapshotId(snapshot.getName());
            result.setSourceHost(source.getHost());
            result.setSourceSpaceId(source.getSpaceId());
            result.setSourceStoreId(source.getStoreId());
            result.setStatus(snapshot.getStatus());
            result.setMemberId(snapshot.getMemberId());
            result.setTotalSizeInBytes(snapshot.getTotalSizeInBytes());
            result.setContentItemCount(
                snapshotContentItemRepo.countBySnapshotName(snapshotId));
            result.setAlternateIds(snapshot.getSnapshotAlternateIds());

            log.debug("got snapshot:" + result);
            return Response.ok()
                           .entity(result)
                           .build();
        } catch (SnapshotNotFoundException ex) {
            log.error(ex.getMessage(), ex);
            return Response.status(HttpStatus.SC_NOT_FOUND)
                           .entity(new ResponseDetails(ex.getMessage()))
                           .build();
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            return Response.serverError()
                           .entity(new ResponseDetails(ex.getMessage()))
                           .build();
        }
    }

    @Path("{snapshotId}/restart")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response restart(@PathParam("snapshotId") String snapshotId) {
        log.info("attempting restart of snapshot " + snapshotId);

        try {
            Snapshot snapshot = this.snapshotRepo.findByName(snapshotId);
            if (snapshot == null) {
                throw new SnapshotNotFoundException(snapshotId);
            }

            log.debug("snapshot {} found.", snapshot);
            SnapshotStatus status = snapshot.getStatus();

            if (!status.equals(SnapshotStatus.FAILED_TO_TRANSFER_FROM_DURACLOUD)) {
                String message = "Snapshot can only be restarted when it has reached " +
                                 "a failure state. ( snapshot=" + snapshot + ")";
                throw new SnapshotException(message, null);
            }

            snapshot.setEndDate(null);
            snapshot.setStatusText("restarting");
            snapshot.setStatus(SnapshotStatus.INITIALIZED);
            snapshot = this.snapshotRepo.saveAndFlush(snapshot);
            eventLog.logSnapshotUpdate(snapshot);

            SnapshotStatus snapshotStatus = snapshot.getStatus();
            this.jobManager.executeSnapshot(snapshotId);
            String message = MessageFormat.format("successfully restarted snapshot: {0}", snapshotStatus);
            log.info(message);
            RestartSnapshotBridgeResult result =
                new RestartSnapshotBridgeResult(message, snapshotStatus);
            return Response.accepted().entity(result).build();
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            return Response.serverError()
                           .entity(new ResponseDetails(ex.getMessage()))
                           .build();
        }

    }

    @Path("{snapshotId}/cancel")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response cancel(@PathParam("snapshotId") final String snapshotId) throws SnapshotException {
        log.debug("attempting cancellation of snapshot " + snapshotId);

        Snapshot snapshot = this.snapshotRepo.findByName(snapshotId);
        if (snapshot == null) {
            throw new SnapshotNotFoundException(snapshotId);
        }

        log.debug("snapshot {} found.", snapshot);
        SnapshotStatus status = snapshot.getStatus();

        if (status.equals(SnapshotStatus.CLEANING_UP)) {
            String message = "Snapshot cannot be cancelled in the cleaning up phase ( snapshot=" + snapshot + ")";
            return Response.serverError().entity(new ResponseDetails(message)).build();
        }

        jobManager.cancelSnapshot(snapshotId);
        snapshotManager.deleteSnapshot(snapshotId);

        CancelSnapshotBridgeResult result =
            new CancelSnapshotBridgeResult(SnapshotStatus.CANCELLED,
                                           "Cancellation succeeded.  Snapshot and all metadata has been deleted.");
        return Response.ok().entity(result).build();

    }

    @Path("{snapshotId}")
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response create(@PathParam("snapshotId") String snapshotId,
                           CreateSnapshotBridgeParameters params) {
        log.debug("creating snapshot " + snapshotId + "; params = " + params);
        Snapshot snapshot = null;

        try {
            if (this.snapshotRepo.findByName(snapshotId) != null) {
                throw new SnapshotAlreadyExistsException("A snapshot with id " + snapshotId
                                                         + " already exists - please use a different name");
            }

            snapshot = new Snapshot();

            DuracloudEndPointConfig source = new DuracloudEndPointConfig();
            source.setHost(params.getHost());
            source.setPort(Integer.valueOf(params.getPort()));
            source.setSpaceId(params.getSpaceId());
            source.setStoreId(params.getStoreId());

            checkForEmptySpace(source);

            Date now = new Date();
            snapshot.setModified(now);
            snapshot.setStartDate(now);
            Date snapshotDate =
                new Date(SnapshotIdentifier.parseSnapshotId(snapshotId)
                                           .getTimestamp());
            snapshot.setSnapshotDate(snapshotDate);
            snapshot.setName(snapshotId);
            snapshot.setSource(source);
            snapshot.setDescription(params.getDescription());
            snapshot.setStatus(SnapshotStatus.INITIALIZED);
            String userEmail = params.getUserEmail();
            snapshot.setUserEmail(userEmail);
            snapshot.setMemberId(params.getMemberId());
            snapshot = this.snapshotRepo.saveAndFlush(snapshot);
            eventLog.logSnapshotUpdate(snapshot);

            this.jobManager.executeSnapshot(snapshotId);
            CreateSnapshotBridgeResult result =
                new CreateSnapshotBridgeResult(snapshotId, snapshot.getStatus());

            // Add history event
            String history =
                "[{'" + SNAPSHOT_ACTION_TITLE + "':'" + SNAPSHOT_ACTION_INITIATED + "'}," +
                "{'" + SNAPSHOT_USER_TITLE + "':'" + userEmail + "'}," +
                "{'" + SNAPSHOT_ID_TITLE + "':'" + snapshotId + "'}]";
            snapshotManager.updateHistory(snapshot, history);

            log.info("successfully created snapshot: {}", result);
            return Response.created(null).entity(result).build();
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);

            if (snapshot != null && snapshot.getId() != null) {
                log.info("cleaning up post exception...");
                try {
                    log.debug("deleting newly created snapshot...");
                    snapshotRepo.deleteById(snapshot.getId());
                } catch (Exception e) {
                    log.error("failed to cleanup snapshot " + snapshotId + ": " +
                              e.getMessage(), e);
                }
                log.info("cleaning up complete");
            }

            Status status = Status.INTERNAL_SERVER_ERROR;
            if (ex instanceof EmptySpaceException) {
                status = Status.CONFLICT;
            }

            return Response.status(status)
                           .entity(new ResponseDetails(ex.getMessage()))
                           .build();
        }
    }

    private void checkForEmptySpace(DuracloudEndPointConfig source) throws SnapshotException {
        SnapshotJobManagerConfig config = this.jobManager.getConfig();
        ContentStore contentStore =
            storeClientHelper.create(source, config.getDuracloudUsername(),
                                     config.getDuracloudPassword());
        try {
            Iterator<String> contents = contentStore.getSpaceContents(source.getSpaceId());
            if (contents == null || isEmpty(contents)) {
                throw new EmptySpaceException("A snapshot of an empty space may not be taken.");
            }

        } catch (ContentStoreException ex) {
            throw new RuntimeException(ex);
        }
    }

    private boolean isEmpty(Iterator<String> contents) {
        while (contents.hasNext()) {
            if (!Constants.SNAPSHOT_PROPS_FILENAME.equals(contents.next())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Notifies the bridge that the snapshot transfer from the bridge storage to
     * the preservation storage is complete. Also sets a snapshot's alternate id's if they
     * are passed in.
     *
     * @param snapshotId
     * @param params
     * @return
     */
    @Path("{snapshotId}/complete")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response complete(@PathParam("snapshotId") String snapshotId,
                             CompleteSnapshotBridgeParameters params) {
        try {
            Snapshot snapshot = this.snapshotRepo.findByName(snapshotId);

            // parse alternate IDs if provided
            List<String> alternateIds = params.getAlternateIds();
            String altIds = "[]";
            if (alternateIds != null && !alternateIds.isEmpty()) {
                // add alternate IDs to snapshot
                snapshot = this.snapshotManager.addAlternateSnapshotIds(snapshot,
                                                                        alternateIds);
                // build alternate IDs for history
                StringBuilder history = new StringBuilder();
                history.append("[");
                boolean first = true;
                for (String id : alternateIds) {
                    if (!first) {
                        history.append(",");
                    }
                    history.append("'" + id + "'");
                    first = false;
                }
                history.append("]");
                altIds = history.toString();
            }

            // Add history event
            String history =
                "[{'" + SNAPSHOT_ACTION_TITLE + "':'" + SNAPSHOT_ACTION_COMPLETED + "'}," +
                "{'" + SNAPSHOT_ID_TITLE + "':'" + snapshotId + "'}," +
                "{'" + SNAPSHOT_ALT_IDS_TITLE + "':" + altIds + "}]";
            snapshotManager.updateHistory(snapshot, history);

            snapshot = this.snapshotManager.transferToStorageComplete(snapshotId);

            log.info("successfully processed snapshot complete notification: {}", snapshot);

            return Response.ok(null)
                           .entity(new CompleteSnapshotBridgeResult(snapshot.getStatus(),
                                                                    snapshot.getStatusText()))
                           .build();
        } catch (AlternateIdAlreadyExistsException ex) {
            log.warn(ex.getMessage());
            return Response.status(HttpStatus.SC_BAD_REQUEST)
                           .entity(new ResponseDetails(ex.getMessage()))
                           .build();
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            return Response.serverError()
                           .entity(new ResponseDetails(ex.getMessage()))
                           .build();
        }
    }

    /**
     * Notifies the bridge that the snapshot process is not able to continue
     * due to an error which cannot be resolved by the system processing the
     * snapshot data.
     *
     * @param snapshotId
     * @param params
     * @return
     */
    @Path("{snapshotId}/error")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response error(@PathParam("snapshotId") String snapshotId,
                          SnapshotErrorBridgeParameters params) {
        try {
            Snapshot snapshot =
                snapshotManager.transferError(snapshotId, params.getError());
            log.info("Processed snapshot error notification: {}", snapshot);

            return Response.ok(new SnapshotErrorBridgeResult(snapshot.getStatus(),
                                                             snapshot.getStatusText()))
                           .build();
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            return Response.serverError()
                           .entity(new ResponseDetails(ex.getMessage()))
                           .build();
        }
    }

    @Path("{snapshotId}/content")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getContent(@PathParam("snapshotId") String snapshotId,
                               @QueryParam(value = "page") Integer page,
                               @QueryParam(value = "pageSize") Integer pageSize,
                               @QueryParam(value = "prefix") String prefix) {
        try {
            if (page == null) {
                page = 0;
            }
            if (pageSize == null || pageSize < 1 || pageSize > 1000) {
                pageSize = 1000;
            }

            PageRequest pageable = PageRequest.of(page, pageSize);

            List<SnapshotContentItem> items;
            if (null == prefix || prefix.equals("")) {
                items = this.snapshotContentItemRepo
                    .findBySnapshotNameOrderByContentIdAsc(snapshotId,
                                                           pageable);

            } else {
                items = this.snapshotContentItemRepo
                    .findBySnapshotNameAndContentIdStartingWithOrderByContentIdAsc(snapshotId,
                                                                                   prefix,
                                                                                   pageable);
            }

            List<org.duracloud.snapshot.dto.SnapshotContentItem> snapshotItems =
                new ArrayList<>();
            for (SnapshotContentItem item : items) {
                org.duracloud.snapshot.dto.SnapshotContentItem snapshotItem =
                    new org.duracloud.snapshot.dto.SnapshotContentItem();
                snapshotItem.setContentId(item.getContentId());
                String metadata = item.getMetadata();
                if (null != metadata) {
                    snapshotItem.setContentProperties(
                        PropertiesSerializer.deserialize(metadata));
                }
                snapshotItems.add(snapshotItem);
            }

            GetSnapshotContentBridgeResult result =
                new GetSnapshotContentBridgeResult();
            result.setContentItems(snapshotItems);
            result.setTotalCount(snapshotContentItemRepo.countBySnapshotName(snapshotId));

            log.debug("returning results: {}", result);
            return Response.ok(null)
                           .entity(result)
                           .build();
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            return Response.serverError()
                           .entity(new ResponseDetails(ex.getMessage()))
                           .build();
        }
    }

    @Path("{snapshotId}/history")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getHistory(@PathParam("snapshotId") String snapshotId,
                               @QueryParam(value = "page") Integer page,
                               @QueryParam(value = "pageSize") Integer pageSize) {
        try {
            if (page == null) {
                page = 0;
            }
            if (pageSize == null || pageSize < 1 || pageSize > 1000) {
                pageSize = 1000;
            }

            Snapshot snapshot = this.snapshotRepo.findByName(snapshotId);
            List<SnapshotHistory> allItems = snapshot.getSnapshotHistory();
            int fromIndex = (page * pageSize);
            int toIndex = ((page * pageSize) + pageSize);
            // !(toIndex > size)
            toIndex = (toIndex > allItems.size() ? allItems.size() : toIndex);
            // !(fromIndex < 0 || fromIndex > toIndex)
            fromIndex = (fromIndex < 0 ? 0 : fromIndex > toIndex ?
                                             ((toIndex - pageSize) > 0 ? (toIndex - pageSize) : 0) : fromIndex);

            List<SnapshotHistory> items = allItems.subList(fromIndex, toIndex);

            List<org.duracloud.snapshot.dto.SnapshotHistoryItem> historyItems =
                new ArrayList<>();

            for (SnapshotHistory item : items) {
                SnapshotHistoryItem historyItem =
                    new org.duracloud.snapshot.dto.SnapshotHistoryItem();
                historyItem.setHistory(item.getHistory());
                historyItem.setHistoryDate(item.getHistoryDate());
                historyItems.add(historyItem);
            }

            GetSnapshotHistoryBridgeResult result =
                new GetSnapshotHistoryBridgeResult();
            result.setHistoryItems(historyItems);
            result.setTotalCount((long) historyItems.size());
            log.debug("returning results: {}", result);
            return Response.ok(null)
                           .entity(result)
                           .build();
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            return Response.serverError()
                           .entity(new ResponseDetails(ex.getMessage()))
                           .build();
        }
    }

    /**
     * Updates a snapshot's history
     *
     * @param snapshotId - a snapshot's ID or it's alternate ID
     * @param params     - JSON object that contains the history String and a
     *                   Boolean of whether this request is using a snapshot's ID
     *                   or an alternate ID
     * @return
     */
    @Path("{snapshotId}/history")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateHistory(@PathParam("snapshotId") String snapshotId,
                                  UpdateSnapshotHistoryBridgeParameters params) {
        try {
            if (params.getAlternate() == null) {
                return Response.serverError()
                               .entity(new ResponseDetails("Incorrect parameters submitted!"))
                               .build();
            }
            Snapshot snapshot = (params.getAlternate() ?
                                 this.snapshotRepo.findBySnapshotAlternateIds(snapshotId) :
                                 this.snapshotRepo.findByName(snapshotId));

            // sanity check to make sure snapshot exists
            if (snapshot != null) {
                // sanity check input from history
                if (params.getHistory() != null &&
                    params.getHistory().length() > 0) {
                    // set history, and refresh our variable from the DB
                    snapshot = this.snapshotManager.updateHistory(snapshot,
                                                                  params.getHistory());
                    log.info("successfully processed snapshot " +
                             "history update: {}", snapshot);
                } else {
                    log.info("did not process empty or null snapshot " +
                             "history update: {}", snapshot);
                }
                SnapshotSummary snapSummary = createSnapshotSummary(snapshot);
                List<SnapshotHistory> snapMeta = snapshot.getSnapshotHistory();
                String history = // retrieve latest history update
                    ((snapMeta != null && snapMeta.size() > 0) ?
                     snapMeta.get(snapMeta.size() - 1).getHistory() : "");
                UpdateSnapshotHistoryBridgeResult result =
                    new UpdateSnapshotHistoryBridgeResult(snapSummary, history);

                log.debug("Returning results of update snapshot history: {}", result);
                return Response.ok(null)
                               .entity(result)
                               .build();
            } else {
                String error = "Snapshot with " +
                               (params.getAlternate() ? "alternate " : "") +
                               "id [" + snapshotId + "] not found!";
                return Response.serverError()
                               .entity(new ResponseDetails(error))
                               .build();
            }
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            return Response.serverError()
                           .entity(new ResponseDetails(ex.getMessage()))
                           .build();
        }
    }

}

/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.bridge.rest;

import java.util.ArrayList;
import java.util.Date;
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
import javax.ws.rs.core.UriInfo;

import org.apache.commons.httpclient.HttpStatus;
import org.duracloud.snapshot.SnapshotNotFoundException;
import org.duracloud.snapshot.db.model.DuracloudEndPointConfig;
import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.db.model.SnapshotContentItem;
import org.duracloud.snapshot.db.model.SnapshotMetadata;
import org.duracloud.snapshot.db.repo.SnapshotContentItemRepo;
import org.duracloud.snapshot.db.repo.SnapshotRepo;
import org.duracloud.snapshot.dto.SnapshotStatus;
import org.duracloud.snapshot.dto.SnapshotSummary;
import org.duracloud.snapshot.dto.bridge.CompleteSnapshotBridgeResult;
import org.duracloud.snapshot.dto.bridge.CreateSnapshotBridgeParameters;
import org.duracloud.snapshot.dto.bridge.CreateSnapshotBridgeResult;
import org.duracloud.snapshot.dto.bridge.GetSnapshotBridgeResult;
import org.duracloud.snapshot.dto.bridge.GetSnapshotContentBridgeResult;
import org.duracloud.snapshot.dto.bridge.GetSnapshotListBridgeResult;
import org.duracloud.snapshot.dto.bridge.GetSnapshotMetadataBridgeResult;
import org.duracloud.snapshot.dto.bridge.UpdateSnapshotMetadataBridgeResult;
import org.duracloud.snapshot.id.SnapshotIdentifier;
import org.duracloud.snapshot.service.SnapshotJobManager;
import org.duracloud.snapshot.service.SnapshotManager;
import org.duracloud.snapshot.service.impl.PropertiesSerializer;
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


    @Autowired
    public SnapshotResource(
        SnapshotJobManager jobManager, 
        SnapshotManager snapshotManager,
        SnapshotRepo snapshotRepo,
        SnapshotContentItemRepo snapshotContentItemRepo) {
        this.jobManager = jobManager;
        this.snapshotManager = snapshotManager;
        this.snapshotRepo = snapshotRepo;
        this.snapshotContentItemRepo = snapshotContentItemRepo;
    }

    /**
     * Returns a list of snapshots.
     * 
     * @return
     */
    // @Path("/") <-- this throws a runtime warning because "/" is implied as an empty @Path
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response list(@QueryParam("host") String host) {
        try {

            List<Snapshot> snapshots =
                this.snapshotRepo.findBySourceHost(host);

            List<SnapshotSummary> summaries = new ArrayList<>(snapshots.size());
            for (Snapshot snapshot : snapshots) {
                summaries.add(new SnapshotSummary(snapshot.getName(),
                                                  snapshot.getStatus(),
                                                  snapshot.getDescription()));
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
                throw new SnapshotAlreadyExistsException("A snapshot with id "
                    + snapshotId
                    + " already exists - please use a different name");
            }
            snapshot = new Snapshot();
            
            DuracloudEndPointConfig source = new DuracloudEndPointConfig();
            source.setHost(params.getHost());
            source.setPort(Integer.valueOf(params.getPort()));
            source.setSpaceId(params.getSpaceId());
            source.setStoreId(params.getStoreId());
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
            snapshot.setUserEmail(params.getUserEmail());
            snapshot = this.snapshotRepo.saveAndFlush(snapshot);

            this.jobManager.executeSnapshot(snapshotId);
            CreateSnapshotBridgeResult result =
                new CreateSnapshotBridgeResult(snapshotId, snapshot.getStatus());
            
            log.info("successfully created snapshot: {}", result);
            return Response.created(null).entity(result).build();
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            
            if(snapshot != null && snapshot.getId() != null){
                log.info("cleaning up post exception...");
                try{
                    log.debug("deleting newly created snapshot...");
                    snapshotRepo.delete(snapshot.getId());
                }catch(Exception e){
                    log.error("failed to cleanup snapshot " + snapshotId + ": " + e.getMessage(), e);
                }
                log.info("cleaning up complete");
            }
            return Response.serverError()
                           .entity(new ResponseDetails(ex.getMessage()))
                           .build();
        }
    }

    /**
     * Notifies the bridge that the snapshot transfer from the bridge storage to the DPN node 
     * is complete. Also sets a snapshot's alternate id's if they are passed in.
     * @param snapshotId
     * @param alternateIds
     * @return
     */
    @Path("{snapshotId}/complete")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response complete(@PathParam("snapshotId") String snapshotId, AlternateIdsJSONParam alternateIds) {
        try {
            Snapshot snapshot = this.snapshotRepo.findByName(snapshotId);

            // sanity check input from alternateIds since they are optional
            if(alternateIds != null) {
                // set alternate id's
                this.snapshotManager.setAlternateSnapshotIds(snapshot, alternateIds.getAlternateIds());
            }

            snapshot = this.snapshotManager.transferToDpnNodeComplete(snapshotId);

            log.info("successfully processed snapshot complete notification from DPN: {}", snapshot);

            return Response.ok(null)
                           .entity(new CompleteSnapshotBridgeResult(snapshot.getStatus(),
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
                               @QueryParam(value="page") Integer page,
                               @QueryParam(value="pageSize") Integer pageSize,
                               @QueryParam(value="prefix") String prefix) {
        try {
            if(page == null){
                page = 0;
            }
            if(pageSize == null || pageSize < 1 || pageSize > 1000){
                pageSize = 1000;
            }
            
            PageRequest pageable = new PageRequest(page, pageSize);
            List<SnapshotContentItem> items =
                this.snapshotContentItemRepo.findBySnapshotNameAndContentIdStartingWithOrderByContentIdAsc(snapshotId, prefix, pageable);

            List<org.duracloud.snapshot.dto.SnapshotContentItem> snapshotItems =
                new ArrayList<>();
            for(SnapshotContentItem item : items) {
                org.duracloud.snapshot.dto.SnapshotContentItem snapshotItem =
                    new org.duracloud.snapshot.dto.SnapshotContentItem();
                snapshotItem.setContentId(item.getContentId());
                String metadata = item.getMetadata();
                if(null != metadata) {
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

    @Path("{snapshotId}/metadata")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMetadata(@PathParam("snapshotId") String snapshotId,
                               @QueryParam(value="page") Integer page,
                               @QueryParam(value="pageSize") Integer pageSize) {
        try {
            if(page == null){
                page = 0;
            }
            if(pageSize == null || pageSize < 1 || pageSize > 1000){
                pageSize = 1000;
            }

            Snapshot snapshot = this.snapshotRepo.findByName(snapshotId);
            List<SnapshotMetadata> allItems = snapshot.getSnapshotMetadata();
            int fromIndex = (page * pageSize);
            int toIndex = ((page * pageSize)+pageSize);
            // !(toIndex > size)
            toIndex = (toIndex > allItems.size() ? allItems.size() : toIndex);
            // !(fromIndex < 0 || fromIndex > toIndex)
            fromIndex = (fromIndex < 0 ? 0 : fromIndex > toIndex ? ((toIndex - pageSize) > 0 ? (toIndex - pageSize) : 0) : fromIndex);

            List<SnapshotMetadata> items = allItems.subList(fromIndex, toIndex);

            List<org.duracloud.snapshot.dto.SnapshotMetadataItem> metadataItems =
                new ArrayList<>();
            for(SnapshotMetadata item : items) {
                org.duracloud.snapshot.dto.SnapshotMetadataItem metadataItem =
                    new org.duracloud.snapshot.dto.SnapshotMetadataItem();
                metadataItem.setMetadata((item.getMetadata()));
                metadataItem.setMetadataDate(item.getMetadataDate());
                metadataItems.add(metadataItem);
            }

            GetSnapshotMetadataBridgeResult result =
                new GetSnapshotMetadataBridgeResult();
            result.setMetadataItems(metadataItems);
            result.setTotalCount((long) metadataItems.size());

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
     * Updates a snapshot's DPN metadata
     * @param snapshotId - a snapshot's ID or it's alternate ID
     * @param metadataUpdateParam - JSON object that contains the metadata String and a Boolean of whether this request is using a snapshot's ID or its alternate ID
     * @return
     */
    @Path("{snapshotId}/metadata/update")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateMetadata(@PathParam("snapshotId") String snapshotId, UpdateMetadataJSONParam metadataUpdateParam) {
        try {
            if(metadataUpdateParam.getIsAlternate() == null) {
                return Response.serverError()
                        .entity(new ResponseDetails("Incorrect parameters submitted!"))
                        .build();
            }
            Snapshot snapshot = (metadataUpdateParam.getIsAlternate() ? this.snapshotRepo.findBySnapshotAlternateIds(snapshotId) : this.snapshotRepo.findByName(snapshotId));

            // sanity check to make sure snapshot exists
            if(snapshot != null) {
                // sanity check input from metadata
                if(metadataUpdateParam.getMetadata() != null && metadataUpdateParam.getMetadata().length() > 0) {
                    // set metadata, and refresh our variable from the DB
                    snapshot = this.snapshotManager.updateMetadata(snapshot, metadataUpdateParam.getMetadata());
                    log.info("successfully processed snapshot metadata update from DPN: {}", snapshot);
                } else {
                    log.info("did not process empty or null snapshot metadata update from DPN: {}", snapshot);
                }
                SnapshotSummary snapSummary = new SnapshotSummary(snapshot.getName(), snapshot.getStatus(), snapshot.getDescription());
                List<SnapshotMetadata> snapMeta = snapshot.getSnapshotMetadata();
                String metadata = (( snapMeta != null && snapMeta.size() > 0) ? snapMeta.get(0).getMetadata() : "");
                UpdateSnapshotMetadataBridgeResult result = new UpdateSnapshotMetadataBridgeResult(snapSummary, metadata);

                log.debug("returning results: {}", result);

	            return Response.ok(null)
	                           .entity(result)
	                           .build();
	        } else {
	            return Response.serverError()
	                    .entity(new ResponseDetails("Snapshot with "+(metadataUpdateParam.getIsAlternate() ? "alternate " : "")+"id [" + snapshotId + "] not found!"))
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

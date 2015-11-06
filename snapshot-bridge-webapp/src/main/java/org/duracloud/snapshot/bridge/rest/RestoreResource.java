/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.bridge.rest;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.httpclient.HttpStatus;
import org.duracloud.common.util.DateUtil;
import org.duracloud.snapshot.db.model.DuracloudEndPointConfig;
import org.duracloud.snapshot.db.model.Restoration;
import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.dto.bridge.CompleteRestoreBridgeResult;
import org.duracloud.snapshot.dto.bridge.CreateRestoreBridgeParameters;
import org.duracloud.snapshot.dto.bridge.CreateRestoreBridgeResult;
import org.duracloud.snapshot.dto.bridge.GetRestoreBridgeResult;
import org.duracloud.snapshot.dto.bridge.RequestRestoreBridgeParameters;
import org.duracloud.snapshot.dto.bridge.RequestRestoreBridgeResult;
import org.duracloud.snapshot.service.RestorationNotFoundException;
import org.duracloud.snapshot.service.RestoreManager;
import org.duracloud.snapshot.service.SnapshotManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Defines the REST resource layer for interacting with the Snapshot processing
 * engine.
 * 
 * @author Daniel Bernstein Date: Feb 4, 2014
 */
@Component
@Path("/restore")
public class RestoreResource {

    private static Logger log = LoggerFactory.getLogger(RestoreResource.class);

    public static final String RESTORE_ACTION_TITLE = "restore-action";
    public static final String RESTORE_ACTION_REQUESTED = "RESTORE_REQUESTED";
    public static final String RESTORE_ACTION_INITIATED = "RESTORE_INITIATED";
    public static final String RESTORE_USER_TITLE = "initiating-user";
    public static final String RESTORE_ID_TITLE = "restore-id";

    @Context
    HttpServletRequest request;

    @Context
    HttpHeaders headers;

    @Context
    UriInfo uriInfo;

    private RestoreManager restorationManager;

    private SnapshotManager snapshotManager;

    @Autowired
    public RestoreResource(RestoreManager restorationManager,
                           SnapshotManager snapshotManager) {
        this.restorationManager = restorationManager;
        this.snapshotManager = snapshotManager;
    }

    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response restoreSnapshot(CreateRestoreBridgeParameters  params) {
        try {
            DuracloudEndPointConfig destination = new DuracloudEndPointConfig();
            destination.setHost(params.getHost());
            destination.setPort(Integer.valueOf(params.getPort()));
            destination.setStoreId(params.getStoreId());
            destination.setSpaceId(params.getSpaceId());
            Restoration result =
                this.restorationManager.restoreSnapshot(params.getSnapshotId(),
                                                        destination, params.getUserEmail());
            
            log.info("executed restore snapshot:  params=" + params + ", result = " + result);

            // Add history event
            String history =
                "[{'"+RESTORE_ACTION_TITLE+"':'"+RESTORE_ACTION_INITIATED+"'}," +
                "{'"+RESTORE_ID_TITLE+"':'"+result.getRestorationId()+"'}," +
                "{'"+RESTORE_USER_TITLE+"':'"+params.getUserEmail()+"'}]";
            snapshotManager.updateHistory(result.getSnapshot(), history);

            return Response.created(null)
                           .entity(new CreateRestoreBridgeResult(result.getRestorationId(),
                                                                 result.getStatus()))
                           .build();
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            return Response.serverError()
                           .entity(new ResponseDetails(ex.getMessage()))
                           .build();
        }
    }

    @Path("/request")
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response requestRestoreSnapshot(RequestRestoreBridgeParameters  params) {
        try {
            DuracloudEndPointConfig destination = new DuracloudEndPointConfig();
            destination.setHost(params.getHost());
            destination.setPort(Integer.valueOf(params.getPort()));
            destination.setStoreId(params.getStoreId());
            Snapshot snapshot =
                this.restorationManager.requestRestoreSnapshot(params.getSnapshotId(),
                                                               destination,
                                                               params.getUserEmail());
            
            log.info("executed request restore snapshot:  params=" + params);

            // Add history event
            String history =
                "[{'"+RESTORE_ACTION_TITLE+"':'"+RESTORE_ACTION_REQUESTED+"'}," +
                 "{'"+RESTORE_USER_TITLE+"':'"+params.getUserEmail()+"'}]";
            snapshotManager.updateHistory(snapshot, history);

            return Response.created(null)
                           .entity(new RequestRestoreBridgeResult("Your request has been sent."))
                           .build();
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            return Response.serverError()
                           .entity(new ResponseDetails(ex.getMessage()))
                           .build();
        }
    }

    @Path("{restorationId}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    /**
     * Returns the status of a restoration.
     * @param snapshotId
     * @return
     */
    public Response get(@PathParam("restorationId") String restorationId) {
        try {
            Restoration restoration =
                this.restorationManager.get(restorationId);
            
            return Response.ok()
                           .entity(toGetRestoreBridgeResult(restoration))
                           .build();
        } catch (RestorationNotFoundException ex) {
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
    
    @Path("by-snapshot/{snapshotId}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    /**
     * Returns the status of a restoration by snapshot id.
     * @param snapshotId
     * @return
     */
    public Response getBySnapshot(@PathParam("snapshotId") String snapshotId) {
        try {
            Restoration restoration =
                this.restorationManager.getBySnapshotId(snapshotId);
            
            return Response.ok()
                           .entity(toGetRestoreBridgeResult(restoration))
                           .build();
        } catch (RestorationNotFoundException ex) {
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

    /**
     * @param restoration
     * @return
     */
    private GetRestoreBridgeResult
        toGetRestoreBridgeResult(Restoration restoration) {
        DuracloudEndPointConfig destination = restoration.getDestination();
        GetRestoreBridgeResult result = new GetRestoreBridgeResult();
        result.setRestoreId(restoration.getRestorationId());
        result.setSnapshotId(restoration.getSnapshot().getName());
        result.setStartDate(restoration.getStartDate());
        result.setEndDate(restoration.getEndDate());
        result.setExpirationDate(restoration.getExpirationDate());
        result.setStatus(restoration.getStatus());
        result.setStatusText(restoration.getStatusText());
        result.setDestinationStoreId(destination.getStoreId());
        result.setDestinationHost(destination.getHost());
        result.setDestinationPort(destination.getPort());
        result.setDestinationSpaceId(destination.getSpaceId());

        return result;
    }

    @Path("{restorationId}/complete")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response restoreComplete(@PathParam("restorationId") String restorationId) {
        try {
            Restoration restoration =
                this.restorationManager.restoreCompleted(restorationId);
            
            log.info("executed restoreComplete for {}", restoration);
            return Response.ok()
                           .entity(new CompleteRestoreBridgeResult(restoration.getStatus(),
                                                                   restoration.getStatusText()))
                           .build();
        } catch (RestorationNotFoundException ex) {
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

}

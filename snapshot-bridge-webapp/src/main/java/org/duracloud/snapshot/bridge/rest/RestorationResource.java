/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.bridge.rest;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
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
import org.codehaus.jettison.json.JSONObject;
import org.duracloud.snapshot.bridge.service.RestorationManager;
import org.duracloud.snapshot.bridge.service.RestorationNotFoundException;
import org.duracloud.snapshot.db.model.DuracloudEndPointConfig;
import org.duracloud.snapshot.db.model.Restoration;
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
public class RestorationResource {

    private static Logger log =
        LoggerFactory.getLogger(RestorationResource.class);

    @Context
    HttpServletRequest request;

    @Context
    HttpHeaders headers;

    @Context
    UriInfo uriInfo;

    private RestorationManager restorationManager;

    @Autowired
    public RestorationResource(RestorationManager restorationManager) {
        this.restorationManager = restorationManager;
    }

    @Path("/")
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response restoreSnapshot(@Valid RestoreParams params) {
        try {
            DuracloudEndPointConfig destination = new DuracloudEndPointConfig();
            destination.setHost(params.getHost());
            destination.setPort(Integer.valueOf(params.getPort()));
            destination.setStoreId(params.getStoreId());
            destination.setSpaceId(params.getSpaceId());
            Restoration restorationRequest =
                this.restorationManager.restoreSnapshot(params.getSnapshotId(),
                                                        destination);
            return Response.ok().entity(restorationRequest).build();
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
     * Returns the status of a snapshot. The fields available in the response will match
     * those in <code>SnapshotStatus</code>.
     * @param snapshotId
     * @return
     */
    public Response getStatus(@PathParam("restorationId") Long restorationId) {
        try {
            Restoration restoration =
                this.restorationManager.get(restorationId);

            return Response.ok()
                           .entity(new JSONObject().put("status",
                                                        restoration.getStatus())
                                                   .put("details",
                                                        restoration.getMemo()))
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

    @Path("{restorationId}/complete")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response
        restoreComplete(@PathParam("restorationId") Long restorationId) {

        try {
            Restoration status =
                this.restorationManager.restorationCompleted(restorationId);
            return Response.ok()
                           .entity(new JSONObject().put("status", status))
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
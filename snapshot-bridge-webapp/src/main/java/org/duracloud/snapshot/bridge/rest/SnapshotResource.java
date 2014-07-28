/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.bridge.rest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

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
import org.duracloud.common.notification.NotificationManager;
import org.duracloud.common.notification.NotificationType;
import org.duracloud.snapshot.bridge.service.BridgeConfiguration;
import org.duracloud.snapshot.db.model.DuracloudEndPointConfig;
import org.duracloud.snapshot.db.model.Snapshot;
import org.duracloud.snapshot.db.model.SnapshotStatus;
import org.duracloud.snapshot.db.repo.SnapshotRepo;
import org.duracloud.snapshot.manager.SnapshotJobManager;
import org.duracloud.snapshot.manager.SnapshotNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestParam;

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

    private SnapshotRepo snapshotRepo;
    
    private NotificationManager notificationManager;

    private BridgeConfiguration config; 
    
    @Autowired
    public SnapshotResource(
        SnapshotJobManager jobManager, SnapshotRepo snapshotRepo,
        NotificationManager notificationManager, BridgeConfiguration config) {
        this.jobManager = jobManager;
        this.snapshotRepo = snapshotRepo;
        this.notificationManager = notificationManager;
        this.config = config;
    }
    
    /**
     * Returns a list of snapshots.
     * 
     * @return
     */
    @Path("/")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response list(@RequestParam(required=true) String host) {
        try {
            return Response.ok().entity(this.snapshotRepo.findBySourceHost(host)).build();
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
    public Response getStatus(@PathParam("snapshotId") String snapshotId) {
        try {
            Snapshot snapshot = this.snapshotRepo.findByName(snapshotId);
            if(snapshot == null){
                throw new SnapshotNotFoundException(snapshotId);
            }
            return Response.ok().entity(snapshot.getStatus()).build();
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
                           @Valid SnapshotRequestParams params) {

        try {
            if (this.snapshotRepo.findByName(snapshotId) != null) {
                throw new SnapshotAlreadyExistsException("A snapshot with id "
                    + snapshotId
                    + " already exists - please use a different id");
            }

            DuracloudEndPointConfig source = new DuracloudEndPointConfig();
            source.setHost(params.getHost());
            source.setPort(Integer.valueOf(params.getPort()));
            source.setSpaceId(params.getSpaceId());
            source.setStoreId(params.getStoreId());
            Snapshot snapshot = new Snapshot();
            snapshot.setModified(new Date());
            snapshot.setName(snapshotId);
            snapshot.setSource(source);
            snapshot.setDescription(params.getDescription());
            snapshot.setStatus(SnapshotStatus.INITIALIZED);
            snapshot.setUserEmail(params.getUserEmail());
            this.snapshotRepo.saveAndFlush(snapshot);

            this.jobManager.executeSnapshot(snapshotId);
            return Response.created(null)
                           .entity(new ResponseDetails("success"))
                           .build();
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            return Response.serverError()
                           .entity(new ResponseDetails(ex.getMessage()))
                           .build();
        }
    }
    
    @Path("{snapshotId}/complete")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response complete(@PathParam("snapshotId") String snapshotId) {

        try {
            Snapshot snapshot = this.snapshotRepo.findByName(snapshotId);
            if (snapshot == null) {
                throw new SnapshotAlreadyExistsException("A snapshot with id "
                    + snapshotId
                    + " does not exist.");
            }
            
            
            snapshot.setStatus(SnapshotStatus.SNAPSHOT_COMPLETE);
            this.snapshotRepo.saveAndFlush(snapshot);
            String message =  "Snapshot complete: " + snapshotId;
            List<String> recipients = new ArrayList<>(Arrays.asList(this.config.getDuracloudEmailAddresses()));
            String userEmail = snapshot.getUserEmail();
            if(userEmail != null){
                recipients.add(userEmail);
            }
            
            if(recipients.size() > 0){
                this.notificationManager.sendNotification(NotificationType.EMAIL,
                                                          message,
                                                          message,
                                                          recipients.toArray(new String[0]));
            }
            
            return Response.created(null)
                           .entity(new ResponseDetails("success"))
                           .build();
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            return Response.serverError()
                           .entity(new ResponseDetails(ex.getMessage()))
                           .build();
        }
    }

}
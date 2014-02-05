/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.rest;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.springframework.stereotype.Component;

/**
 * Defines the REST resource layer for interacting with the Snapshot processing
 * engine.
 * 
 * @author Daniel Bernstein Date: Feb 4, 2014
 */
@Component
@Path("snapshot")
public class SnapshotResource {

    @Context
    HttpServletRequest request;

    @Context
    HttpHeaders headers;

    @Context
    UriInfo uriInfo;


    /**
     * Returns a list of snapshots.
     * 
     * @return
     */
    @Path("version")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response version() {
        try {
            InputStream is = getClass().getResourceAsStream("/application.properties");
            Properties props = new Properties();
            props.load(is);
            String version = props.get("version").toString();
            return Response.ok().entity("{version:'"+version+"'}").build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns a list of snapshots.
     * 
     * @return
     */
    @Path("list")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response list() {
        return Response.ok().entity(getSnapshotList()).build();
    }

    /**
     * @return
     */
    private List<Map<String, String>> getSnapshotList() {
        List<Map<String, String>> list = new LinkedList<>();

        for (int i = 0; i < 10; i++) {
            Map<String, String> map = new HashMap<>();
            map.put("id", i + "");
            list.add(map);
        }
        return list;
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
            SnapshotStatus status = getSnapshotStatus(snapshotId);
            return Response.ok(status).build();

        } catch (NotFoundException ex) {
            return Response.status(Status.NOT_FOUND).build();
        }
    }

    /**
     * @param snapshotId
     * @return
     */
    private SnapshotStatus getSnapshotStatus(String snapshotId)
        throws NotFoundException {
        return new SnapshotStatus(snapshotId, "running");
    }

    @Path("{id}/cancel")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response cancel(@PathParam("id") String id) {
        return Response.status(Status.NOT_IMPLEMENTED).build();
    }

    @Path("{host}/{port}/{storeId}/{spaceId}/{snapshotId}")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response create(@PathParam("host") String host,
                           @PathParam("port") String port,
                           @PathParam("storeId") String storeId,
                           @PathParam("spaceId") String spaceId,
                           @PathParam("snapshotId") String snapshotId) {
        if (alreadyExists(snapshotId)) {
            return Response.serverError()
                           .entity("Snapshot "
                               + snapshotId + " already exists.")
                           .build();
        }

        try {
            return Response.created(null)
                           .entity(new SnapshotStatus(snapshotId, "new"))
                           .build();
        }catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity(e).build();
        }
    }

    /**
     * @param snapshotId
     * @return
     */
    private boolean alreadyExists(String snapshotId) {
        try {
            //getSnapshotStatus(snapshotId);
            //return true;
        } catch (NotFoundException e) {
        }

        return false;

    }
}
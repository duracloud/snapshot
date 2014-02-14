/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.rest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
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

import org.apache.commons.httpclient.HttpStatus;
import org.duracloud.snapshot.spring.batch.DatabaseInitializer;
import org.duracloud.snapshot.spring.batch.SnapshotException;
import org.duracloud.snapshot.spring.batch.SnapshotJobManager;
import org.duracloud.snapshot.spring.batch.SnapshotNotFoundException;
import org.duracloud.snapshot.spring.batch.SnapshotStatus;
import org.duracloud.snapshot.spring.batch.driver.DatabaseConfig;
import org.duracloud.snapshot.spring.batch.driver.SnapshotConfig;
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
@Path("/")
public class SnapshotResource {
    
    private static Logger log = LoggerFactory.getLogger(SnapshotResource.class);

    @Context
    HttpServletRequest request;

    @Context
    HttpHeaders headers;

    @Context
    UriInfo uriInfo;
    
    private File workDir;
    private File contentDirRoot;
    
    private SnapshotJobManager jobManager;
    private DatabaseInitializer databaseInitializer;
    
    @Autowired
    public SnapshotResource(SnapshotJobManager jobManager, 
                            DatabaseInitializer databaseInitializer) {
        this.jobManager = jobManager;
        this.databaseInitializer = databaseInitializer;
    }    
    
    @Path("init")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response init(InitParams initParams) {
        try {
            
            initializeLocalDirectories(initParams);            
            DatabaseConfig dbConfig  = new DatabaseConfig();
            dbConfig.setUrl(initParams.getDatabaseURL());
            dbConfig.setUsername(initParams.getDatabaseUser());
            dbConfig.setPassword(initParams.getDatabasePassword());
            dbConfig.setClean(initParams.isClean());
            //initialize database
            databaseInitializer.init(dbConfig);
            
            this.jobManager.init();
            return Response.accepted().entity(new ResponseDetails("success!")).build();
        } catch (Exception e) {
            return Response.serverError()
                           .entity(new ResponseDetails("failure!"+e.getMessage()))
                           .build();
        }
    }   
    
    private void initializeLocalDirectories(InitParams initParams) throws IOException{
        String defaultDirRoot =
            System.getProperty("java.io.tmpdir") + File.separator;
        
        this.workDir =
            createDirectoryIfNotExists(initParams.getWorkDir(), defaultDirRoot
                + "snapshot-work");

        this.contentDirRoot =
            createDirectoryIfNotExists(initParams.getContentDirRoot(), defaultDirRoot
                + "snapshot-content");

    }

    /**
     * @param path
     * @param defaultDir
     * @return
     */
    private File createDirectoryIfNotExists(String path, String defaultDir) {
        if(path == null){
            path = defaultDir;
        }

        File wdir = new File(path);
        if(!wdir.exists()){
            if (!wdir.mkdirs()) {
                throw new RuntimeException("failed to initialize "
                    + path + ": directory could not be created.");
            }
        }
        
        if(!wdir.canWrite()){
            throw new RuntimeException(wdir.getAbsolutePath() + " must be writable.");
        }

        return wdir;
    }

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
            //should never happen
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns a list of snapshots.
     * 
     * @return
     */
    /*
    @Path("list")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response list() {
        return Response.ok().entity(getSnapshotList()).build();
    }
    */

    
    /**
     * @return
     */
    /*
    private List<SnapshotSummary> getSnapshotList() {
        return this.jobManager.getSnapshotList();
    }
    */

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
            SnapshotStatus status = this.jobManager.getStatus(snapshotId);
            return Response.ok().entity(status).build();
        } catch (SnapshotNotFoundException ex) {
            log.error(ex.getMessage(), ex);
            return Response.status(HttpStatus.SC_NOT_FOUND)
                           .entity(new ResponseDetails(ex.getMessage()))
                           .build();
        } catch (SnapshotException ex) {
            log.error(ex.getMessage(), ex);
            return Response.serverError()
                           .entity(new ResponseDetails(ex.getMessage()))
                           .build();
        }
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

        try {

            SnapshotConfig config = new SnapshotConfig();
            config.setHost(host);
            config.setPort(Integer.parseInt(port));
            config.setStoreId(storeId);
            config.setSpace(spaceId);
            config.setSnapshotId(snapshotId);
            config.setWorkDir(this.workDir);
            
            File contentDir = new File(this.contentDirRoot,snapshotId);
            contentDir.mkdir();
            config.setContentDir(contentDir);
            SnapshotStatus status = this.jobManager.executeSnapshotAsync(config);
            return Response.created(null)
                .entity(status)
                .build();
        }catch(Exception ex){
            log.error(ex.getMessage(),ex);
            return Response.serverError()
                .entity(new ResponseDetails(ex.getMessage()))
                .build();
        }
    }
}
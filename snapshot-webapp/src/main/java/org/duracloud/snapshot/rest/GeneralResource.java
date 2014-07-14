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
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.lang.StringUtils;
import org.duracloud.appconfig.domain.NotificationConfig;
import org.duracloud.common.notification.NotificationManager;
import org.duracloud.common.notification.NotificationType;
import org.duracloud.snapshot.db.DatabaseConfig;
import org.duracloud.snapshot.db.DatabaseInitializer;
import org.duracloud.snapshot.manager.SnapshotJobManager;
import org.duracloud.snapshot.manager.config.ExecutionListenerConfig;
import org.duracloud.snapshot.manager.config.SnapshotJobManagerConfig;
import org.duracloud.snapshot.manager.spring.batch.SnapshotExecutionListener;
import org.duracloud.snapshot.service.RestorationManager;
import org.duracloud.snapshot.service.RestorationManagerConfig;
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
public class GeneralResource {
    
    private static Logger log = LoggerFactory.getLogger(GeneralResource.class);

    @Context
    HttpServletRequest request;

    @Context
    HttpHeaders headers;

    @Context
    UriInfo uriInfo;
   
    private SnapshotJobManager jobManager;
    private DatabaseInitializer databaseInitializer;
    private SnapshotExecutionListener jobListener;
    private RestorationManager restorationManager;
    private NotificationManager notificationManager;
    
    @Autowired
    public GeneralResource(SnapshotJobManager jobManager, 
                            RestorationManager restorationManager,
                            DatabaseInitializer databaseInitializer,
                            SnapshotExecutionListener jobListener,
                            NotificationManager notificationManager) {
        this.jobManager = jobManager;
        this.restorationManager = restorationManager;
        this.databaseInitializer = databaseInitializer;
        this.jobListener = jobListener;
        this.notificationManager = notificationManager;
    }    
    
    @Path("init")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response init(InitParams initParams) {
        try {
            initializeLocalDirectories(initParams); 
            initDatabase(initParams);
            initExecutionListener(initParams);
            initJobManager(initParams);
            initRestorationResource(initParams);
            initNotificationManager(initParams);
            return Response.accepted().entity(new ResponseDetails("success!")).build();
        } catch (Exception e) {
            return Response.serverError()
                           .entity(new ResponseDetails("failure!"+e.getMessage()))
                           .build();
        }
    }

    /**
     * @param initParams
     */
    private void initNotificationManager(InitParams initParams) {

        NotificationConfig notifyConfig = new NotificationConfig();
        notifyConfig.setType(NotificationType.EMAIL.name());
        notifyConfig.setUsername(initParams.getAwsAccessKey());
        notifyConfig.setPassword(initParams.getAwsSecretKey());
        notifyConfig.setOriginator(
            initParams.getOriginatorEmailAddress());

        List<NotificationConfig> notifyConfigs = new ArrayList<>();
        notifyConfigs.add(notifyConfig);
        notificationManager.initializeNotifiers(notifyConfigs);
        
    }

    /**
     * @param initParams
     */
    private void initRestorationResource(InitParams initParams) {
        RestorationManagerConfig config = new RestorationManagerConfig();
        config.setRestorationRootDir(initParams.getContentDirRoot()
            + File.separator + "restorations");
        config.setDpnEmailAddresses(initParams.getDpnEmailAddresses());
        config.setDuracloudEmailAddresses(initParams.getDuracloudEmailAddresses());
        config.setDuracloudUsername(initParams.getDuracloudUsername());
        config.setDuracloudPassword(initParams.getDuracloudPassword());
        
        this.restorationManager.init(config);
    }

    /**
     * @param initParams
     */
    private void initDatabase(InitParams initParams) {
        DatabaseConfig dbConfig  = new DatabaseConfig();
        dbConfig.setUrl(initParams.getDatabaseURL());
        dbConfig.setUsername(initParams.getDatabaseUser());
        dbConfig.setPassword(initParams.getDatabasePassword());
        dbConfig.setClean(initParams.isClean());
        //initialize database
        databaseInitializer.init(dbConfig);
    }

    /**
     * @param initParams
     */
    private void initExecutionListener(InitParams initParams) {
        ExecutionListenerConfig notifyConfig = new ExecutionListenerConfig();
        notifyConfig.setSesUsername(initParams.getAwsAccessKey());
        notifyConfig.setSesPassword(initParams.getAwsSecretKey());
        notifyConfig.setDuracloudEmailAddresses(
            initParams.getDuracloudEmailAddresses());
        notifyConfig.setDpnEmailAddresses(
            initParams.getDpnEmailAddresses());
        notifyConfig.setOriginatorEmailAddress(
            initParams.getOriginatorEmailAddress());
        notifyConfig.setContentRoot(new File(initParams.getContentDirRoot()));
        this.jobListener.init(notifyConfig);

    }


    /**
     * @param initParams
     */
    private void initJobManager(InitParams initParams) {
        if(StringUtils.isBlank(initParams.getWorkDir()))       {
            throw new IllegalArgumentException("workDir must not be blank.");
        }

        if(StringUtils.isBlank(initParams.getContentDirRoot()))       {
            throw new IllegalArgumentException("contentDirRoot must not be blank.");
        }

        File workDir =
            createDirectoryIfNotExists(initParams.getWorkDir());
        File contentDirRoot =
            createDirectoryIfNotExists(initParams.getContentDirRoot());

        SnapshotJobManagerConfig jobManagerConfig = new SnapshotJobManagerConfig();
        jobManagerConfig.setDuracloudUsername(initParams.getDuracloudUsername());
        jobManagerConfig.setDuracloudPassword(initParams.getDuracloudPassword());
        jobManagerConfig.setContentRootDir(contentDirRoot);
        jobManagerConfig.setWorkDir(workDir);
        this.jobManager.init(jobManagerConfig);
    }   
    
    private void initializeLocalDirectories(InitParams initParams)  {
        
    }

    /**
     * @param path
     * @return
     */
    private File createDirectoryIfNotExists(String path) {
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
            return Response.ok().entity("{\"version\":\""+version+"\"}").build();
        } catch (IOException e) {
            //should never happen
            throw new RuntimeException(e);
        }
    }
}
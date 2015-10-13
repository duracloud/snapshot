/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.bridge.rest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.annotation.PostConstruct;
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

import org.duracloud.appconfig.domain.NotificationConfig;
import org.duracloud.common.json.JaxbJsonSerializer;
import org.duracloud.common.model.RootUserCredential;
import org.duracloud.common.notification.NotificationManager;
import org.duracloud.common.notification.NotificationType;
import org.duracloud.common.util.EncryptionUtil;
import org.duracloud.common.util.IOUtil;
import org.duracloud.snapshot.db.DatabaseConfig;
import org.duracloud.snapshot.db.DatabaseInitializer;
import org.duracloud.snapshot.service.AlreadyInitializedException;
import org.duracloud.snapshot.service.BridgeConfiguration;
import org.duracloud.snapshot.service.Finalizer;
import org.duracloud.snapshot.service.RestoreManager;
import org.duracloud.snapshot.service.RestoreManagerConfig;
import org.duracloud.snapshot.service.SnapshotJobManager;
import org.duracloud.snapshot.service.SnapshotJobManagerConfig;
import org.duracloud.snapshot.service.impl.ExecutionListenerConfig;
import org.duracloud.snapshot.service.impl.RestoreJobExecutionListener;
import org.duracloud.snapshot.service.impl.SnapshotJobExecutionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Defines the REST resource layer for interacting with the Snapshot processing
 * engine.
 * 
 * @author Daniel Bernstein Date: Feb 4, 2014
 */
@Component
@Path("/")
@Lazy(value=false)
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
    private SnapshotJobExecutionListener snapshotJobListener;
    private RestoreJobExecutionListener restoreJobListener;
    private RestoreManager restorationManager;
    private NotificationManager notificationManager;
    private BridgeConfiguration bridgeConfiguration;
    private Finalizer finalizer;
    
    @Autowired
    public GeneralResource(SnapshotJobManager jobManager, 
                            RestoreManager restorationManager,
                            DatabaseInitializer databaseInitializer,
                            SnapshotJobExecutionListener snapshotJobListener,
                            RestoreJobExecutionListener restoreListener,
                            NotificationManager notificationManager, 
                            Finalizer finalizer,
                            BridgeConfiguration bridgeConfiguration) {
        this.jobManager = jobManager;
        this.restorationManager = restorationManager;
        this.databaseInitializer = databaseInitializer;
        this.snapshotJobListener = snapshotJobListener;
        this.restoreJobListener = restoreListener;
        this.notificationManager = notificationManager;
        this.finalizer = finalizer;
        this.bridgeConfiguration = bridgeConfiguration;
        
    }    

    @PostConstruct()
    public void init(){
        initFromStoreInitConfig();
    }
    

    @Path("init")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response init(InitParams initParams) {
        try {
            
            checkIfAlreadyInitialized();

            initBridgeConfiguration(initParams);
            initDatabase(initParams);
            initExecutionListener(initParams);
            initJobManager(initParams);
            initRestorationResource(initParams);
            initNotificationManager(initParams);
            this.finalizer.initialize(initParams.getFinalizerPeriodMs());

            //set the clean variable to false to ensure that on restart
            //the database is not reset automatically.
            initParams.setClean(false);
            writeInitConfigToDisk(initParams);

            log.info("successfully initialized bridge application.");

            return Response.accepted().entity(new ResponseDetails("success!")).build();
        } catch (Exception e) {
            return Response.serverError()
                           .entity(new ResponseDetails("failure!"+e.getMessage()))
                           .build();
        }
    }

    private void checkIfAlreadyInitialized() throws AlreadyInitializedException{
        if(this.jobManager.isInitialized()){
            throw new AlreadyInitializedException("The bridge has already been initialized.");
        }
    }

    /**
     * @param initParams
     */
    private void writeInitConfigToDisk(InitParams initParams) {
        EncryptionUtil encryptionUtil = getEncryptionUtil();
        try (FileWriter writer = new FileWriter(getStoreInitFile())) {
            String serialized = getInitSerializer().serialize(initParams);
            String encrypted = encryptionUtil.encrypt(serialized);
            writer.write(encrypted);
        } catch (IOException e) {
            log.error("failed to write init config: " + e.getMessage(), e);
        }
    }
    
    private void initFromStoreInitConfig() {
        File storedInitFile = getStoreInitFile();
        if(!storedInitFile.exists()){
            log.info("The encrypted stored init file ({}) does not exist. Ignoring...",
                     storedInitFile.getAbsolutePath());
            return;
        }
        try {
            log.info("Initializing from stored encrypted file ({})...",
                     storedInitFile.getAbsolutePath());

            init(getStoredInitParams());
        } catch (IOException e) {
            log.error("failed to initialize from stored config: " + e.getMessage(), e);
        } 
    }
    
    protected InitParams getStoredInitParams() throws IOException {
        File storedInitFile = getStoreInitFile();
        try (InputStream is = new FileInputStream(storedInitFile)) {
            String encryptedString = IOUtil.readStringFromStream(is);
            String decryptedString = getEncryptionUtil().decrypt(encryptedString);
            return getInitSerializer().deserialize(decryptedString);
        }
    }

    /**
     * @return
     */
    private EncryptionUtil getEncryptionUtil() {
        RootUserCredential rootCredential = new RootUserCredential();
        return new EncryptionUtil(rootCredential.getPassword());
    }

    /**
     * @return
     */
    protected File getStoreInitFile() {
       return  new File(BridgeConfiguration.getBridgeRootDir(), "duracloud-bridge-init.dat");
    }

    /**
     * @return
     */
    private JaxbJsonSerializer<InitParams> getInitSerializer() {
        JaxbJsonSerializer<InitParams> serializer =
            new JaxbJsonSerializer<>(InitParams.class);
        return serializer;
    }

    /**
     * @param initParams
     */
    private void initBridgeConfiguration(InitParams initParams) {
        this.bridgeConfiguration.setDuracloudEmailAddresses(initParams.getDuracloudEmailAddresses());
        this.bridgeConfiguration.setDuracloudUsername(initParams.getDuracloudUsername());
        this.bridgeConfiguration.setDuracloudPassword(initParams.getDuracloudPassword());
        assert(BridgeConfiguration.getBridgeRootDir().exists());
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
        RestoreManagerConfig config = new RestoreManagerConfig();
        config.setRestorationRootDir(BridgeConfiguration.getContentRootDir().getAbsolutePath());
        config.setDpnEmailAddresses(initParams.getDpnEmailAddresses());
        config.setDuracloudEmailAddresses(initParams.getDuracloudEmailAddresses());
        config.setDuracloudUsername(initParams.getDuracloudUsername());
        config.setDuracloudPassword(initParams.getDuracloudPassword());

        this.restorationManager.init(config, jobManager);
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
        notifyConfig.setContentRoot(BridgeConfiguration.getContentRootDir());
        this.snapshotJobListener.init(notifyConfig);
        this.restoreJobListener.init(notifyConfig, initParams.getDaysToExpireRestore());
    }


    /**
     * @param initParams
     */
    private void initJobManager(InitParams initParams) throws AlreadyInitializedException{


        
        SnapshotJobManagerConfig jobManagerConfig = new SnapshotJobManagerConfig();
        jobManagerConfig.setDuracloudUsername(initParams.getDuracloudUsername());
        jobManagerConfig.setDuracloudPassword(initParams.getDuracloudPassword());
        jobManagerConfig.setContentRootDir(BridgeConfiguration.getContentRootDir());
        jobManagerConfig.setWorkDir(BridgeConfiguration.getBridgeWorkDir());
        this.jobManager.init(jobManagerConfig);
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
            String buildNumber = props.get("buildNumber").toString();
            return Response.ok().entity(
                "{\"version\":\"" + version +
                "\",\"build\":\"" + buildNumber + "\"}").build();
        } catch (IOException e) {
            //should never happen
            throw new RuntimeException(e);
        }
    }
}
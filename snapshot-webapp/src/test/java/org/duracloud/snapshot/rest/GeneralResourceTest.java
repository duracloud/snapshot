/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.rest;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import javax.ws.rs.core.Response;

import org.duracloud.appconfig.domain.NotificationConfig;
import org.duracloud.common.notification.NotificationManager;
import org.duracloud.snapshot.common.test.SnapshotTestBase;
import org.duracloud.snapshot.db.DatabaseConfig;
import org.duracloud.snapshot.db.DatabaseInitializer;
import org.duracloud.snapshot.manager.SnapshotJobManager;
import org.duracloud.snapshot.manager.config.SnapshotJobManagerConfig;
import org.duracloud.snapshot.manager.config.ExecutionListenerConfig;
import org.duracloud.snapshot.manager.spring.batch.SnapshotExecutionListener;
import org.duracloud.snapshot.service.RestorationManagerConfig;
import org.duracloud.snapshot.service.RestorationManager;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.Mock;
import org.easymock.TestSubject;
import org.junit.Assert;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Daniel Bernstein
 *         Date: Feb 4, 2014
 */

public class GeneralResourceTest extends SnapshotTestBase {
    
    private String databaseUser = "db-user";
    private String databasePassword = "db-pass";
    private String databaseURL = "db-url";
    private String awsAccessKey = "aws-access-key";
    private String awsSecretKey = "aws-secret-key";
    private String originatorEmailAddress = "orig-email";
    private String[] duracloudEmailAddresses = {"duracloud-email"};
    private String[] dpnEmailAddresses = {"dpn-email"};
    private String duracloudUsername = "duracloud-username";
    private String duracloudPassword = "duracloud-password";
    private File workDir = new File(System.getProperty("java.io.tmpdir"),
        "snapshot-work");
    private File contentDirRoot = new File(System.getProperty("java.io.tmpdir"),
        "snapshot-content");
    
    private boolean clean = true;

    @Mock
    private SnapshotJobManager manager;

    @Mock
    private RestorationManager restorationManager;

    @TestSubject
    private GeneralResource resource;
    @Mock
    private DatabaseInitializer initializer;
    @Mock
    private SnapshotExecutionListener executionListener;
    @Mock
    private NotificationManager notificationManager;
    
    /* (non-Javadoc)
     * @see org.duracloud.snapshot.common.test.EasyMockTestBase#setup()
     */
    @Override
    public void setup() {
        super.setup();
        resource = new GeneralResource(manager, restorationManager, initializer, executionListener, notificationManager);
    }
    
    @Test
    public void testInit() {
        Capture<DatabaseConfig> dbConfigCapture = new Capture<>();
        initializer.init(EasyMock.capture(dbConfigCapture));
        EasyMock.expectLastCall();

        Capture<ExecutionListenerConfig> notifyConfigCapture = new Capture<>();
        executionListener.init(EasyMock.capture(notifyConfigCapture));
        EasyMock.expectLastCall();

        Capture<SnapshotJobManagerConfig> duracloudConfigCapture = new Capture<>();
        manager.init(EasyMock.capture(duracloudConfigCapture));
        EasyMock.expectLastCall();
        
        Capture<RestorationManagerConfig> restorationConfigCapture = new Capture<>();
        restorationManager.init(EasyMock.capture(restorationConfigCapture));
        EasyMock.expectLastCall();

        Collection<NotificationConfig> collection = new ArrayList<>();
        this.notificationManager.initializeNotifiers(EasyMock.isA(collection.getClass()));
        EasyMock.expectLastCall();


        replayAll();

        InitParams initParams = createInitParams();
        
        resource.init(initParams);


        DatabaseConfig dbConfig = dbConfigCapture.getValue();
        assertEquals(databaseUser, dbConfig.getUsername());
        assertEquals(databasePassword, dbConfig.getPassword());
        assertEquals(databaseURL, dbConfig.getUrl());
        assertEquals(clean, dbConfig.isClean());

        ExecutionListenerConfig notifyConfig = notifyConfigCapture.getValue();
        assertEquals(awsAccessKey, notifyConfig.getSesUsername());
        assertEquals(awsSecretKey, notifyConfig.getSesPassword());
        assertEquals(originatorEmailAddress,
                     notifyConfig.getOriginatorEmailAddress());
        assertEquals(duracloudEmailAddresses[0],
                     notifyConfig.getDuracloudEmailAddresses()[0]);
        assertEquals(dpnEmailAddresses[0],
                     notifyConfig.getDpnEmailAddresses()[0]);

        SnapshotJobManagerConfig jobManagerConfig = duracloudConfigCapture.getValue();

        assertEquals(duracloudUsername, jobManagerConfig.getDuracloudUsername());
        assertEquals(duracloudPassword, jobManagerConfig.getDuracloudPassword());
        assertEquals(contentDirRoot, jobManagerConfig.getContentRootDir());
        assertEquals(workDir, jobManagerConfig.getWorkDir());

        
        RestorationManagerConfig restorationConfig = restorationConfigCapture.getValue();
        assertEquals(duracloudEmailAddresses[0],
                     restorationConfig.getDuracloudEmailAddresses()[0]);
        assertEquals(dpnEmailAddresses[0],
                     restorationConfig.getDpnEmailAddresses()[0]);

    }

    
    @Test
    public void testVersion() throws JsonParseException, IOException {

        replayAll();

        
        Response response = resource.version();

        String message = (String)response.getEntity();
        
        ObjectMapper mapper = new ObjectMapper();
        JsonFactory factory = mapper.getFactory(); 
        JsonParser jp = factory.createJsonParser(message);
        JsonNode obj = mapper.readTree(jp);
        Assert.assertNotNull(obj);
        Assert.assertNotNull(obj.get("version"));

        
    }

    /**
     * @return
     */
    private InitParams createInitParams() {
        InitParams initParams = new InitParams();
        initParams.setDatabaseUser(databaseUser);
        initParams.setDatabasePassword(databasePassword);
        initParams.setDatabaseURL(databaseURL);
        initParams.setClean(clean);
        initParams.setAwsAccessKey(awsAccessKey);
        initParams.setAwsSecretKey(awsSecretKey);
        initParams.setOriginatorEmailAddress(originatorEmailAddress);
        initParams.setDuracloudEmailAddresses(duracloudEmailAddresses);
        initParams.setDpnEmailAddresses(dpnEmailAddresses);
        initParams.setDuracloudUsername(duracloudUsername);
        initParams.setDuracloudPassword(duracloudPassword);
        initParams.setWorkDir(workDir.getAbsolutePath());
        initParams.setContentDirRoot(contentDirRoot.getAbsolutePath());
        return initParams;
    }



    /**
     * 
     */
    private void setupInitialize() {
        initializer.init(EasyMock.isA(DatabaseConfig.class));
        EasyMock.expectLastCall();

        executionListener.init(EasyMock.isA(ExecutionListenerConfig.class));
        EasyMock.expectLastCall();

        manager.init(EasyMock.isA(SnapshotJobManagerConfig.class));
        EasyMock.expectLastCall();

    }
}

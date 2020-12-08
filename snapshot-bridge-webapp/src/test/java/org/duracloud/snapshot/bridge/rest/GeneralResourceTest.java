/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.bridge.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.duracloud.common.notification.NotificationConfig;
import org.duracloud.common.notification.NotificationManager;
import org.duracloud.common.util.WaitUtil;
import org.duracloud.snapshot.common.test.SnapshotTestBase;
import org.duracloud.snapshot.db.DatabaseConfig;
import org.duracloud.snapshot.db.DatabaseInitializer;
import org.duracloud.snapshot.service.BridgeConfiguration;
import org.duracloud.snapshot.service.Finalizer;
import org.duracloud.snapshot.service.RestoreManager;
import org.duracloud.snapshot.service.RestoreManagerConfig;
import org.duracloud.snapshot.service.SnapshotJobManager;
import org.duracloud.snapshot.service.SnapshotJobManagerConfig;
import org.duracloud.snapshot.service.impl.ExecutionListenerConfig;
import org.duracloud.snapshot.service.impl.RestoreJobExecutionListener;
import org.duracloud.snapshot.service.impl.SnapshotJobExecutionListener;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.easymock.Mock;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Daniel Bernstein
 * Date: Feb 4, 2014
 */

public class GeneralResourceTest extends SnapshotTestBase {

    private String databaseUser = "db-user";
    private String databasePassword = "db-pass";
    private String databaseURL = "db-url";
    private String awsAccessKey = "aws-access-key";
    private String awsSecretKey = "aws-secret-key";
    private String originatorEmailAddress = "orig-email";
    private String[] duracloudEmailAddresses = {"duracloud-email"};
    private String[] targetStoreEmailAddresses = {"target-email"};
    private String duracloudUsername = "duracloud-username";
    private String duracloudPassword = "duracloud-password";
    private Integer finalizerPeriodMs = 1000;
    private int daysToExpire = 42;
    private File workDir = new File(System.getProperty("java.io.tmpdir"), "snapshot-work");

    private boolean clean = true;

    @Mock
    private PurgeObsoleteDataTask purgeTask;

    @Mock
    private SnapshotJobManager manager;

    @Mock
    private RestoreManager restorationManager;

    private GeneralResource resource;
    @Mock
    private DatabaseInitializer initializer;
    @Mock
    private SnapshotJobExecutionListener snapshotJobListener;
    @Mock
    private RestoreJobExecutionListener restoreJobListener;

    @Mock
    private NotificationManager notificationManager;

    @Mock
    private Finalizer finalizer;

    @Mock
    private BridgeConfiguration bridgeConfiguration;

    /* (non-Javadoc)
     * @see org.duracloud.snapshot.common.test.EasyMockTestBase#setup()
     */
    @Override
    public void setup() throws Exception {
        super.setup();
        resource =
            new GeneralResource(manager,
                                restorationManager,
                                initializer,
                                snapshotJobListener,
                                restoreJobListener,
                                notificationManager,
                                finalizer,
                                bridgeConfiguration,
                                purgeTask);

        System.setProperty(BridgeConfiguration.DURACLOUD_BRIDGE_ROOT_SYSTEM_PROPERTY,
                           this.workDir.getAbsolutePath());
    }

    @Test
    public void testInit() throws Exception {

        File file = resource.getStoreInitFile();
        if (file.exists()) {
            file.delete();
        }

        System.setProperty("root.password", "test");
        Capture<DatabaseConfig> dbConfigCapture = Capture.newInstance(CaptureType.FIRST);
        initializer.init(EasyMock.capture(dbConfigCapture));
        EasyMock.expectLastCall();

        Capture<ExecutionListenerConfig> snapshotListenerConfigCapture = Capture.newInstance(CaptureType.FIRST);
        snapshotJobListener.init(EasyMock.capture(snapshotListenerConfigCapture));
        EasyMock.expectLastCall();

        Capture<ExecutionListenerConfig> restoreListenerConfigCapture = Capture.newInstance(CaptureType.FIRST);
        restoreJobListener.init(EasyMock.capture(restoreListenerConfigCapture),
                                EasyMock.eq(daysToExpire));
        EasyMock.expectLastCall();

        finalizer.initialize(finalizerPeriodMs);
        EasyMock.expectLastCall();

        EasyMock.expect(manager.isInitialized()).andReturn(false);

        Capture<SnapshotJobManagerConfig> duracloudConfigCapture = Capture.newInstance(CaptureType.FIRST);
        manager.init(EasyMock.capture(duracloudConfigCapture));
        EasyMock.expectLastCall();

        Capture<RestoreManagerConfig> restorationConfigCapture = Capture.newInstance(CaptureType.FIRST);
        restorationManager.init(EasyMock.capture(restorationConfigCapture),
                                EasyMock.isA(SnapshotJobManager.class));
        EasyMock.expectLastCall();

        Collection<NotificationConfig> collection = new ArrayList<>();
        this.notificationManager.initializeNotifiers(EasyMock.isA(collection.getClass()));
        EasyMock.expectLastCall();

        bridgeConfiguration.setDuracloudUsername(duracloudUsername);
        EasyMock.expectLastCall();
        bridgeConfiguration.setDuracloudPassword(duracloudPassword);
        EasyMock.expectLastCall();
        bridgeConfiguration.setDuracloudEmailAddresses(duracloudEmailAddresses);
        EasyMock.expectLastCall();
        System.setProperty(BridgeConfiguration.DURACLOUD_BRIDGE_ROOT_SYSTEM_PROPERTY, this.workDir.getAbsolutePath());

        this.purgeTask.run();
        EasyMock.expectLastCall();

        replayAll();

        InitParams initParams = createInitParams();

        Response response = resource.init(initParams);
        WaitUtil.wait(2);
        assertEquals(202, response.getStatus());
        DatabaseConfig dbConfig = dbConfigCapture.getValue();
        assertEquals(databaseUser, dbConfig.getUsername());
        assertEquals(databasePassword, dbConfig.getPassword());
        assertEquals(databaseURL, dbConfig.getUrl());
        assertEquals(clean, dbConfig.isClean());

        ExecutionListenerConfig snapshotNotifyConfig =
            snapshotListenerConfigCapture.getValue();
        assertEquals(awsAccessKey, snapshotNotifyConfig.getSesUsername());
        assertEquals(awsSecretKey, snapshotNotifyConfig.getSesPassword());
        assertEquals(originatorEmailAddress,
                     snapshotNotifyConfig.getOriginatorEmailAddress());
        assertEquals(duracloudEmailAddresses[0],
                     snapshotNotifyConfig.getDuracloudEmailAddresses()[0]);
        assertEquals(targetStoreEmailAddresses[0],
                     snapshotNotifyConfig.getTargetStoreEmailAddresses()[0]);

        ExecutionListenerConfig restoreNotifyConfig =
            restoreListenerConfigCapture.getValue();
        assertEquals(awsAccessKey, restoreNotifyConfig.getSesUsername());
        assertEquals(awsSecretKey, restoreNotifyConfig.getSesPassword());
        assertEquals(originatorEmailAddress,
                     restoreNotifyConfig.getOriginatorEmailAddress());
        assertEquals(duracloudEmailAddresses[0],
                     restoreNotifyConfig.getDuracloudEmailAddresses()[0]);
        assertEquals(targetStoreEmailAddresses[0],
                     restoreNotifyConfig.getTargetStoreEmailAddresses()[0]);

        SnapshotJobManagerConfig jobManagerConfig = duracloudConfigCapture.getValue();

        assertEquals(duracloudUsername, jobManagerConfig.getDuracloudUsername());
        assertEquals(duracloudPassword, jobManagerConfig.getDuracloudPassword());
        assertEquals(BridgeConfiguration.getBridgeWorkDir(), jobManagerConfig.getWorkDir());
        assertEquals(new File(this.workDir, "content"), jobManagerConfig.getContentRootDir());

        RestoreManagerConfig restorationConfig = restorationConfigCapture.getValue();
        assertEquals(duracloudEmailAddresses[0],
                     restorationConfig.getDuracloudEmailAddresses()[0]);
        assertEquals(targetStoreEmailAddresses[0],
                     restorationConfig.getTargetStoreEmailAddresses()[0]);

        InitParams params = resource.getStoredInitParams();

        //verify that the state was saved successfully
        //and that the database clean flag was flipped to false.
        assertNotNull(params);
        assertEquals(false, params.isClean());
    }

    @Test
    public void testAlreadyInitialized() {
        EasyMock.expect(manager.isInitialized()).andReturn(true);
        replayAll();
        InitParams initParams = createInitParams();
        Response response = resource.init(initParams);
        assertEquals(500, response.getStatus());
        assertTrue(((ResponseDetails)
                        response.getEntity()).getMessage()
                                             .contains("already"));
    }

    @Test
    public void testVersion() throws JsonParseException, IOException {
        replayAll();

        Response response = resource.version();

        String message = (String) response.getEntity();

        ObjectMapper mapper = new ObjectMapper();
        JsonFactory factory = mapper.getFactory();
        JsonParser jp = factory.createJsonParser(message);
        JsonNode obj = mapper.readTree(jp);
        Assert.assertNotNull(obj);
        Assert.assertNotNull(obj.get("version"));
        Assert.assertNotNull(obj.get("build"));
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
        initParams.setTargetStoreEmailAddresses(targetStoreEmailAddresses);
        initParams.setDuracloudUsername(duracloudUsername);
        initParams.setDuracloudPassword(duracloudPassword);
        initParams.setFinalizerPeriodMs(finalizerPeriodMs);
        initParams.setDaysToExpireRestore(daysToExpire);
        return initParams;
    }

}

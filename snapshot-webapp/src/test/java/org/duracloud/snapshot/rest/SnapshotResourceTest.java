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

import org.duracloud.snapshot.common.test.SnapshotTestBase;
import org.duracloud.snapshot.spring.batch.DatabaseInitializer;
import org.duracloud.snapshot.spring.batch.SnapshotException;
import org.duracloud.snapshot.spring.batch.SnapshotExecutionListener;
import org.duracloud.snapshot.spring.batch.SnapshotJobManager;
import org.duracloud.snapshot.spring.batch.SnapshotNotFoundException;
import org.duracloud.snapshot.spring.batch.SnapshotStatus;
import org.duracloud.snapshot.spring.batch.config.DatabaseConfig;
import org.duracloud.snapshot.spring.batch.config.SnapshotConfig;
import org.duracloud.snapshot.spring.batch.config.SnapshotJobManagerConfig;
import org.duracloud.snapshot.spring.batch.config.SnapshotNotifyConfig;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.Mock;
import org.easymock.TestSubject;
import org.junit.Test;

/**
 * @author Daniel Bernstein
 *         Date: Feb 4, 2014
 */

public class SnapshotResourceTest extends SnapshotTestBase {
    
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
    private File workDir = new File(System.getProperty("java.io.tmpdir")
        + "snapshot-work");
    private File contentDirRoot = new File(System.getProperty("java.io.tmpdir")
        + "snapshot-content");
    
    private boolean clean = true;

    @Mock
    private SnapshotJobManager manager;
    @TestSubject
    private SnapshotResource resource;
    @Mock
    private DatabaseInitializer initializer;
    @Mock
    private SnapshotExecutionListener executionListener;
    
    /* (non-Javadoc)
     * @see org.duracloud.snapshot.common.test.EasyMockTestBase#setup()
     */
    @Override
    public void setup() {
        super.setup();
        resource = new SnapshotResource(manager, initializer, executionListener);
    }
    
    @Test
    public void testInit() {
        Capture<DatabaseConfig> dbConfigCapture = new Capture<>();
        initializer.init(EasyMock.capture(dbConfigCapture));
        EasyMock.expectLastCall();

        Capture<SnapshotNotifyConfig> notifyConfigCapture = new Capture<>();
        executionListener.initialize(EasyMock.capture(notifyConfigCapture));
        EasyMock.expectLastCall();

        Capture<SnapshotJobManagerConfig> duracloudConfigCapture = new Capture<>();
        manager.init(EasyMock.capture(duracloudConfigCapture));
        EasyMock.expectLastCall();

        replayAll();

        InitParams initParams = createInitParams();
        
        resource.init(initParams);

        DatabaseConfig dbConfig = dbConfigCapture.getValue();
        assertEquals(databaseUser, dbConfig.getUsername());
        assertEquals(databasePassword, dbConfig.getPassword());
        assertEquals(databaseURL, dbConfig.getUrl());
        assertEquals(clean, dbConfig.isClean());

        SnapshotNotifyConfig notifyConfig = notifyConfigCapture.getValue();
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

    @Test
    public void testGetStatusSuccess() throws SnapshotException {
        EasyMock.expect(manager.getStatus(EasyMock.isA(String.class)))
                .andReturn(new SnapshotStatus("snapshotId", "testStatus"));
        replayAll();
        resource.getStatus("snapshotId");
    }

    @Test
    public void testGetStatusNotFound() throws SnapshotException {
        EasyMock.expect(manager.getStatus(EasyMock.isA(String.class)))
                .andThrow(new SnapshotNotFoundException("test"));
        replayAll();
        resource.getStatus("snapshotId");
    }

    @Test
    public void testCreate() throws SnapshotException {
        setupInitialize();
        String host = "host";
        String port = "444";
        String storeId = "storeId";
        String spaceId = "spaceId";
        String snapshotId = "snapshotId";
        
        Capture<SnapshotConfig> snapshotConfigCapture = new Capture<>();
        EasyMock.expect(manager.executeSnapshotAsync(
                     EasyMock.capture(snapshotConfigCapture)))
                .andReturn(new SnapshotStatus("test","test"));

        
        replayAll();
        resource.init(createInitParams());
        resource.create(host, port, storeId, spaceId, snapshotId);

        SnapshotConfig snapshotConfig = snapshotConfigCapture.getValue();
        assertEquals(host, snapshotConfig.getHost());
        assertEquals(Integer.parseInt(port), snapshotConfig.getPort());
        assertEquals(storeId, snapshotConfig.getStoreId());
        assertEquals(spaceId, snapshotConfig.getSpace());
        assertEquals(snapshotId, snapshotConfig.getSnapshotId());
    }

    /**
     * 
     */
    private void setupInitialize() {
        initializer.init(EasyMock.isA(DatabaseConfig.class));
        EasyMock.expectLastCall();

        executionListener.initialize(EasyMock.isA(SnapshotNotifyConfig.class));
        EasyMock.expectLastCall();

        manager.init(EasyMock.isA(SnapshotJobManagerConfig.class));
        EasyMock.expectLastCall();

    }
}

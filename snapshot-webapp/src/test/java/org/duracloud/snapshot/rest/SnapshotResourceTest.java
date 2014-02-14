/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.rest;

import org.duracloud.snapshot.spring.batch.DatabaseInitializer;
import org.duracloud.snapshot.spring.batch.SnapshotException;
import org.duracloud.snapshot.spring.batch.SnapshotExecutionListener;
import org.duracloud.snapshot.spring.batch.SnapshotJobManager;
import org.duracloud.snapshot.spring.batch.SnapshotNotFoundException;
import org.duracloud.snapshot.spring.batch.SnapshotStatus;
import org.duracloud.snapshot.spring.batch.config.SnapshotNotifyConfig;
import org.duracloud.snapshot.spring.batch.driver.DatabaseConfig;
import org.duracloud.snapshot.spring.batch.driver.SnapshotConfig;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Daniel Bernstein
 *         Date: Feb 4, 2014
 */
public class SnapshotResourceTest extends EasyMockTestBase {
    
    private SnapshotJobManager manager;
    private SnapshotResource resource;
    private DatabaseInitializer initializer;
    private SnapshotExecutionListener executionListener;
    
    @Before
    public void setup() {
        manager = createMock(SnapshotJobManager.class);
        initializer = createMock(DatabaseInitializer.class);
        executionListener = createMock(SnapshotExecutionListener.class);

        resource = new SnapshotResource(manager,initializer, executionListener);
    }

    @Test
    public void testInit() {
        Capture<DatabaseConfig> dbConfigCapture = new Capture<>();
        initializer.init(EasyMock.capture(dbConfigCapture));
        EasyMock.expectLastCall();

        Capture<SnapshotNotifyConfig> notifyConfigCapture = new Capture<>();
        executionListener.initialize(EasyMock.capture(notifyConfigCapture));
        EasyMock.expectLastCall();

        manager.init();
        EasyMock.expectLastCall();

        replay();

        String databaseUser = "db-user";
        String databasePassword = "db-pass";
        String databaseURL = "db-url";
        boolean clean = true;

        InitParams initParams = new InitParams();
        initParams.setDatabaseUser(databaseUser);
        initParams.setDatabasePassword(databasePassword);
        initParams.setDatabaseURL(databaseURL);
        initParams.setClean(clean);

        String awsAccessKey = "aws-access-key";
        String awsSecretKey = "aws-secret-key";
        String originatorEmailAddress = "orig-email";
        String[] duracloudEmailAddresses = {"duracloud-email"};
        String[] dpnEmailAddresses = {"dpn-email"};

        initParams.setAwsAccessKey(awsAccessKey);
        initParams.setAwsSecretKey(awsSecretKey);
        initParams.setOriginatorEmailAddress(originatorEmailAddress);
        initParams.setDuracloudEmailAddresses(duracloudEmailAddresses);
        initParams.setDpnEmailAddresses(dpnEmailAddresses);

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
    }

    @Test
    public void testGetStatusSuccess() throws SnapshotException {
        EasyMock.expect(manager.getStatus(EasyMock.isA(String.class)))
                .andReturn(new SnapshotStatus("snapshotId", "testStatus"));
        replay();
        resource.getStatus("snapshotId");
    }

    @Test
    public void testGetStatusNotFound() throws SnapshotException {
        EasyMock.expect(manager.getStatus(EasyMock.isA(String.class)))
                .andThrow(new SnapshotNotFoundException("test"));
        replay();
        resource.getStatus("snapshotId");
    }

    @Test
    public void testCreate() throws SnapshotException {
        EasyMock.expect(manager.executeSnapshotAsync(
                     EasyMock.isA(SnapshotConfig.class)))
                .andReturn(new SnapshotStatus("test","test"));
        replay();
        resource.create("host", "444", "storeId", "spaceId", "snapshotId");
    }
}

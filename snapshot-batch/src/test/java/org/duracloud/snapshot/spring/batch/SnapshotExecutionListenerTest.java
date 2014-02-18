/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.spring.batch;

import org.duracloud.common.notification.NotificationManager;
import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.easymock.Mock;
import org.easymock.TestSubject;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.batch.core.JobExecution;

/**
 * @author Bill Branan
 *         Date: 2/18/14
 */
@RunWith(EasyMockRunner.class)
public class SnapshotExecutionListenerTest extends EasyMockSupport {

    @Mock
    private NotificationManager notificationManager;
    @Mock
    private JobExecution jobExecution;

    @TestSubject
    private SnapshotExecutionListener executionListener =
        new SnapshotExecutionListener(notificationManager);

    @After
    public void tearDown() {
        verifyAll();
    }

    @Test
    public void testAfterJob() {
        

        replayAll();
        System.out.println("Test");
    }

}

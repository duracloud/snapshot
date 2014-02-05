/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.text.MessageFormat;
import java.util.List;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.TestProperties;
import org.junit.Test;

/**
 * @author Daniel Bernstein
 *         Date: Feb 5, 2014
 */
public class TestSnapshotRest extends JerseyTest {

    @Override
    protected ResourceConfig configure() {
        enable(TestProperties.LOG_TRAFFIC);
        enable(TestProperties.DUMP_ENTITY);

        return new Application();
    }

    @Override
    protected void configureClient(ClientConfig config) {
        config.register(new JacksonFeature()).register(SnapshotObjectMapperProvider.class);
    }


    @Test
    public void testVersion() {
        WebTarget target = target();
        String responseMsg = target.path("snapshot/version").request(MediaType.APPLICATION_JSON).get(String.class);
        assertNotNull(responseMsg);
    }
    
    @Test
    public void testList() {
        WebTarget target = target();
        List responseMsg = (List)target.path("snapshot/list").request(MediaType.APPLICATION_JSON).get(List.class);
        assertNotNull(responseMsg);
        assertTrue(responseMsg.size() > 0);
    }

    @Test
    public void testGet() {
        WebTarget target = target();
        String id = "1";
        SnapshotStatus responseMsg =
            target.path("snapshot/" + id)
                  .request(MediaType.APPLICATION_JSON)
                  .get(SnapshotStatus.class);
        assertNotNull(responseMsg);
        assertEquals(id, responseMsg.getId());
        assertNotNull(responseMsg.getStatus());
    }

    @Test
    public void testCreate() {
        WebTarget target = target();
        String host = "host";
        String port = "443";
        String storeId = "1";
        String spaceId = "space";
        String snapshotId = "1234";
        String path =
            MessageFormat.format("snapshot/{0}/{1}/{2}/{3}/{4}",
                                 host,
                                 port,
                                 storeId,
                                 spaceId,
                                 snapshotId);
        SnapshotStatus responseMsg =
            target.path(path)
                  .request(MediaType.APPLICATION_JSON)
                  .post(null, SnapshotStatus.class);
        assertNotNull(responseMsg);
        assertEquals(snapshotId, responseMsg.getId());
        assertNotNull(responseMsg.getStatus());
    }

}

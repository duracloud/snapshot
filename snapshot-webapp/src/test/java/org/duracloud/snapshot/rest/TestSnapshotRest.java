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

import java.io.InputStream;
import java.text.MessageFormat;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

import org.codehaus.jackson.map.ObjectMapper;
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

    private WebTarget target;
    /* (non-Javadoc)
     * @see org.glassfish.jersey.test.JerseyTest#setUp()
     */
    @Override
    public void setUp() throws Exception {
        super.setUp();

        InputStream is = getClass().getResourceAsStream("test-init.json");
        ObjectMapper mapper = new ObjectMapper();
        InitParams params = mapper.readValue(is, InitParams.class);
        Entity<InitParams> entity = Entity.entity(params, MediaType.APPLICATION_JSON);
        ResponseDetails details =
            target.path("init")
                  .request(MediaType.APPLICATION_JSON)
                  .post(entity, ResponseDetails.class);
        assertNotNull(details);

        target = target();

        
        
    }
    
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
        Builder b =  target.path("version").request(MediaType.APPLICATION_JSON);
        String response = b.get(String.class);
        assertNotNull(response);
        
    }


    /*
    @Test
    public void testList() {
        List responseMsg =
            (List) target.path("list")
                         .request(MediaType.APPLICATION_JSON)
                         .get(List.class);
        assertNotNull(responseMsg);
        assertTrue(responseMsg.size() > 0);
    }
    */

    @Test
    public void testGet() {
        String id = "1";
        SnapshotStatus responseMsg =
            target.path(id)
                  .request(MediaType.APPLICATION_JSON)
                  .get(SnapshotStatus.class);
        assertNotNull(responseMsg);
        assertEquals(id, responseMsg.getId());
        assertNotNull(responseMsg.getStatus());
    }

    @Test
    public void testCreate() {
        String host = "host";
        String port = "443";
        String storeId = "1";
        String spaceId = "space";
        String snapshotId = "1234";
        String path =
            MessageFormat.format("{0}/{1}/{2}/{3}/{4}",
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

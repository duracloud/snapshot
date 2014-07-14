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

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.duracloud.snapshot.db.model.SnapshotStatus;
import org.duracloud.snapshot.manager.SnapshotSummary;
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
        target = target();
    }

    /**
     * @throws IOException
     * @throws JsonParseException
     * @throws JsonMappingException
     */
    private void doInit()
        throws IOException,
            JsonParseException,
            JsonMappingException {
        InputStream is = getClass().getResourceAsStream("/test-init.json");
        ObjectMapper mapper = new ObjectMapper();
        InitParams params = mapper.readValue(is, InitParams.class);
        Entity<InitParams> entity = Entity.entity(params, MediaType.APPLICATION_JSON);
        ResponseDetails details =
            target.path("init")
                  .request(MediaType.APPLICATION_JSON)
                  .post(entity, ResponseDetails.class);
        assertNotNull(details);
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
        Map response = b.get(Map.class);
        
        assertNotNull(response);
        assertNotNull(response.get("version"));
        
    }


    @Test
    public void testList() {
        List<SnapshotSummary> responseMsg =
            (List<SnapshotSummary>) target.path("list")
                         .request(MediaType.APPLICATION_JSON)
                         .get(List.class);
        assertNotNull(responseMsg);
    }

    @Test
    public void test() throws Exception {
        doInit();

        Properties props = new Properties();
        props.load(getClass().getResourceAsStream("/test.properties"));
        String host = props.getProperty("host");
        String port = props.getProperty("port");
        String storeId = props.getProperty("storeId");
        String spaceId = props.getProperty("spaceId");
        String snapshotId = System.currentTimeMillis()+"";
        String description = "description";
        SnapshotRequestParams params =
            new SnapshotRequestParams(host, port, storeId, spaceId, description);
        Entity<SnapshotRequestParams> entity =
            Entity.entity(params, MediaType.APPLICATION_JSON);        
        
        SnapshotStatus responseMsg =
            target.path("snapshots/"+snapshotId)
                  .request(MediaType.APPLICATION_JSON)
                  .put(entity, SnapshotStatus.class);
        assertNotNull(responseMsg);
        
        Thread.sleep(5*1000);
        responseMsg =
            target.path(snapshotId)
                  .request(MediaType.APPLICATION_JSON)
                  .get(SnapshotStatus.class);
        assertNotNull(responseMsg);
        assertNotNull(responseMsg);
    }



}

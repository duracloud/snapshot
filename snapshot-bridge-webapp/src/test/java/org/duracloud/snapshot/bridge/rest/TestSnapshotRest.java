/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.bridge.rest;

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

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.duracloud.snapshot.dto.SnapshotStatus;
import org.duracloud.snapshot.dto.SnapshotSummary;
import org.duracloud.snapshot.dto.bridge.CreateSnapshotBridgeParameters;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.TestProperties;
import org.junit.Test;

/**
 * @author Daniel Bernstein
 * Date: Feb 5, 2014
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
        Builder b = target.path("version").request(MediaType.APPLICATION_JSON);
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
        String email = props.getProperty("email");
        String memberID = props.getProperty("uuid");

        String snapshotId = System.currentTimeMillis() + "";
        String description = "description";
        CreateSnapshotBridgeParameters params =
            new CreateSnapshotBridgeParameters(host, port, storeId, spaceId, description, email, memberID);
        Entity<CreateSnapshotBridgeParameters> entity =
            Entity.entity(params, MediaType.APPLICATION_JSON);

        SnapshotStatus responseMsg =
            target.path("snapshot/" + snapshotId)
                  .request(MediaType.APPLICATION_JSON)
                  .put(entity, SnapshotStatus.class);
        assertNotNull(responseMsg);

        Thread.sleep(5 * 1000);
        responseMsg =
            target.path(snapshotId)
                  .request(MediaType.APPLICATION_JSON)
                  .get(SnapshotStatus.class);
        assertNotNull(responseMsg);
        assertNotNull(responseMsg);
    }

}

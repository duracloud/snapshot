/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */

package org.duracloud.snapshot.db;

import static com.wix.mysql.EmbeddedMysql.anEmbeddedMysql;
import static com.wix.mysql.config.Charset.UTF8;
import static com.wix.mysql.config.MysqldConfig.aMysqldConfig;
import static com.wix.mysql.distribution.Version.v5_7_latest;

import java.util.concurrent.TimeUnit;

import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.wix.mysql.EmbeddedMysql;
import com.wix.mysql.ScriptResolver;
import com.wix.mysql.config.MysqldConfig;

/**
 * @author Daniel Bernstein 
 *         Date: June 8, 2017
 */
@RunWith(EasyMockRunner.class)
public abstract class JpaIntegrationTestBase extends EasyMockSupport {

    private EmbeddedMysql mysqld = null;

    protected AnnotationConfigApplicationContext context;

    @Before
    public void setup() {
        int port = 3310;
        MysqldConfig config = aMysqldConfig(v5_7_latest).withCharset(UTF8).withPort(port).withUser("user", "pass")
                .withTimeZone("GMT").withTimeout(2, TimeUnit.MINUTES).withServerVariable("max_connect_errors", 666)
                .withServerVariable("log_syslog", 0)
                .build();

        mysqld = anEmbeddedMysql(config).addSchema("snapshot", ScriptResolver.classPathScript("db_init.sql")).start();

        System.setProperty("generate.database", "true");
        System.setProperty("snapshot.db.port", port+"");

        context = new AnnotationConfigApplicationContext("org.duracloud.snapshot.db");

    }

    @After
    public void tearDown() {
        mysqld.stop();
    }

}

/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */

package org.duracloud.snapshot.db;

import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.runner.RunWith;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.testcontainers.containers.MySQLContainer;

/**
 * @author Daniel Bernstein
 * Date: June 8, 2017
 */
@RunWith(EasyMockRunner.class)
public abstract class JpaIntegrationTestBase extends EasyMockSupport {

    @ClassRule
    public static MySQLContainer<?> mysql = new MySQLContainer<>()
        .withUsername("user")
        .withPassword("pass")
        .withDatabaseName("snapshot")
        .withInitScript("db_init.sql")
        .withEnv("TZ", "GMT")
        .withEnv("max_connect_errors", "666");

    protected static AnnotationConfigApplicationContext context;

    @BeforeClass
    public static void setup() {
        System.setProperty("generate.database", "true");
        System.setProperty("snapshot.db.port", mysql.getFirstMappedPort().toString());

        context = new AnnotationConfigApplicationContext("org.duracloud.snapshot.db");
    }

    @AfterClass
    public static void tearDown() {
        context.close();
        mysql.close();
    }

}

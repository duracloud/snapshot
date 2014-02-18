/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.spring.batch;

import org.duracloud.snapshot.spring.batch.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.DatabasePopulator;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/**
 * This class is responsible for initializing the database.  This class has been
 * added as a replacement for jdbc:initialize tags in the spring config in order to 
 * enable us to lazily initialize the database.
 * @author Daniel Bernstein
 *         Date: Feb 12, 2014
 */
public class DatabaseInitializer {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(DatabaseInitializer.class);

    private Resource dropSchema;

    private Resource schema;
    
    private DriverManagerDataSource dataSource;
    
    public DatabaseInitializer(DriverManagerDataSource dataSource, Resource dropSchema, Resource schema){
        this.dataSource = dataSource;
        this.dropSchema = dropSchema;
        this.schema = schema;
    }

    public void init(DatabaseConfig databaseConfig) {
        final DataSourceInitializer initializer = new DataSourceInitializer();
        dataSource.setUrl(databaseConfig.getUrl());
        dataSource.setUsername(databaseConfig.getUsername());
        dataSource.setPassword(databaseConfig.getPassword());
        initializer.setDataSource(dataSource);
        initializer.setDatabasePopulator(databasePopulator(databaseConfig));

        try {
            initializer.afterPropertiesSet();
        } catch (Exception e) {
            Throwable rootCause = getRootCause(e);

            // The database initialization SQL scripts create the necessary
            // tables.  If the exception indicates that the database already
            // contains tables then ignore the exception and continue on,
            // otherwise throw the exception.
            if(rootCause.getMessage().contains("already exists")) {
                LOGGER.info("Database initialization - tables already exist: {}",
                            rootCause.getMessage());
            } else {
                throw e;
            }
        }
    }

    private DatabasePopulator databasePopulator(DatabaseConfig databaseConfig) {
        final ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        if(databaseConfig.isClean()){
            populator.addScript(dropSchema);
        }
        populator.addScript(schema);
        return populator;
    }

    private Throwable getRootCause(Throwable throwable) {
        if(throwable.getCause() != null) {
            return getRootCause(throwable.getCause());
        }
        return throwable;
    }
}

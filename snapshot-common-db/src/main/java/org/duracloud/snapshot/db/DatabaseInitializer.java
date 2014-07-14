/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.db;

import java.util.List;

import org.apache.commons.dbcp.BasicDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.Resource;
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
public class DatabaseInitializer implements ApplicationContextAware{

    private static final Logger LOGGER =
        LoggerFactory.getLogger(DatabaseInitializer.class);

    private List<Resource> dropSchemas;

    private List<Resource> schemas;
    
    private BasicDataSource dataSource;
    
    private ApplicationContext context;
    public DatabaseInitializer(BasicDataSource dataSource, List<Resource> dropSchemas, List<Resource> schemas){
        this.dataSource = dataSource;
        this.dropSchemas = dropSchemas;
        this.schemas = schemas;
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
    
    /* (non-Javadoc)
     * @see org.springframework.context.ApplicationContextAware#setApplicationContext(org.springframework.context.ApplicationContext)
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext)
        throws BeansException {
        this.context = applicationContext;
    }

    private DatabasePopulator databasePopulator(DatabaseConfig databaseConfig) {
        final ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        if(databaseConfig.isClean()){
            for(Resource schema : dropSchemas){
                populator.addScript(schema);
            }
        }

        for(Resource schema : schemas){
            populator.addScript(schema);
        }

        return populator;
    }

    private Throwable getRootCause(Throwable throwable) {
        if(throwable.getCause() != null) {
            return getRootCause(throwable.getCause());
        }
        return throwable;
    }
}

/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.spring.batch;

import javax.sql.DataSource;

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
public class DatabaseInitializer {

    private Resource dropSchema;

    private Resource schema;
    
    private DataSource dataSource;
    
    public DatabaseInitializer(DataSource dataSource, Resource dropSchema, Resource schema){
        this.dataSource = dataSource;
        this.dropSchema = dropSchema;
        this.schema = schema;
    }

    public void init() {
        final DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);
        initializer.setDatabasePopulator(databasePopulator());
        initializer.afterPropertiesSet();
    }

    private DatabasePopulator databasePopulator() {
        final ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(dropSchema);
        populator.addScript(schema);
        return populator;
    }
}

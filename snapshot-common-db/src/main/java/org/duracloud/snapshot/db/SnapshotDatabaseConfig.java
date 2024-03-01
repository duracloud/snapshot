/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.db;

import java.text.MessageFormat;
import java.util.Properties;
import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import org.apache.commons.dbcp2.BasicDataSource;
import org.duracloud.common.db.jpa.JpaConfigurationUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * @author Daniel Bernstein
 */
@Configuration
@EnableJpaRepositories(basePackages = {"org.duracloud.snapshot.db"},
                       entityManagerFactoryRef = SnapshotDatabaseConfig.ENTITY_MANAGER_FACTORY_BEAN,
                       transactionManagerRef = SnapshotDatabaseConfig.TRANSACTION_MANAGER_BEAN)
@EnableTransactionManagement
public class SnapshotDatabaseConfig {
    public static final String SNAPSHOT_REPO_DATA_SOURCE_BEAN =
        "dataSource";
    public static final String TRANSACTION_MANAGER_BEAN = "transactionManager";
    public static final String ENTITY_MANAGER_FACTORY_BEAN = "entityManagerFactory";

    @Autowired
    private Environment env;

    @Bean(name = SNAPSHOT_REPO_DATA_SOURCE_BEAN, destroyMethod = "close")
    public BasicDataSource snapshotDataSource() {
        BasicDataSource dataSource = new BasicDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(MessageFormat.format("jdbc:mysql://{0}:{1}/{2}" +
                                               "?useLegacyDatetimeCode=false" +
                                               "&serverTimezone=GMT" +
                                               "&characterEncoding=utf8" +
                                               "&characterSetResults=utf8",
                                               env.getProperty("snapshot.db.host", "localhost"),
                                               env.getProperty("snapshot.db.port", "3306"),
                                               env.getProperty("snapshot.db.name", "snapshot")));
        dataSource.setUsername(env.getProperty("snapshot.db.user", "user"));
        dataSource.setPassword(env.getProperty("snapshot.db.pass", "pass"));
        //ensure connection pool does not limit database connection creation
        dataSource.setMaxTotal(-1);
        dataSource.setTestOnBorrow(true);
        dataSource.setValidationQuery("SELECT 1");
        dataSource.setValidationQueryTimeout(10);
        dataSource.addConnectionProperty("hibernate.connection.release_mode", "after_transaction");
        return dataSource;
    }

    @Bean(name = TRANSACTION_MANAGER_BEAN)
    @Primary
    public PlatformTransactionManager snapshotTransactionManager(
        @Qualifier(ENTITY_MANAGER_FACTORY_BEAN) EntityManagerFactory entityManagerFactory) {

        JpaTransactionManager tm =
            new JpaTransactionManager(entityManagerFactory);
        tm.setJpaDialect(new HibernateJpaDialect());
        return tm;
    }

    @Bean(name = ENTITY_MANAGER_FACTORY_BEAN)
    public LocalContainerEntityManagerFactoryBean snapshotRepoEntityManagerFactory(
        @Qualifier(SNAPSHOT_REPO_DATA_SOURCE_BEAN) DataSource dataSource) {

        LocalContainerEntityManagerFactoryBean emf =
            new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(dataSource);
        emf.setPersistenceUnitName("snapshot-repo-pu");
        emf.setPackagesToScan("org.duracloud.snapshot");

        JpaConfigurationUtil.configureEntityManagerFactory(env, emf);

        if (Boolean.parseBoolean(env.getProperty("generate.database", "false"))) {
            Properties properties = new Properties();
            properties.setProperty("javax.persistence.schema-generation.database.action", "create");
            emf.setJpaProperties(properties);
        }

        return emf;
    }

}

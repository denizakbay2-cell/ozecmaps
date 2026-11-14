package com.citynavigator;

import jakarta.annotation.sql.DataSourceDefinition;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * The PostgreSQL connection pool. Payara replaces the {@code ${ENV=...}} placeholders with environment variables
 * at deploy time, so connection details and the password never appear in the code or the WAR.
 */
@DataSourceDefinition(
        name = DataSourceConfig.JNDI_NAME,
        className = "org.postgresql.ds.PGSimpleDataSource",
        url = "${ENV=DB_URL}",
        user = "${ENV=DB_USER}",
        password = "${ENV=DB_PASSWORD}",
        minPoolSize = 1,
        maxPoolSize = 8,
        properties = {"reWriteBatchedInserts=true", "ApplicationName=city-navigator"})
@ApplicationScoped
public class DataSourceConfig {

    public static final String JNDI_NAME = "java:app/jdbc/cityNavigator";
}

package com.citynavigator;

/**
 * The JNDI name of the PostgreSQL connection pool. The pool itself is defined in
 * {@code src/main/webapp/WEB-INF/glassfish-resources.xml} (rather than with a {@code @DataSourceDefinition}) so that it
 * can validate its connections and recover when the free database suspends its compute; connection details and the
 * password come from the environment, so they never appear in the code or the WAR.
 */
public final class DataSourceConfig {

    public static final String JNDI_NAME = "java:app/jdbc/cityNavigator";

    private DataSourceConfig() {
    }
}

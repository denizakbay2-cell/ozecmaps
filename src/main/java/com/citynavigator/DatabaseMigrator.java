package com.citynavigator;

import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;

/**
 * Brings the schema up to date with Flyway ({@code src/main/resources/db/migration}).
 */
@ApplicationScoped
public class DatabaseMigrator {

    static final String LOCATION = "classpath:db/migration";
    private static final Logger LOGGER = Logger.getLogger(DatabaseMigrator.class.getName());

    @Resource(lookup = DataSourceConfig.JNDI_NAME)
    private DataSource dataSource;

    public void migrate() {
        MigrateResult result = Flyway.configure()
                .dataSource(dataSource)
                .locations(LOCATION)
                .load()
                .migrate();
        LOGGER.info(() -> "database schema is at version " + result.targetSchemaVersion
                + ", " + result.migrationsExecuted + " migration(s) applied");
    }
}

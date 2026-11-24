package com.citynavigator;

import com.citynavigator.service.DemoIncidents;
import com.citynavigator.service.NationalEngine;
import com.citynavigator.service.NetworkImportService;
import com.citynavigator.service.RoutingEngine;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Prepares everything before routes can be served: schema, road network, routing graph, the optional national car
 * graph and, with {@code DEMO_DATA=true}, a few sample incidents.
 *
 * <p>This work runs in the background, so the web layer is deployed at once and the platform's liveness check
 * ({@code /api/health}) passes immediately. The first start imports the bundled road network into the database, which
 * can take minutes against a remote database; until it finishes, routes answer 503 ({@code /api/ping} reports
 * {@code LOADING}) and the interface shows that the server is still starting. Later starts find the data already there
 * and are quick. Without the national graph the application still works, with the nationwide routing service outside
 * its detailed area; if a step fails, it is logged and the application stays up but cannot route until the next start.
 */
@ApplicationScoped
public class ApplicationStartup {

    private static final Logger LOGGER = Logger.getLogger(ApplicationStartup.class.getName());

    private DatabaseMigrator migrator;
    private NetworkImportService networkImport;
    private RoutingEngine routingEngine;
    private NationalEngine nationalEngine;
    private DemoIncidents demoIncidents;

    protected ApplicationStartup() {
        // for the CDI proxy
    }

    @Inject
    public ApplicationStartup(DatabaseMigrator migrator, NetworkImportService networkImport,
                              RoutingEngine routingEngine, NationalEngine nationalEngine,
                              DemoIncidents demoIncidents) {
        this.migrator = migrator;
        this.networkImport = networkImport;
        this.routingEngine = routingEngine;
        this.nationalEngine = nationalEngine;
        this.demoIncidents = demoIncidents;
    }

    void onStart(@Observes @Initialized(ApplicationScoped.class) Object applicationStarted) {
        Thread worker = new Thread(this::prepare, "startup-prepare");
        worker.setDaemon(true);
        worker.start();
    }

    /** The heavy preparation; package-visible so the ordering can be tested without the background thread. */
    void prepare() {
        try {
            migrator.migrate();
            networkImport.importBundledAreaIfEmpty();
            routingEngine.load();
            nationalEngine.load();
            if (Boolean.parseBoolean(System.getenv("DEMO_DATA"))) {
                demoIncidents.addIfNone();
            }
            LOGGER.info("startup preparation finished; routes are ready");
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "startup preparation failed; the application is up but cannot route yet", e);
        }
    }
}

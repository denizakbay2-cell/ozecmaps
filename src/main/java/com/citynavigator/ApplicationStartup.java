package com.citynavigator;

import com.citynavigator.service.DemoIncidents;
import com.citynavigator.service.NationalEngine;
import com.citynavigator.service.NetworkImportService;
import com.citynavigator.service.RoutingEngine;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Prepares everything before the first request is served: schema, road network, routing graph, the optional national
 * car graph and, with {@code DEMO_DATA=true}, a few sample incidents. If a required step fails, the deployment fails
 * and the health check stays red, rather than serving an application that cannot route; without the national graph
 * the application still works, with the nationwide routing service outside its detailed area.
 */
@ApplicationScoped
public class ApplicationStartup {

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
        migrator.migrate();
        networkImport.importBundledAreaIfEmpty();
        routingEngine.load();
        nationalEngine.load();
        if (Boolean.parseBoolean(System.getenv("DEMO_DATA"))) {
            demoIncidents.addIfNone();
        }
    }
}

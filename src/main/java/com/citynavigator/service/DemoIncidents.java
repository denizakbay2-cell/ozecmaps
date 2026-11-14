package com.citynavigator.service;

import com.citynavigator.dto.IncidentRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.logging.Logger;

/**
 * A few sample incidents in Kadıköy for a demo ({@code DEMO_DATA=true}), added only to a database that never had
 * any. They go through {@link IncidentService} like a real report, so the same rules and the same transaction apply.
 */
@ApplicationScoped
public class DemoIncidents {

    private static final Logger LOGGER = Logger.getLogger(DemoIncidents.class.getName());

    private IncidentService incidents;

    protected DemoIncidents() {
        // for the CDI proxy
    }

    @Inject
    public DemoIncidents(IncidentService incidents) {
        this.incidents = incidents;
    }

    public void addIfNone() {
        if (incidents.anyRecorded()) {
            return;
        }
        incidents.report(new IncidentRequest("ROADWORK", "Altyapı çalışması, tek şerit açık",
                40.982681, 29.023423, null));
        incidents.report(new IncidentRequest("CLOSURE", "Sokak etkinliği nedeniyle trafiğe kapalı",
                40.984980, 29.020918, null));
        incidents.report(new IncidentRequest("CONGESTION", "Akşam saatlerinde yoğunluk",
                40.983952, 29.059877, null));
        LOGGER.info("added 3 sample incidents");
    }
}

package com.citynavigator;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * Serves the REST API under {@code /api}; everything else under {@code /} is the static web client.
 */
@ApplicationPath("api")
public class RestApplication extends Application {
}

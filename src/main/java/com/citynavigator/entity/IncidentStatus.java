package com.citynavigator.entity;

/**
 * Life cycle of an incident: {@code ACTIVE → RESOLVED}, never back. An active incident with an end time stops
 * affecting routes once that time has passed.
 */
public enum IncidentStatus {
    ACTIVE,
    RESOLVED
}

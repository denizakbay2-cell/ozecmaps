package com.citynavigator.entity;

/**
 * Kinds of road incidents and what each does to traffic.
 *
 * @see Incident
 */
public enum IncidentType {

    ACCIDENT("Kaza", false, 0.35, 150),
    ROADWORK("Yol çalışması", false, 0.5, 200),
    CONGESTION("Yoğun trafik", false, 0.3, 400),
    CLOSURE("Kapalı yol", true, 0, 600);

    private final String label;
    private final boolean closesRoad;
    private final double speedFactor;
    private final double reachMeters;

    IncidentType(String label, boolean closesRoad, double speedFactor, double reachMeters) {
        this.label = label;
        this.closesRoad = closesRoad;
        this.speedFactor = speedFactor;
        this.reachMeters = reachMeters;
    }

    public String label() {
        return label;
    }

    /**
     * A closure shuts the block between the junctions around the reported point; the other kinds slow cars down.
     */
    public boolean closesRoad() {
        return closesRoad;
    }

    /**
     * Share of the normal speed cars keep on the affected road; 0 for a closure.
     */
    public double speedFactor() {
        return speedFactor;
    }

    /**
     * How far along the road the incident reaches from the reported point (for a closure: at most, each way).
     */
    public double reachMeters() {
        return reachMeters;
    }
}

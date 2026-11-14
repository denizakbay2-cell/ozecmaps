package com.citynavigator.routing;

import java.util.List;

/**
 * A way through public transport from a point to a point: walks and rides, one after the other.
 *
 * @param departure when to set off, in seconds after the midnight of the day it was planned for
 * @param arrival   when it arrives at the destination, likewise
 */
public record TransitJourney(int departure, int arrival, List<Leg> legs) {

    public TransitJourney {
        legs = List.copyOf(legs);
    }

    /** How many vehicles it takes; walking all the way takes none. */
    public int rides() {
        return (int) legs.stream().filter(Ride.class::isInstance).count();
    }

    public int durationSeconds() {
        return arrival - departure;
    }

    /** The same journey a number of seconds later (or earlier). */
    TransitJourney shifted(int seconds) {
        return new TransitJourney(departure + seconds, arrival + seconds, legs.stream().map(leg -> switch (leg) {
            case Walk walk -> (Leg) new Walk(walk.fromStop(), walk.toStop(), walk.departure() + seconds,
                    walk.arrival() + seconds, walk.meters());
            case Ride ride -> new Ride(ride.pattern(), ride.trip(), ride.boardIndex(), ride.alightIndex(),
                    ride.departure() + seconds, ride.arrival() + seconds);
        }).toList());
    }

    /**
     * A part of the journey.
     */
    public sealed interface Leg permits Walk, Ride {

        int departure();

        int arrival();
    }

    /**
     * A walk.
     *
     * @param fromStop the stop it starts at, or -1 for the start of the journey
     * @param toStop   the stop it ends at, or -1 for the destination
     * @param meters   the straight-line distance walked
     */
    public record Walk(int fromStop, int toStop, int departure, int arrival, double meters) implements Leg {
    }

    /**
     * A ride on one trip of a pattern, from the stop at {@code boardIndex} in the pattern to the stop at
     * {@code alightIndex}.
     */
    public record Ride(int pattern, int trip, int boardIndex, int alightIndex, int departure, int arrival)
            implements Leg {
    }
}

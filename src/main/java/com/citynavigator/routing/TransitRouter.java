package com.citynavigator.routing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Plans journeys through a {@link TransitNetwork} with RAPTOR (Delling, Pajor and Werneck, "Round-Based Public Transit
 * Routing", 2012). Round k finds the earliest arrival at every stop with at most k vehicles, so one search gives the
 * earliest arrival for each number of rides, and a faster journey can be weighed against one with fewer changes.
 *
 * <p>Every search has its own working arrays: one router serves any number of searches at once.
 */
public final class TransitRouter {

    /** At most this many vehicles in one journey. */
    public static final int MAX_RIDES = 5;
    /** Walking to the first stop or from the last one: at most this far in a straight line. */
    public static final double ACCESS_METERS = 1_000;
    /** Walking all the way is considered up to this far in a straight line. */
    public static final double MAX_WALK_METERS = 3_000;
    /** Being at the stop this long before the vehicle leaves: to find the platform, to pass the gates. */
    public static final int BOARD_SECONDS = 60;
    /** Until this time of the day, trips of the day before that run past midnight count too. */
    static final int NIGHT_SECONDS = 4 * 3600;
    private static final int DAY_SECONDS = 24 * 3600;

    private static final int NONE = Integer.MAX_VALUE;
    private static final byte COPIED = 0;
    private static final byte ACCESS = 1;
    private static final byte RIDE = 2;
    private static final byte WALK = 3;

    private final TransitNetwork network;

    public TransitRouter(TransitNetwork network) {
        this.network = network;
    }

    public TransitNetwork network() {
        return network;
    }

    /**
     * The journeys worth offering for leaving at {@code seconds} after midnight on {@code day} (0 Monday to 6 Sunday):
     * walking all the way when it is not too far, and for each number of rides the earliest arrival, when it is
     * earlier than with fewer rides. Ordered by the number of rides; empty when no journey is found.
     */
    public List<TransitJourney> plan(GeoPoint from, GeoPoint to, int day, int seconds) {
        return plan(from, to, day, seconds, EnumSet.allOf(TransitMode.class));
    }

    /** The same, riding only the given kinds of vehicle (for example: none that gets stuck in traffic). */
    public List<TransitJourney> plan(GeoPoint from, GeoPoint to, int day, int seconds, Set<TransitMode> modes) {
        boolean[] usable = new boolean[network.patternCount()];
        for (int pattern = 0; pattern < usable.length; pattern++) {
            usable[pattern] = modes.contains(network.lineMode(network.patternLine(pattern)));
        }
        List<TransitJourney> journeys = new ArrayList<>(new Search(from, to, day, seconds, usable).run());
        if (seconds < NIGHT_SECONDS) {
            new Search(from, to, (day + 6) % 7, seconds + DAY_SECONDS, usable).run()
                    .forEach(journey -> journeys.add(journey.shifted(-DAY_SECONDS)));
        }
        journeys.sort(Comparator.comparingInt(TransitJourney::rides).thenComparingInt(TransitJourney::arrival));
        List<TransitJourney> front = new ArrayList<>();
        for (TransitJourney journey : journeys) {
            if (front.isEmpty() || journey.arrival() < front.getLast().arrival()) {
                front.add(journey);
            }
        }
        return front;
    }

    /** One search: the working arrays of every round, and the best arrival at the destination so far. */
    private final class Search {

        private final GeoPoint from;
        private final GeoPoint to;
        private final int day;
        private final int departure;
        private final boolean[] usable;
        private final int stops = network.stopCount();
        private final int[][] arrival = new int[MAX_RIDES + 1][];
        private final byte[][] how = new byte[MAX_RIDES + 1][];
        /** The pattern ridden, or for a walk the stop walked from. */
        private final int[][] via = new int[MAX_RIDES + 1][];
        private final int[][] trip = new int[MAX_RIDES + 1][];
        private final int[][] boarded = new int[MAX_RIDES + 1][];
        private final int[][] alighted = new int[MAX_RIDES + 1][];
        private final int[] best = new int[stops];
        private final int[] egress = new int[stops];
        private int target = NONE;

        Search(GeoPoint from, GeoPoint to, int day, int departure, boolean[] usable) {
            this.from = from;
            this.to = to;
            this.day = day;
            this.departure = departure;
            this.usable = usable;
        }

        List<TransitJourney> run() {
            List<TransitJourney> journeys = new ArrayList<>();
            double direct = GeoMath.distance(from, to);
            if (direct <= MAX_WALK_METERS) {
                target = departure + TransitNetwork.walkSeconds(direct);
                journeys.add(new TransitJourney(departure, target,
                        List.of(new TransitJourney.Walk(-1, -1, departure, target, direct))));
            }
            Arrays.fill(best, NONE);
            Arrays.fill(egress, NONE);
            for (int stop : network.stopsWithin(to, ACCESS_METERS)) {
                egress[stop] = TransitNetwork.walkSeconds(GeoMath.distance(network.stop(stop), to));
            }
            startRound(0);
            BitSet marked = new BitSet(stops);
            for (int stop : network.stopsWithin(from, ACCESS_METERS)) {
                int at = departure + TransitNetwork.walkSeconds(GeoMath.distance(from, network.stop(stop)));
                if (at < target) {
                    arrival[0][stop] = at;
                    best[stop] = at;
                    how[0][stop] = ACCESS;
                    marked.set(stop);
                }
            }
            int[] firstIndex = new int[network.patternCount()];
            Arrays.fill(firstIndex, -1);
            for (int round = 1; round <= MAX_RIDES && !marked.isEmpty(); round++) {
                startRound(round);
                for (int pattern : patternsThrough(marked, firstIndex)) {
                    int start = firstIndex[pattern];
                    firstIndex[pattern] = -1;
                    ride(round, pattern, start, marked);
                }
                transfer(round, marked);
                int lastStop = -1;
                for (int stop = marked.nextSetBit(0); stop >= 0; stop = marked.nextSetBit(stop + 1)) {
                    if (egress[stop] != NONE && arrival[round][stop] + egress[stop] < target) {
                        target = arrival[round][stop] + egress[stop];
                        lastStop = stop;
                    }
                }
                if (lastStop >= 0) {
                    journeys.add(journey(round, lastStop));
                }
            }
            return journeys;
        }

        private void startRound(int round) {
            if (round == 0) {
                arrival[0] = new int[stops];
                Arrays.fill(arrival[0], NONE);
            } else {
                arrival[round] = arrival[round - 1].clone();
            }
            how[round] = new byte[stops];
            via[round] = new int[stops];
            trip[round] = new int[stops];
            boarded[round] = new int[stops];
            alighted[round] = new int[stops];
        }

        /** The patterns calling at the marked stops, each with the first marked stop's place in it; unmarks them. */
        private List<Integer> patternsThrough(BitSet marked, int[] firstIndex) {
            List<Integer> patterns = new ArrayList<>();
            for (int stop = marked.nextSetBit(0); stop >= 0; stop = marked.nextSetBit(stop + 1)) {
                for (int call = network.callStart[stop]; call < network.callStart[stop + 1]; call++) {
                    int pattern = network.callPattern[call];
                    int index = network.callIndex[call];
                    if (!usable[pattern]) {
                        continue;
                    }
                    if (firstIndex[pattern] < 0) {
                        firstIndex[pattern] = index;
                        patterns.add(pattern);
                    } else if (index < firstIndex[pattern]) {
                        firstIndex[pattern] = index;
                    }
                }
            }
            marked.clear();
            return patterns;
        }

        /** Rides the pattern from the stop at {@code start}, catching the earliest trip possible at each stop. */
        private void ride(int round, int pattern, int start, BitSet marked) {
            int[] calls = network.patternStops[pattern];
            int current = -1;
            int boardIndex = -1;
            for (int index = start; index < calls.length; index++) {
                int stop = calls[index];
                if (current >= 0) {
                    int at = network.time(pattern, current, index);
                    if (at < best[stop] && at < target) {
                        arrival[round][stop] = at;
                        best[stop] = at;
                        how[round][stop] = RIDE;
                        via[round][stop] = pattern;
                        trip[round][stop] = current;
                        boarded[round][stop] = boardIndex;
                        alighted[round][stop] = index;
                        marked.set(stop);
                    }
                }
                int ready = arrival[round - 1][stop];
                if (ready != NONE
                        && (current < 0 || ready + BOARD_SECONDS <= network.time(pattern, current, index))) {
                    int earlier = earliestTrip(pattern, index, ready + BOARD_SECONDS, current);
                    if (earlier != current) {
                        current = earlier;
                        boardIndex = index;
                    }
                }
            }
        }

        /** The first trip running today that leaves the stop at {@code index} no earlier than the time. */
        private int earliestTrip(int pattern, int index, int notBefore, int current) {
            int limit = current >= 0 ? current : network.tripCount(pattern);
            int low = 0;
            int high = limit;
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (network.time(pattern, middle, index) < notBefore) {
                    low = middle + 1;
                } else {
                    high = middle;
                }
            }
            for (int candidate = low; candidate < limit; candidate++) {
                if (network.runs(pattern, candidate, day)) {
                    return candidate;
                }
            }
            return current;
        }

        /**
         * Walks from the stops a vehicle reached in this round to the stops nearby. A stop a vehicle reached keeps that
         * arrival, so that a walk never starts from the end of another walk.
         */
        private void transfer(int round, BitSet marked) {
            for (int stop : marked.stream().toArray()) {
                int reached = arrival[round][stop];
                for (int walk = network.walkStart[stop]; walk < network.walkStart[stop + 1]; walk++) {
                    int other = network.walkTo[walk];
                    int at = reached + network.walkSeconds[walk];
                    if (at < best[other] && at < target && how[round][other] != RIDE) {
                        arrival[round][other] = at;
                        best[other] = at;
                        how[round][other] = WALK;
                        via[round][other] = stop;
                        marked.set(other);
                    }
                }
            }
        }

        /** Follows the labels back from the last stop to the start. */
        private TransitJourney journey(int round, int lastStop) {
            List<TransitJourney.Leg> legs = new ArrayList<>();
            int reached = arrival[round][lastStop];
            legs.add(new TransitJourney.Walk(lastStop, -1, reached, reached + egress[lastStop],
                    GeoMath.distance(network.stop(lastStop), to)));
            int stop = lastStop;
            int k = round;
            while (true) {
                while (how[k][stop] == COPIED) {
                    k--;
                }
                if (how[k][stop] == ACCESS) {
                    break;
                }
                if (how[k][stop] == WALK) {
                    int origin = via[k][stop];
                    legs.add(new TransitJourney.Walk(origin, stop, arrival[k][origin], arrival[k][stop],
                            GeoMath.distance(network.stop(origin), network.stop(stop))));
                    stop = origin;
                    continue;
                }
                int pattern = via[k][stop];
                int ridden = trip[k][stop];
                int board = boarded[k][stop];
                int alight = alighted[k][stop];
                legs.add(new TransitJourney.Ride(pattern, ridden, board, alight, network.time(pattern, ridden, board),
                        network.time(pattern, ridden, alight)));
                stop = network.patternStops[pattern][board];
                k--;
            }
            Collections.reverse(legs);
            // leave just in time for the first vehicle
            double meters = GeoMath.distance(from, network.stop(stop));
            int walk = TransitNetwork.walkSeconds(meters);
            int leave = Math.max(departure, legs.getFirst().departure() - BOARD_SECONDS - walk);
            legs.addFirst(new TransitJourney.Walk(-1, stop, leave, leave + walk, meters));
            return new TransitJourney(leave, legs.getLast().arrival(), joinWalks(legs));
        }

        /** A walk to a nearby stop and on to the destination is one walk. */
        private static List<TransitJourney.Leg> joinWalks(List<TransitJourney.Leg> legs) {
            List<TransitJourney.Leg> joined = new ArrayList<>();
            for (TransitJourney.Leg leg : legs) {
                if (!joined.isEmpty() && joined.getLast() instanceof TransitJourney.Walk first
                        && leg instanceof TransitJourney.Walk second) {
                    joined.set(joined.size() - 1, new TransitJourney.Walk(first.fromStop(), second.toStop(),
                            first.departure(), second.arrival(), first.meters() + second.meters()));
                } else {
                    joined.add(leg);
                }
            }
            return joined;
        }
    }
}

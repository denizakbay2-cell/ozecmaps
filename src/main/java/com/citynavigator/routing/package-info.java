/**
 * The routing engine: the road graph, snapping a coordinate to the nearest road, A* search and turn-by-turn
 * instructions. Plain Java on purpose (no CDI, JPA or JAX-RS), so it can be tested in isolation and reasoned about
 * on its own; the {@code service} package feeds it data and turns its results into responses.
 */
package com.citynavigator.routing;

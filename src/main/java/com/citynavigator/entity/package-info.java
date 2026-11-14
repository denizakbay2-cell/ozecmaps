/**
 * JPA entities. They guard their own rules (rich model) and raise {@code BusinessRuleException} when a change
 * would break one. The road network itself is not mapped here: it is loaded in bulk by
 * {@code RoadNetworkRepository} straight into the routing graph.
 */
package com.citynavigator.entity;

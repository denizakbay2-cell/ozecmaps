/**
 * Use cases and transaction boundaries. Services check requests against the map, call the routing engine and
 * the entities, and turn results into DTOs; {@code @Transactional} lives only here.
 */
package com.citynavigator.service;

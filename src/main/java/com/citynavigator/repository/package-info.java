/**
 * Data access. Entities go through JPA; the road network, tens of thousands of rows written once and read once
 * per start, goes through plain JDBC batches in the same JTA transaction.
 */
package com.citynavigator.repository;

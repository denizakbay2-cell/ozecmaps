package com.citynavigator.repository;

import com.citynavigator.DataSourceConfig;
import com.citynavigator.osm.RoadExtract;
import com.citynavigator.routing.RoadClass;
import com.citynavigator.routing.RoadGraph;
import com.citynavigator.routing.RoadWay;
import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

/**
 * Stores the road network and reads it back into a {@link RoadGraph}. Plain JDBC: the rows are written once in
 * batches and read once per start; managing them as JPA entities would only cost memory and time.
 * Connections taken inside a transaction take part in it, so an import is all or nothing.
 */
@ApplicationScoped
public class RoadNetworkRepository {

    private static final int BATCH_SIZE = 2_000;
    private static final int FETCH_SIZE = 5_000;

    @Resource(lookup = DataSourceConfig.JNDI_NAME)
    private DataSource dataSource;

    public boolean isEmpty() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT EXISTS (SELECT 1 FROM road_way)");
             ResultSet result = statement.executeQuery()) {
            result.next();
            return !result.getBoolean(1);
        } catch (SQLException e) {
            throw new IllegalStateException("could not check the road network", e);
        }
    }

    public void insert(RoadExtract extract) {
        try (Connection connection = dataSource.getConnection()) {
            insertNodes(connection, extract.nodes());
            insertWays(connection, extract.ways());
            insertWayNodes(connection, extract.ways());
        } catch (SQLException e) {
            throw new IllegalStateException("could not store the road network", e);
        }
    }

    public RoadGraph loadGraph() {
        RoadGraph.Builder graph = RoadGraph.builder();
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT id, lat, lon FROM road_node")) {
                statement.setFetchSize(FETCH_SIZE);
                try (ResultSet row = statement.executeQuery()) {
                    while (row.next()) {
                        graph.addNode(row.getLong(1), row.getDouble(2), row.getDouble(3));
                    }
                }
            }
            Map<Long, RoadWay> ways = loadWays(connection);
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT way_id, node_id FROM road_way_node ORDER BY way_id, seq")) {
                statement.setFetchSize(FETCH_SIZE);
                try (ResultSet row = statement.executeQuery()) {
                    addWays(graph, ways, row);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("could not load the road network", e);
        }
        return graph.build();
    }

    private static void insertNodes(Connection connection, List<RoadExtract.Node> nodes) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO road_node (id, lat, lon) VALUES (?, ?, ?)")) {
            int pending = 0;
            for (RoadExtract.Node node : nodes) {
                statement.setLong(1, node.id());
                statement.setDouble(2, node.lat());
                statement.setDouble(3, node.lon());
                statement.addBatch();
                pending = flushIfFull(statement, pending + 1);
            }
            statement.executeBatch();
        }
    }

    private static void insertWays(Connection connection, List<RoadExtract.Way> ways) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO road_way"
                + " (id, name, road_class, max_speed_kmh, roundabout, access) VALUES (?, ?, ?, ?, ?, ?)")) {
            int pending = 0;
            for (RoadExtract.Way extracted : ways) {
                RoadWay way = extracted.way();
                statement.setLong(1, way.id());
                statement.setString(2, way.name());
                statement.setString(3, way.roadClass().name());
                if (way.maxSpeedKmh() > 0) {
                    statement.setInt(4, way.maxSpeedKmh());
                } else {
                    statement.setNull(4, Types.SMALLINT);
                }
                statement.setBoolean(5, way.roundabout());
                statement.setInt(6, way.access());
                statement.addBatch();
                pending = flushIfFull(statement, pending + 1);
            }
            statement.executeBatch();
        }
    }

    private static void insertWayNodes(Connection connection, List<RoadExtract.Way> ways) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO road_way_node (way_id, seq, node_id) VALUES (?, ?, ?)")) {
            int pending = 0;
            for (RoadExtract.Way way : ways) {
                for (int seq = 0; seq < way.nodes().size(); seq++) {
                    statement.setLong(1, way.way().id());
                    statement.setInt(2, seq);
                    statement.setLong(3, way.nodes().get(seq));
                    statement.addBatch();
                    pending = flushIfFull(statement, pending + 1);
                }
            }
            statement.executeBatch();
        }
    }

    private static int flushIfFull(PreparedStatement statement, int pending) throws SQLException {
        if (pending < BATCH_SIZE) {
            return pending;
        }
        statement.executeBatch();
        return 0;
    }

    private static Map<Long, RoadWay> loadWays(Connection connection) throws SQLException {
        Map<Long, RoadWay> ways = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, name, road_class, max_speed_kmh, roundabout, access FROM road_way")) {
            statement.setFetchSize(FETCH_SIZE);
            try (ResultSet row = statement.executeQuery()) {
                while (row.next()) {
                    long id = row.getLong(1);
                    ways.put(id, new RoadWay(id, row.getString(2), RoadClass.valueOf(row.getString(3)),
                            row.getInt(4), row.getBoolean(5), row.getInt(6)));
                }
            }
        }
        return ways;
    }

    /** Rows arrive ordered by way and position; each run of one way id becomes one way. */
    private static void addWays(RoadGraph.Builder graph, Map<Long, RoadWay> ways, ResultSet row) throws SQLException {
        long currentWay = Long.MIN_VALUE;
        List<Long> nodes = new ArrayList<>();
        while (row.next()) {
            long wayId = row.getLong(1);
            if (wayId != currentWay && !nodes.isEmpty()) {
                graph.addWay(ways.get(currentWay), toArray(nodes));
                nodes.clear();
            }
            currentWay = wayId;
            nodes.add(row.getLong(2));
        }
        if (!nodes.isEmpty()) {
            graph.addWay(ways.get(currentWay), toArray(nodes));
        }
    }

    private static long[] toArray(List<Long> values) {
        return values.stream().mapToLong(Long::longValue).toArray();
    }
}

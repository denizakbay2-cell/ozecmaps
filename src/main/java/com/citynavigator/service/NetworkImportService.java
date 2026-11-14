package com.citynavigator.service;

import com.citynavigator.entity.MapArea;
import com.citynavigator.entity.Place;
import com.citynavigator.osm.RoadExtract;
import com.citynavigator.repository.MapAreaRepository;
import com.citynavigator.repository.PlaceRepository;
import com.citynavigator.repository.RoadNetworkRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

/**
 * Loads the OpenStreetMap extract bundled in {@code src/main/resources/osm} into an empty database: roads,
 * searchable places and the area description, in one transaction. A failure halfway leaves the database empty,
 * and the next start simply tries again.
 */
@ApplicationScoped
@Transactional
public class NetworkImportService {

    static final String DATA_FOLDER = "osm/";
    private static final Logger LOGGER = Logger.getLogger(NetworkImportService.class.getName());

    private RoadNetworkRepository network;
    private PlaceRepository places;
    private MapAreaRepository areas;
    private Clock clock;

    protected NetworkImportService() {
        // for the CDI proxy
    }

    @Inject
    public NetworkImportService(RoadNetworkRepository network, PlaceRepository places, MapAreaRepository areas,
                                Clock clock) {
        this.network = network;
        this.places = places;
        this.areas = areas;
        this.clock = clock;
    }

    /**
     * @return whether anything was imported (false when the network is already there)
     */
    public boolean importBundledAreaIfEmpty() {
        if (!network.isEmpty()) {
            return false;
        }
        long started = System.nanoTime();
        Instant now = clock.instant();
        AreaDefinition area = AreaDefinition.read(open("area.properties"));
        RoadExtract roads;
        try (InputStream json = new GZIPInputStream(open("roads.json.gz"))) {
            roads = RoadExtract.read(json);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read the road extract", e);
        }
        if (roads.ways().isEmpty()) {
            throw new IllegalStateException("the road extract contains no routable way");
        }
        network.insert(roads);

        // businesses first: where the landmark extract repeats one (a pharmacy), the business version is kept
        List<Place> found = new ArrayList<>();
        InputStream businesses = NetworkImportService.class.getClassLoader()
                .getResourceAsStream(DATA_FOLDER + "businesses.json.gz");
        if (businesses != null) {
            try (InputStream json = new GZIPInputStream(businesses)) {
                found.addAll(OsmPlaces.readBusinesses(json, now));
            } catch (IOException e) {
                throw new UncheckedIOException("could not read the business extract", e);
            }
        }
        try (InputStream json = new GZIPInputStream(open("places.json.gz"))) {
            found.addAll(OsmPlaces.read(json, now));
        } catch (IOException e) {
            throw new UncheckedIOException("could not read the places extract", e);
        }
        found.addAll(OsmPlaces.streets(roads, now));
        List<Place> unique = OsmPlaces.withoutDuplicates(found);
        places.addAll(unique);

        areas.save(new MapArea(area.name(), boundsOf(roads), new double[] {area.centerLat(), area.centerLon()},
                roads.timestamp().orElse(null), now));
        long seconds = (System.nanoTime() - started) / 1_000_000_000;
        long businessCount = unique.stream().filter(Place::isBusiness).count();
        LOGGER.info(() -> "imported " + area.name() + ": " + roads.nodes().size() + " nodes, " + roads.ways().size()
                + " ways (" + roads.skippedWays() + " skipped), " + unique.size() + " places of which " + businessCount
                + " businesses in " + seconds + " s");
        return true;
    }

    static double[] boundsOf(RoadExtract roads) {
        double[] bounds = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (RoadExtract.Node node : roads.nodes()) {
            bounds[0] = Math.min(bounds[0], node.lat());
            bounds[1] = Math.min(bounds[1], node.lon());
            bounds[2] = Math.max(bounds[2], node.lat());
            bounds[3] = Math.max(bounds[3], node.lon());
        }
        return bounds;
    }

    private static InputStream open(String name) {
        InputStream input = NetworkImportService.class.getClassLoader().getResourceAsStream(DATA_FOLDER + name);
        if (input == null) {
            throw new IllegalStateException("missing data file " + DATA_FOLDER + name);
        }
        return input;
    }
}

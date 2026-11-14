-- Road network imported from OpenStreetMap (ODbL). Ids are the OpenStreetMap ids, so data can be traced back.
CREATE TABLE road_node (
    id  BIGINT PRIMARY KEY,
    lat DOUBLE PRECISION NOT NULL CHECK (lat BETWEEN -90 AND 90),
    lon DOUBLE PRECISION NOT NULL CHECK (lon BETWEEN -180 AND 180)
);

-- One routable way with its tags already interpreted; access holds the TravelMode bits
-- (1 car forward, 2 car backward, 4 bicycle forward, 8 bicycle backward, 16 foot).
CREATE TABLE road_way (
    id            BIGINT PRIMARY KEY,
    name          VARCHAR(200),
    road_class    VARCHAR(20) NOT NULL,
    max_speed_kmh SMALLINT CHECK (max_speed_kmh BETWEEN 5 AND 150),
    roundabout    BOOLEAN     NOT NULL,
    access        SMALLINT    NOT NULL CHECK (access BETWEEN 1 AND 31)
);

-- The ordered nodes of a way. Segment n of a way runs from node seq n to node seq n + 1.
CREATE TABLE road_way_node (
    way_id  BIGINT  NOT NULL REFERENCES road_way (id),
    seq     INTEGER NOT NULL CHECK (seq >= 0),
    node_id BIGINT  NOT NULL REFERENCES road_node (id),
    PRIMARY KEY (way_id, seq)
);
CREATE INDEX ix_road_way_node_node ON road_way_node (node_id);

-- The imported area; a single row.
CREATE TABLE map_area (
    id             SMALLINT PRIMARY KEY CHECK (id = 1),
    name           VARCHAR(120)     NOT NULL,
    min_lat        DOUBLE PRECISION NOT NULL,
    min_lon        DOUBLE PRECISION NOT NULL,
    max_lat        DOUBLE PRECISION NOT NULL,
    max_lon        DOUBLE PRECISION NOT NULL,
    center_lat     DOUBLE PRECISION NOT NULL,
    center_lon     DOUBLE PRECISION NOT NULL,
    data_timestamp TIMESTAMP WITH TIME ZONE,
    imported_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_map_area_bounds CHECK (min_lat <= max_lat AND min_lon <= max_lon)
);

-- Searchable places: neighbourhoods, piers, stations, landmarks, streets. search_key is the folded name
-- (SearchKey.of): lower case, Turkish letters without marks.
CREATE TABLE place (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(200)     NOT NULL,
    search_key VARCHAR(200)     NOT NULL CHECK (search_key <> ''),
    category   VARCHAR(20)      NOT NULL,
    lat        DOUBLE PRECISION NOT NULL CHECK (lat BETWEEN -90 AND 90),
    lon        DOUBLE PRECISION NOT NULL CHECK (lon BETWEEN -180 AND 180),
    osm_ref    VARCHAR(30) UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_place_search_key ON place (search_key text_pattern_ops);

-- Road incidents and the segments they cover.
CREATE TABLE incident (
    id          BIGSERIAL PRIMARY KEY,
    type        VARCHAR(20)      NOT NULL CHECK (type IN ('ACCIDENT', 'ROADWORK', 'CONGESTION', 'CLOSURE')),
    status      VARCHAR(20)      NOT NULL CHECK (status IN ('ACTIVE', 'RESOLVED')),
    description VARCHAR(300),
    lat         DOUBLE PRECISION NOT NULL CHECK (lat BETWEEN -90 AND 90),
    lon         DOUBLE PRECISION NOT NULL CHECK (lon BETWEEN -180 AND 180),
    street      VARCHAR(200),
    reported_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at     TIMESTAMP WITH TIME ZONE,
    resolved_at TIMESTAMP WITH TIME ZONE,
    version     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_incident_ends_after_report CHECK (ends_at IS NULL OR ends_at > reported_at),
    CONSTRAINT ck_incident_resolved_iff_status CHECK ((status = 'RESOLVED') = (resolved_at IS NOT NULL))
);
CREATE INDEX ix_incident_active ON incident (reported_at) WHERE status = 'ACTIVE';

CREATE TABLE incident_segment (
    incident_id BIGINT  NOT NULL REFERENCES incident (id) ON DELETE CASCADE,
    way_id      BIGINT  NOT NULL,
    seq         INTEGER NOT NULL,
    PRIMARY KEY (incident_id, way_id, seq),
    CONSTRAINT fk_incident_segment_way_node FOREIGN KEY (way_id, seq) REFERENCES road_way_node (way_id, seq)
);

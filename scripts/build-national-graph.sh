#!/bin/sh
# Builds data/turkey-car.graph, the car road network of Turkey for the own national routing engine (ADR-0008):
#   1. downloads the OpenStreetMap extract of Turkey from Geofabrik (about 650 MB, refreshed daily),
#   2. keeps the roads cars use with osmium, in a throwaway Debian container,
#   3. cuts them into junction-to-junction edges with the BuildNationalGraph tool (about 3 GB of heap, under a minute).
# Needs Docker and about 3 GB of free disk space while it runs; the result is about 290 MB.
#
#   scripts/build-national-graph.sh            # REFRESH=1 downloads the extract again even if it is there
#
# docker-compose.yml mounts data/ and points NATIONAL_GRAPH_FILE at the result; the application then needs about
# 1 GB of heap. The data is © OpenStreetMap contributors, available under the ODbL.
set -eu

root=$(cd "$(dirname "$0")/.." && pwd)
data="$root/data"
agent="city-navigator/0.1 (Jakarta EE course project)"
roads="motorway,motorway_link,trunk,trunk_link,primary,primary_link,secondary,secondary_link,tertiary,tertiary_link"
roads="$roads,unclassified,residential,living_street,service,road"
mkdir -p "$data"

if [ ! -s "$data/turkey-latest.osm.pbf" ] || [ "${REFRESH:-0}" = 1 ]; then
    echo "1/3 downloading the OpenStreetMap extract of Turkey ..."
    curl -sSL --fail -A "$agent" --retry 5 --retry-all-errors -o "$data/turkey-latest.osm.pbf.part" \
        https://download.geofabrik.de/europe/turkey-latest.osm.pbf
    mv "$data/turkey-latest.osm.pbf.part" "$data/turkey-latest.osm.pbf"
else
    echo "1/3 using the extract already in data/ (REFRESH=1 downloads a new one)"
fi

echo "2/3 keeping the car roads (osmium) ..."
docker run --rm -v "$data":/data debian:bookworm-slim sh -c "
    set -e
    apt-get update -qq && apt-get install -y -qq osmium-tool >/dev/null 2>&1
    osmium fileinfo -g header.option.osmosis_replication_timestamp /data/turkey-latest.osm.pbf > /data/turkey-latest.timestamp
    osmium tags-filter /data/turkey-latest.osm.pbf w/highway=$roads -o /data/turkey-car.osm.pbf --overwrite
    osmium cat /data/turkey-car.osm.pbf -f opl,add_metadata=false -o /data/turkey-car.opl --overwrite"

echo "3/3 building the graph ..."
docker run --rm -v "$root":/workspace -v m2-cache:/root/.m2 -w /workspace maven:3.9-eclipse-temurin-21 sh -c "
    set -e
    mvn -B -q -DskipTests compile
    java -Xmx3g -cp target/classes com.citynavigator.tools.BuildNationalGraph \
        data/turkey-car.opl data/turkey-car.graph \"\$(cat data/turkey-latest.timestamp)\""
rm -f "$data/turkey-car.opl" "$data/turkey-car.osm.pbf"
ls -la "$data/turkey-car.graph"

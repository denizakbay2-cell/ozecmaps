#!/bin/sh
# Builds src/main/resources/pois/turkey-pois.csv.gz, the places the application searches along a route (fuel and
# charging stations, pharmacies, banks and ATMs, markets), from the OpenStreetMap extract of Turkey that
# scripts/build-national-graph.sh downloads into data/:
#   1. keeps those places with osmium, in a throwaway Debian container,
#   2. writes one line per place with the BuildPoiIndex tool.
# The result is about 1.5 MB and ships with the application. The data is (c) OpenStreetMap contributors, ODbL.
set -eu

root=$(cd "$(dirname "$0")/.." && pwd)
data="$root/data"
if [ ! -s "$data/turkey-latest.osm.pbf" ]; then
    echo "data/turkey-latest.osm.pbf is missing: run scripts/build-national-graph.sh first" >&2
    exit 1
fi

echo "1/2 keeping the places (osmium) ..."
docker run --rm -v "$data":/data debian:bookworm-slim sh -c "
    set -e
    apt-get update -qq && apt-get install -y -qq osmium-tool >/dev/null 2>&1
    osmium tags-filter /data/turkey-latest.osm.pbf nwr/amenity=fuel,pharmacy,atm,bank,charging_station \
        nwr/shop=supermarket,convenience -o /data/turkey-pois.osm.pbf --overwrite
    osmium cat /data/turkey-pois.osm.pbf -f opl,add_metadata=false -o /data/turkey-pois.opl --overwrite"

echo "2/2 writing the list ..."
docker run --rm -v "$root":/workspace -v m2-cache:/root/.m2 -w /workspace maven:3.9-eclipse-temurin-21 sh -c "
    set -e
    mvn -B -q -DskipTests compile
    java -cp target/classes com.citynavigator.tools.BuildPoiIndex data/turkey-pois.opl \
        src/main/resources/pois/turkey-pois.csv.gz \"\$(cat data/turkey-latest.timestamp 2>/dev/null || echo unknown)\""
rm -f "$data/turkey-pois.opl" "$data/turkey-pois.osm.pbf" "$data/turkey-pois.geojsonseq"
ls -la "$root/src/main/resources/pois/turkey-pois.csv.gz"

#!/bin/sh
# Downloads the roads, landmarks and businesses of another area from the Overpass API and stores them where the
# application imports them from (src/main/resources/osm). The import runs on the next start with an empty
# database (docker compose down -v), so also edit src/main/resources/osm/area.properties (name, center).
#
#   scripts/fetch-osm.sh SOUTH WEST NORTH EAST
#   scripts/fetch-osm.sh 40.955 29.015 41.010 29.105     # Kadıköy, the bundled area
#
# Keep the area to a district or a small town (about 50 km²): the graph lives in memory and the free Render
# plan has 512 MB. The data is © OpenStreetMap contributors, available under the ODbL.
set -eu

if [ $# -ne 4 ]; then
    echo "usage: $0 SOUTH WEST NORTH EAST" >&2
    exit 1
fi
bbox="$1,$2,$3,$4"
here=$(cd "$(dirname "$0")" && pwd)
target="$here/../src/main/resources/osm"
endpoint="${OVERPASS_URL:-https://overpass-api.de/api/interpreter}"
agent="city-navigator/0.1 (course project; one-off extract)"

fetch() {
    query=$(sed "s/\[bbox:[^]]*\]/[bbox:$bbox]/" "$here/overpass/$1.overpassql")
    echo "downloading $1 for $bbox ..."
    curl -sS --fail -A "$agent" --max-time 300 --data-urlencode "data=$query" "$endpoint" | gzip -9 > "$target/$1.json.gz"
    echo "  $(wc -c < "$target/$1.json.gz") bytes -> src/main/resources/osm/$1.json.gz"
}

fetch roads
sleep 5   # be gentle with the public Overpass server
fetch places
sleep 5
fetch businesses
echo "done; update area.properties, then start with an empty database"

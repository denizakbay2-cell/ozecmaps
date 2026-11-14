[out:json][timeout:180][bbox:40.955,29.015,41.010,29.105];
(
  nwr["name"]["amenity"~"^(hospital|clinic|pharmacy|school|university|college|library|police|fire_station|townhall|courthouse|post_office|place_of_worship|theatre|cinema|arts_centre|marketplace|ferry_terminal|bus_station|fuel|community_centre|conference_centre)$"];
  nwr["name"]["tourism"~"^(museum|attraction|viewpoint|hotel|gallery)$"];
  nwr["name"]["leisure"~"^(park|stadium|sports_centre|marina)$"];
  nwr["name"]["railway"~"^(station|halt)$"];
  nwr["name"]["public_transport"="station"];
  nwr["name"]["shop"~"^(mall|department_store)$"];
  nwr["name"]["historic"];
  node["name"]["place"~"^(suburb|neighbourhood|quarter)$"];
);
out center tags qt;

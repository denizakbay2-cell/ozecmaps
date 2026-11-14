[out:json][timeout:240][bbox:40.955,29.015,41.010,29.105];
way["highway"]["highway"!~"^(proposed|construction|abandoned|disused|platform|raceway|bus_stop|elevator|corridor|via_ferrata|bridleway|escape|bus_guideway|busway|rest_area|services|emergency_bay)$"]["area"!~"^yes$"]->.r;
.r out body;
node(w.r);
out skel qt;

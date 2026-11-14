[out:json][timeout:240][bbox:40.955,29.015,41.010,29.105];
(
  nwr["name"]["shop"];
  nwr["name"]["amenity"~"^(restaurant|cafe|fast_food|bar|pub|biergarten|ice_cream|food_court|bank|atm|bureau_de_change|pharmacy|dentist|doctors|veterinary|fuel|car_wash|car_rental|bicycle_rental|cinema|theatre|nightclub|marketplace|driving_school|language_school)$"];
  nwr["name"]["tourism"~"^(hotel|hostel|guest_house|motel|apartment)$"];
  nwr["name"]["craft"];
  nwr["name"]["office"~"^(company|estate_agent|insurance|lawyer|travel_agent|accountant|architect|notary)$"];
  nwr["name"]["leisure"~"^(fitness_centre|sports_centre)$"];
);
out center tags qt;

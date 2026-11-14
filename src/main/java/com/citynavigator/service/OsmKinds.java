package com.citynavigator.service;

import com.citynavigator.entity.PlaceCategory;
import java.util.Map;
import java.util.Optional;

/**
 * Turkish names and categories for the kinds of businesses OpenStreetMap knows ({@code amenity=cafe} is a "Kafe" in
 * the "Yeme-içme" category). Used for the bundled extract and for search results from the geocoder alike.
 */
final class OsmKinds {

    private static final String[] KEYS = {"amenity", "shop", "tourism", "craft", "office", "leisure"};
    private static final Map<String, Kind> KINDS = Map.ofEntries(
            kind("amenity=restaurant", PlaceCategory.FOOD, "Restoran"),
            kind("amenity=cafe", PlaceCategory.FOOD, "Kafe"),
            kind("amenity=fast_food", PlaceCategory.FOOD, "Fast food"),
            kind("amenity=bar", PlaceCategory.FOOD, "Bar"),
            kind("amenity=pub", PlaceCategory.FOOD, "Pub"),
            kind("amenity=biergarten", PlaceCategory.FOOD, "Bira bahçesi"),
            kind("amenity=ice_cream", PlaceCategory.FOOD, "Dondurmacı"),
            kind("amenity=food_court", PlaceCategory.FOOD, "Yemek alanı"),
            kind("amenity=nightclub", PlaceCategory.FOOD, "Gece kulübü"),
            kind("amenity=bank", PlaceCategory.MONEY, "Banka"),
            kind("amenity=atm", PlaceCategory.MONEY, "ATM"),
            kind("amenity=bureau_de_change", PlaceCategory.MONEY, "Döviz bürosu"),
            kind("amenity=pharmacy", PlaceCategory.HEALTH, "Eczane"),
            kind("amenity=dentist", PlaceCategory.HEALTH, "Diş hekimi"),
            kind("amenity=doctors", PlaceCategory.HEALTH, "Muayenehane"),
            kind("amenity=veterinary", PlaceCategory.SERVICE, "Veteriner"),
            kind("amenity=fuel", PlaceCategory.FUEL, "Akaryakıt istasyonu"),
            kind("amenity=car_wash", PlaceCategory.SERVICE, "Oto yıkama"),
            kind("amenity=car_rental", PlaceCategory.SERVICE, "Araç kiralama"),
            kind("amenity=bicycle_rental", PlaceCategory.SERVICE, "Bisiklet kiralama"),
            kind("amenity=cinema", PlaceCategory.CULTURE, "Sinema"),
            kind("amenity=theatre", PlaceCategory.CULTURE, "Tiyatro"),
            kind("amenity=marketplace", PlaceCategory.SHOPPING, "Pazar yeri"),
            kind("amenity=driving_school", PlaceCategory.SERVICE, "Sürücü kursu"),
            kind("amenity=language_school", PlaceCategory.SERVICE, "Dil okulu"),
            kind("shop=supermarket", PlaceCategory.GROCERY, "Süpermarket"),
            kind("shop=convenience", PlaceCategory.GROCERY, "Bakkal / market"),
            kind("shop=greengrocer", PlaceCategory.GROCERY, "Manav"),
            kind("shop=butcher", PlaceCategory.GROCERY, "Kasap"),
            kind("shop=deli", PlaceCategory.GROCERY, "Şarküteri"),
            kind("shop=alcohol", PlaceCategory.GROCERY, "Tekel bayi"),
            kind("shop=beverages", PlaceCategory.GROCERY, "İçecek"),
            kind("shop=kiosk", PlaceCategory.GROCERY, "Büfe"),
            kind("shop=bakery", PlaceCategory.FOOD, "Fırın"),
            kind("shop=pastry", PlaceCategory.FOOD, "Pastane"),
            kind("shop=confectionery", PlaceCategory.FOOD, "Şekerci"),
            kind("shop=coffee", PlaceCategory.FOOD, "Kahveci"),
            kind("shop=mall", PlaceCategory.SHOPPING, "Alışveriş merkezi"),
            kind("shop=department_store", PlaceCategory.SHOPPING, "Büyük mağaza"),
            kind("shop=clothes", PlaceCategory.SHOP, "Giyim"),
            kind("shop=shoes", PlaceCategory.SHOP, "Ayakkabı"),
            kind("shop=jewelry", PlaceCategory.SHOP, "Kuyumcu"),
            kind("shop=optician", PlaceCategory.SHOP, "Optik"),
            kind("shop=mobile_phone", PlaceCategory.SHOP, "Cep telefonu"),
            kind("shop=electronics", PlaceCategory.SHOP, "Elektronik"),
            kind("shop=appliance", PlaceCategory.SHOP, "Beyaz eşya"),
            kind("shop=furniture", PlaceCategory.SHOP, "Mobilya"),
            kind("shop=books", PlaceCategory.SHOP, "Kitapçı"),
            kind("shop=stationery", PlaceCategory.SHOP, "Kırtasiye"),
            kind("shop=florist", PlaceCategory.SHOP, "Çiçekçi"),
            kind("shop=pet", PlaceCategory.SHOP, "Pet shop"),
            kind("shop=cosmetics", PlaceCategory.SHOP, "Kozmetik"),
            kind("shop=chemist", PlaceCategory.SHOP, "Kozmetik ve temizlik"),
            kind("shop=hardware", PlaceCategory.SHOP, "Hırdavat"),
            kind("shop=car", PlaceCategory.SHOP, "Otomobil bayisi"),
            kind("shop=bicycle", PlaceCategory.SHOP, "Bisikletçi"),
            kind("shop=sports", PlaceCategory.SHOP, "Spor malzemeleri"),
            kind("shop=toys", PlaceCategory.SHOP, "Oyuncakçı"),
            kind("shop=hairdresser", PlaceCategory.SERVICE, "Kuaför"),
            kind("shop=beauty", PlaceCategory.SERVICE, "Güzellik salonu"),
            kind("shop=car_repair", PlaceCategory.SERVICE, "Oto tamir"),
            kind("shop=dry_cleaning", PlaceCategory.SERVICE, "Kuru temizleme"),
            kind("shop=laundry", PlaceCategory.SERVICE, "Çamaşırhane"),
            kind("shop=copyshop", PlaceCategory.SERVICE, "Fotokopi"),
            kind("shop=travel_agency", PlaceCategory.SERVICE, "Seyahat acentesi"),
            kind("shop=tattoo", PlaceCategory.SERVICE, "Dövme"),
            kind("tourism=hotel", PlaceCategory.HOTEL, "Otel"),
            kind("tourism=hostel", PlaceCategory.HOTEL, "Hostel"),
            kind("tourism=guest_house", PlaceCategory.HOTEL, "Pansiyon"),
            kind("tourism=motel", PlaceCategory.HOTEL, "Motel"),
            kind("tourism=apartment", PlaceCategory.HOTEL, "Apart otel"),
            kind("office=company", PlaceCategory.SERVICE, "Şirket"),
            kind("office=estate_agent", PlaceCategory.SERVICE, "Emlakçı"),
            kind("office=insurance", PlaceCategory.SERVICE, "Sigorta"),
            kind("office=lawyer", PlaceCategory.SERVICE, "Avukat"),
            kind("office=travel_agent", PlaceCategory.SERVICE, "Seyahat acentesi"),
            kind("office=accountant", PlaceCategory.SERVICE, "Mali müşavir"),
            kind("office=architect", PlaceCategory.SERVICE, "Mimarlık ofisi"),
            kind("office=notary", PlaceCategory.SERVICE, "Noter"),
            kind("leisure=fitness_centre", PlaceCategory.PARK, "Spor salonu"),
            kind("leisure=sports_centre", PlaceCategory.PARK, "Spor merkezi"));

    private OsmKinds() {
    }

    /**
     * The business an element is, if it is one; a shop or craft the table does not name is still a business.
     */
    static Optional<Kind> businessOf(Map<String, String> tags) {
        for (String key : KEYS) {
            String value = tags.get(key);
            if (value == null) {
                continue;
            }
            Kind known = KINDS.get(key + "=" + value);
            if (known != null) {
                return Optional.of(known);
            }
            if ("shop".equals(key)) {
                return Optional.of(new Kind(PlaceCategory.SHOP, "Mağaza"));
            }
            if ("craft".equals(key)) {
                return Optional.of(new Kind(PlaceCategory.SERVICE, "Usta / atölye"));
            }
        }
        return Optional.empty();
    }

    private static Map.Entry<String, Kind> kind(String tag, PlaceCategory category, String label) {
        return Map.entry(tag, new Kind(category, label));
    }

    record Kind(PlaceCategory category, String label) {
    }
}

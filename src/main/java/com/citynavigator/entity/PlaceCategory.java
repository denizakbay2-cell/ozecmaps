package com.citynavigator.entity;

/**
 * Kinds of searchable places. The declaration order is the order of search results with equally good matches.
 */
public enum PlaceCategory {

    SETTLEMENT("Yerleşim"),
    NEIGHBOURHOOD("Semt"),
    TRANSIT("Ulaşım"),
    STREET("Cadde / sokak"),
    CULTURE("Kültür"),
    PARK("Park ve spor"),
    EDUCATION("Eğitim"),
    SHOPPING("Alışveriş merkezi"),
    PUBLIC_SERVICE("Kamu"),
    HEALTH("Sağlık"),
    FOOD("Yeme-içme"),
    GROCERY("Market"),
    SHOP("Mağaza"),
    MONEY("Banka / ATM"),
    SERVICE("Hizmet"),
    WORSHIP("İbadet"),
    HOTEL("Konaklama"),
    FUEL("Akaryakıt"),
    OTHER("Diğer");

    private final String label;

    PlaceCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}

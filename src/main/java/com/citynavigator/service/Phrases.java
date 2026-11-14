package com.citynavigator.service;

import com.citynavigator.routing.Instruction;
import com.citynavigator.routing.RoadClass;
import com.citynavigator.routing.TravelMode;

/**
 * The Turkish wording of directions and messages, kept in one place.
 */
final class Phrases {

    private Phrases() {
    }

    /**
     * Directions say what to do from where the traveller is, never a compass direction: the start is "Düz devam
     * edin", and a roundabout names its exit by number ("Dönel kavşakta 3. çıkışa girin").
     */
    static String instruction(Instruction instruction) {
        return switch (instruction.maneuver()) {
            case DEPART, CONTINUE -> "Düz devam edin";
            case SLIGHT_LEFT -> "Hafif sola dönün";
            case LEFT -> "Sola dönün";
            case SHARP_LEFT -> "Keskin sola dönün";
            case SLIGHT_RIGHT -> "Hafif sağa dönün";
            case RIGHT -> "Sağa dönün";
            case SHARP_RIGHT -> "Keskin sağa dönün";
            case U_TURN -> "U dönüşü yapın";
            case ROUNDABOUT -> instruction.exitNumber() > 0
                    ? "Dönel kavşakta " + instruction.exitNumber() + ". çıkışa girin"
                    : "Dönel kavşaktan devam edin";
            case ARRIVE -> "Hedefinize ulaştınız";
        };
    }

    /**
     * The street name, or for a nameless road what kind of road it is ("merdiven", "yaya yolu"), so walkers
     * know what to look for; {@code null} for a nameless ordinary street.
     */
    static String streetLabel(String name, RoadClass roadClass) {
        if (name != null || roadClass == null) {
            return name;
        }
        return switch (roadClass) {
            case FOOTWAY -> "yaya yolu";
            case PEDESTRIAN -> "yaya bölgesi";
            case STEPS -> "merdiven";
            case CYCLEWAY -> "bisiklet yolu";
            case PATH -> "patika";
            case TRACK -> "toprak yol";
            case SERVICE -> "servis yolu";
            case MOTORWAY_LINK, TRUNK_LINK, PRIMARY_LINK, SECONDARY_LINK, TERTIARY_LINK -> "bağlantı yolu";
            default -> null;
        };
    }

    /** "araç yolu", as in "300 m yakınında araç yolu yok". */
    static String roadFor(TravelMode mode) {
        return switch (mode) {
            case CAR -> "araç yolu";
            case BICYCLE -> "bisikletle kullanılabilecek yol";
            case FOOT -> "yürünebilir yol";
        };
    }

    /** "araçla", as in "Bu iki nokta arasında araçla gidilebilecek bir yol bulunamadı". */
    static String by(TravelMode mode) {
        return switch (mode) {
            case CAR -> "araçla";
            case BICYCLE -> "bisikletle";
            case FOOT -> "yürüyerek";
        };
    }
}

package com.citynavigator.entity;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The form names are searched by: Turkish-aware lower case, letters without diacritics, only letters, digits and
 * single spaces. "KADIKÖY", "Kadıköy" and "kadikoy" all become {@code kadikoy}, so people can type without
 * Turkish characters.
 */
public final class SearchKey {

    private static final Locale TURKISH = Locale.forLanguageTag("tr-TR");
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern SEPARATORS = Pattern.compile("[^a-z0-9]+");

    private SearchKey() {
    }

    public static String of(String text) {
        String lower = text.toLowerCase(TURKISH)
                .replace('ı', 'i')
                .replace('ç', 'c')
                .replace('ğ', 'g')
                .replace('ö', 'o')
                .replace('ş', 's')
                .replace('ü', 'u');
        String withoutMarks = MARKS.matcher(Normalizer.normalize(lower, Normalizer.Form.NFD)).replaceAll("");
        return SEPARATORS.matcher(withoutMarks).replaceAll(" ").strip();
    }
}

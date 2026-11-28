package com.rehome.rehome.component;

import com.rehome.rehome.util.VietnameseText;
import java.math.BigDecimal;
import java.util.*;

public final class OutfitPreferenceModel {
    public static final String VERSION = "multinomial-nb-v1";
    public record Item(String name, String category, String brand, String size, BigDecimal price) {}
    private final Map<String, Integer> personal = new HashMap<>(), background = new HashMap<>();
    private final Set<String> vocabulary = new HashSet<>();
    private int personalTotal, backgroundTotal;

    public OutfitPreferenceModel(List<Item> purchases, List<Item> catalogue) {
        for (Item item : purchases) personalTotal += observe(personal, item);
        for (Item item : catalogue) backgroundTotal += observe(background, item);
    }

    private int observe(Map<String, Integer> counts, Item item) {
        Set<String> tokens = features(item);
        for (String token : tokens) counts.merge(token, 1, Integer::sum);
        vocabulary.addAll(tokens);
        return tokens.size();
    }

    public double score(Item candidate) {
        if (personalTotal == 0 || backgroundTotal == 0) return 0;
        double score = 0;
        int known = 0;
        for (String token : features(candidate)) {
            if (!vocabulary.contains(token)) continue;
            double userLikelihood = (personal.getOrDefault(token, 0) + 1.0) / (personalTotal + vocabulary.size());
            double catalogueLikelihood = (background.getOrDefault(token, 0) + 1.0) / (backgroundTotal + vocabulary.size());
            score += Math.log(userLikelihood / catalogueLikelihood);
            known++;
        }
        return known == 0 ? 0 : score / known;
    }

    private static Set<String> features(Item item) {
        Set<String> tokens = new HashSet<>();
        String name = normalize(item.name());
        Arrays.stream(name.split("[^a-z0-9]+"))
                .filter(word -> word.length() > 1 && word.length() <= 50)
                .forEach(word -> tokens.add("word:" + word));
        field(tokens, "category:", item.category());
        field(tokens, "brand:", item.brand());
        field(tokens, "size:", item.size());
        if (item.price() != null && item.price().signum() > 0) {
            tokens.add("price:" + (int) Math.floor(Math.log(item.price().doubleValue()) / Math.log(2)));
        }
        return tokens;
    }

    private static void field(Set<String> tokens, String prefix, String value) {
        String normalized = normalize(value);
        if (!normalized.isBlank()) tokens.add(prefix + normalized);
    }

    private static String normalize(String value) {
        return VietnameseText.normalize(value == null ? "" : value);
    }
}

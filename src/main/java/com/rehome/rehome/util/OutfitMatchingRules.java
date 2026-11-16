package com.rehome.rehome.util;

import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.Warehouse;

import java.util.List;
import java.util.Set;

public final class OutfitMatchingRules {

    private static final List<String> COLORS = List.of(
            "xanh navy", "xanh duong", "xanh la", "xanh reu", "xanh olive",
            "trang", "den", "xam", "ghi", "be", "kem", "nau", "navy", "do", "cam", "vang", "hong", "tim");
    private static final Set<String> NEUTRALS = Set.of("trang", "den", "xam", "be", "kem", "nau", "navy");

    private OutfitMatchingRules() {
    }

    public static Traits traits(Product product) {
        String name = words(product.getName());
        String category = words(product.getCategory().getName());
        String combined = name + category;
        String color = COLORS.stream().filter(value -> contains(name, value)).findFirst()
                .orElseGet(() -> COLORS.stream().filter(value -> contains(category, value)).findFirst().orElse(""));
        color = switch (color) {
            case "ghi" -> "xam";
            case "xanh navy" -> "navy";
            case "xanh reu", "xanh olive" -> "xanh la";
            default -> color;
        };

        String style = "";
        if (containsAny(combined, "the thao", "gym", "chay bo", "jogger")) {
            style = "sport";
        } else if (containsAny(combined, "cong so", "vest", "blazer", "quan tay", "so mi")) {
            style = "formal";
        } else if (containsAny(combined, "jean", "jeans", "denim", "ao thun", "hoodie", "quan short")) {
            style = "casual";
        }
        return new Traits(color, style);
    }

    public record Traits(String color, String style) {
        public int colorScore(Traits other) {
            if (color.isEmpty() || other.color.isEmpty()) {
                return 1;
            }
            if (NEUTRALS.contains(color) || NEUTRALS.contains(other.color) || color.equals(other.color)) {
                return 3;
            }
            if (Set.of("xanh duong", "cam").containsAll(List.of(color, other.color))
                    || Set.of("vang", "tim").containsAll(List.of(color, other.color))) {
                return 2;
            }
            return 0;
        }

        public int styleScore(Traits other) {
            if (style.isEmpty() || other.style.isEmpty()) {
                return 1;
            }
            return style.equals(other.style) ? 2 : 0;
        }
    }

    public static double distanceKm(Warehouse warehouse, Double latitude, Double longitude) {
        if (latitude == null || warehouse.getLatitude() == null || warehouse.getLongitude() == null) {
            return Double.POSITIVE_INFINITY;
        }
        double warehouseLatitude = warehouse.getLatitude().doubleValue();
        double latitudeDelta = Math.toRadians(warehouseLatitude - latitude);
        double longitudeDelta = Math.toRadians(warehouse.getLongitude().doubleValue() - longitude);
        double a = Math.pow(Math.sin(latitudeDelta / 2), 2)
                + Math.cos(Math.toRadians(latitude)) * Math.cos(Math.toRadians(warehouseLatitude))
                * Math.pow(Math.sin(longitudeDelta / 2), 2);
        return 6371 * 2 * Math.asin(Math.sqrt(Math.min(1, a)));
    }

    private static String words(String text) {
        return " " + VietnameseText.normalize(text == null ? "" : text).replaceAll("[^a-z0-9]+", " ").trim() + " ";
    }

    private static boolean contains(String text, String phrase) {
        return text.contains(" " + phrase + " ");
    }

    private static boolean containsAny(String text, String... phrases) {
        for (String phrase : phrases) {
            if (contains(text, phrase)) {
                return true;
            }
        }
        return false;
    }
}

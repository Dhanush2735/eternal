package com.eternalengine.agriculture;

import java.util.Locale;

/**
 * Era/region crop selection foundation.
 */
public final class CropSystem {

    public String getCropType(String regionId, int year, String techEra) {
        String era = techEra == null ? "" : techEra.toUpperCase(Locale.ROOT);
        String region = regionId == null ? "" : regionId.toLowerCase(Locale.ROOT);
        if (year < -3000 || era.contains("STONE")) {
            return "wild_gathering";
        }
        if (era.contains("MODERN") || year >= 1945) {
            return "industrial_agriculture";
        }
        if (era.contains("INDUSTRIAL") || year >= 1750) {
            return "mechanized_grain";
        }
        if (year >= 800 && year < 1750) {
            return "three_field_rotation";
        }
        if (region.contains("asia") || region.contains("china") || region.contains("india") || region.contains("bengal")) {
            return "rice";
        }
        if (region.contains("america") || region.contains("meso") || region.contains("andes")) {
            return "maize";
        }
        if (region.contains("mediterranean") || region.contains("rome") || region.contains("egypt")) {
            return "wheat_barley";
        }
        return "mixed_grain";
    }

    public long getBaseYield(String cropType, String regionId) {
        String crop = cropType == null ? "wild_gathering" : cropType;
        return switch (crop) {
            case "wild_gathering" -> 800L;
            case "rice" -> 1800L;
            case "maize" -> 1700L;
            case "wheat_barley" -> 1500L;
            case "mixed_grain" -> 1300L;
            case "three_field_rotation" -> 2200L;
            case "mechanized_grain" -> 5000L;
            case "industrial_agriculture" -> 9000L;
            default -> 1000L;
        };
    }

    public double cropDiseaseModifier(double severity) {
        if (Double.isNaN(severity)) {
            return 1.0d;
        }
        return Math.max(0.20d, 1.0d - Math.max(0.0d, Math.min(1.0d, severity)) * 0.60d);
    }

    public boolean checkCropFailure(double climateSeverity, double diseaseSeverity, int year) {
        double combined = Math.max(0.0d, climateSeverity) * 0.7d + Math.max(0.0d, diseaseSeverity) * 0.5d;
        return combined >= 0.75d;
    }
}

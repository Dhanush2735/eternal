package com.eternalengine.villager;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Region/civilization name foundation for villager agents.
 */
public final class VillagerNameDatabase {
    private final ConcurrentHashMap<String, NameData> regionNames = new ConcurrentHashMap<>();

    public VillagerNameDatabase() {
        registerDefaults();
    }

    public void putRegion(String regionId, NameData data) {
        if (regionId != null && data != null) {
            regionNames.put(regionId, data);
        }
    }

    public String getRandomName(String region, String era, String role, Random random) {
        Random rng = random == null ? new Random() : random;
        NameData data = regionNames.getOrDefault(region, regionNames.getOrDefault("global", NameData.global()));
        String first = choose(data.firstNamesMale(), rng, "Alex");
        String surname = choose(data.surnames(), rng, "Worker");
        String title = choose(data.rolesTitles().get(role == null ? "villager" : role.toLowerCase(java.util.Locale.ROOT)), rng, "Villager");
        return title + " " + first + " " + surname;
    }

    public String getRandomFirstName(String region) {
        return choose(regionNames.getOrDefault(region, NameData.global()).firstNamesMale(), new Random(), "Alex");
    }

    public String getRandomSurname(String region) {
        return choose(regionNames.getOrDefault(region, NameData.global()).surnames(), new Random(), "Worker");
    }

    public int regionCount() {
        return regionNames.size();
    }

    private String choose(List<String> values, Random random, String fallback) {
        if (values == null || values.isEmpty()) {
            return fallback;
        }
        return values.get(Math.floorMod(random.nextInt(), values.size()));
    }

    private void registerDefaults() {
        putRegion("global", NameData.global());
        putRegion("rome", new NameData(
            List.of("Gaius", "Marcus", "Lucius"),
            List.of("Julia", "Aurelia", "Cornelia"),
            List.of("Julius", "Cornelius", "Aurelius"),
            Map.of("soldier", List.of("Legionary", "Centurion"), "merchant", List.of("Trader"), "advisor", List.of("Senator"), "villager", List.of("Citizen"))
        ));
        putRegion("maurya", new NameData(
            List.of("Chandra", "Bindu", "Ashoka"),
            List.of("Devi", "Kaurwaki", "Subhadrangi"),
            List.of("Maurya", "Gupta", "Sharma"),
            Map.of("soldier", List.of("Guard"), "merchant", List.of("Vanij"), "advisor", List.of("Mantri"), "villager", List.of("Villager"))
        ));
    }

    public record NameData(List<String> firstNamesMale, List<String> firstNamesFemale,
                           List<String> surnames, Map<String, List<String>> rolesTitles) {
        public static NameData global() {
            return new NameData(List.of("Alex", "Sam"), List.of("Alex", "Sam"),
                List.of("Stone", "River"), Map.of("villager", List.of("Villager"), "advisor", List.of("Advisor")));
        }
    }
}

package com.eternalengine.villager;

import com.eternalengine.people.DayRound;
import com.eternalengine.people.Households;
import com.eternalengine.people.Jobs;
import com.eternalengine.people.Health;
import com.eternalengine.people.Migrations;
import com.eternalengine.people.Needs;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * Virtualized villager/person state. Used when entities are far from players.
 */
public final class VirtualVillager {
    private final UUID entityUuid;
    private String civId;
    private String roleType;
    private String displayName;
    private double latitude;
    private double longitude;
    private int loyaltyScore;
    private int health;
    private boolean dead;
    private int lastUpdatedYear;
    private Households.Home home;
    private String job;
    private String gender = "neutral";
    private String era = "";

    public VirtualVillager(UUID entityUuid, String civId, String roleType, String displayName,
                           double latitude, double longitude, int loyaltyScore, int health, int lastUpdatedYear) {
        this.entityUuid = entityUuid == null ? UUID.randomUUID() : entityUuid;
        this.civId = civId == null || civId.isBlank() ? "neutral" : civId;
        this.roleType = roleType == null || roleType.isBlank() ? "VILLAGER" : roleType;
        this.displayName = displayName == null || displayName.isBlank() ? this.roleType : displayName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.loyaltyScore = clamp100(loyaltyScore);
        this.health = clamp100(health);
        this.lastUpdatedYear = lastUpdatedYear;
    }

    public void updateYear(int simYear) {
        if (dead) {
            return;
        }
        int delta = Math.max(0, simYear - lastUpdatedYear);
        if (delta > 0) {
            health = clamp100(health - Math.min(20, delta / 20));
            if (health <= 0) {
                dead = true;
            }
            lastUpdatedYear = simYear;
        }
    }

    private int clamp100(int value) {
        return Math.max(0, Math.min(100, value));
    }

    /**
     * Everything the game needs to build this person again — the same UUID (so the same skin), the
     * same name, the same civilization, role, era and gender.
     */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Most", entityUuid.getMostSignificantBits());
        tag.putLong("Least", entityUuid.getLeastSignificantBits());
        tag.putString("Civ", civId);
        tag.putString("Role", roleType);
        tag.putString("Name", displayName);
        tag.putString("Gender", gender);
        tag.putString("Era", era);
        tag.putDouble("Lat", latitude);
        tag.putDouble("Lon", longitude);
        tag.putInt("Loyalty", loyaltyScore);
        tag.putInt("Health", health);
        tag.putBoolean("Dead", dead);
        tag.putInt("Year", lastUpdatedYear);
        return tag;
    }

    /** The same person, read back out of a save. */
    public static VirtualVillager load(CompoundTag tag) {
        VirtualVillager person = new VirtualVillager(
                new UUID(tag.getLong("Most"), tag.getLong("Least")),
                tag.getString("Civ"), tag.getString("Role"), tag.getString("Name"),
                tag.getDouble("Lat"), tag.getDouble("Lon"),
                tag.getInt("Loyalty"), tag.getInt("Health"), tag.getInt("Year"));
        person.setGender(tag.getString("Gender"));
        person.setEra(tag.getString("Era"));
        person.setDead(tag.getBoolean("Dead"));
        return person;
    }

    /** The person a live entity is, before it is removed: read off the entity while it still exists. */
    public void setGender(String gender) { this.gender = gender == null || gender.isBlank() ? "neutral" : gender; }

    /**
     * Who this person lives with. Built once from the household register for their own place and
     * year, and stable thereafter: the same villager always goes home to the same people.
     */
    public Households.Home home() {
        if (home == null) {
            home = Households.get().home(entityUuid.getMostSignificantBits(), lastUpdatedYear,
                    latitude, longitude, false);
        }
        return home;
    }

    /**
     * What this person does for a living, drawn from the occupational structure of their own
     * place and year — a farmer in 1688 Essex, a craftsman in 1841 Lancashire, a clerk in
     * modern Hyderabad. Stable, like the rest of them: the same person keeps the same trade.
     */
    public String job() {
        if (job == null || job.isEmpty()) {
            job = Jobs.get().pick(entityUuid.getLeastSignificantBits(), latitude, longitude,
                    lastUpdatedYear, false);
        }
        return job;
    }

    /** "I am a farmer" — or, from the register's own words, "I am a craftsman". */
    public String jobLine() {
        return "I am a " + Jobs.plain(job()) + ".";
    }

    /** "There are five of us at home: my wife of 36, our son of 12 and my mother of 71." */
    public String homeLine() {
        Households.Home h = home();
        return h == null || h.members().isEmpty() ? "" : h.firstPerson();
    }

    /**
     * The round of this person's day, from the register for their own place and year: how long
     * the working day is and what sets it, whether there is a day on which nobody works, how
     * often the market comes round, and how many times the day stops for prayer.
     */
    public String dayLine() {
        return dayLine(null);
    }

    /** The same, with the faith whose hours the day is marked by. */
    public String dayLine(String faith) {
        return DayRound.get().line(latitude, longitude, lastUpdatedYear, false, faith);
    }

    /** "I am a farmer. There are five of us at home..." — job, household and the day itself. */
    public String aboutLine(String faith) {
        String home = homeLine();
        return jobLine() + (home.isEmpty() ? "" : " " + home) + " " + dayLine(faith);
    }

    /**
     * What this person needs and how they are bearing it, read off the registers rather than
     * invented: food from the hazard pack's own effect table, safety from the same place, faith
     * from how many times their day stops to pray, and money from whether there is a market and
     * how much of the week the work takes.
     */
    public String needsLine(String faith) {
        return Needs.get().personLine(latitude, longitude, lastUpdatedYear, false, job(), faith);
    }

    /** One paragraph on how life is here, for a player who asks. */
    public String situationLine(String faith) {
        return Needs.get().line(latitude, longitude, lastUpdatedYear, false, job(), faith);
    }

    /** What the roads out of this place are doing in this year, as step 38's migration register
     * reads it - who is on the move past here, how many, and how long the road takes. Says plainly
     * when no movement was ever counted, because a silence is not an empty road. */
    public String roadsLine(int year) {
        return Migrations.line(latitude, longitude, year);
    }

    /** What can kill this person here, and what anybody here can do about it, as step 38's health
     * register reads it. Unlike everything else in this class this is not a question about the
     * year: penicillin reached Oxford years before it reached the places that needed it, and the
     * register says so rather than curing people with drugs that never came near them. */
    public String healthLine(int year) {
        return Health.line(latitude, longitude, year);
    }

    public String gender() { return gender; }

    public void setEra(String era) { this.era = era == null ? "" : era; }

    public String era() { return era; }

    public UUID entityUuid() { return entityUuid; }
    public String civId() { return civId; }
    public void setCivId(String civId) { this.civId = civId == null ? this.civId : civId; }
    public String roleType() { return roleType; }
    public void setRoleType(String roleType) { this.roleType = roleType == null ? this.roleType : roleType; }
    public String displayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName == null ? this.displayName : displayName; }
    public double latitude() { return latitude; }
    public double longitude() { return longitude; }
    public void setLocation(double latitude, double longitude) { this.latitude = latitude; this.longitude = longitude; }
    public int loyaltyScore() { return loyaltyScore; }
    public void setLoyaltyScore(int loyaltyScore) { this.loyaltyScore = clamp100(loyaltyScore); }
    public int health() { return health; }
    public void setHealth(int health) { this.health = clamp100(health); }
    public boolean dead() { return dead; }
    public void setDead(boolean dead) { this.dead = dead; }
    public int lastUpdatedYear() { return lastUpdatedYear; }
}

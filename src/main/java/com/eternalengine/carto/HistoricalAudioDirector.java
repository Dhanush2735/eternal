package com.eternalengine.carto;

import com.eternalengine.civilization.SettlementRank;
import com.eternalengine.civilization.Soundscape;
import com.eternalengine.terrabridge.TerraCoordinateHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Step 43 (J2): the sound of a place and year, now worked out instead of guessed.
 *
 * <h3>What this replaced</h3>
 *
 * <p>This class used to hold an enum of six soundscapes and a method
 * {@code determineSoundscape(double x, double z, int year, boolean nearActiveWar)} that
 * <b>took {@code x} and {@code z} and never read either of them</b>. The year alone decided. So:
 *
 * <ul>
 *   <li>China in 1000 and Norway in 1000 and the Aztecs in 1000 all came back
 *       {@code CLASSICAL_MEDITERRANEAN};</li>
 *   <li>everything from 1400 to 1750 came back {@code RENAISSANCE_EUROPE}, everywhere on
 *       earth;</li>
 *   <li>and everything after 1920 came back {@code PASTORAL_ANCIENT} — the two loudest centuries
 *       there have ever been, filed as prehistoric countryside.</li>
 * </ul>
 *
 * <p>It then played note-block sounds and printed a chat line announcing the soundscape. The chat
 * line is gone: a player did not ask to be told what century the code thinks they are in, and
 * saying it did not make it true.
 *
 * <h3>What it does now</h3>
 *
 * <p>It asks {@link Soundscape}, which answers from the place:
 *
 * <ul>
 *   <li><b>voice</b> — the holy places standing within 25 km that year, then the faith of the
 *       polity ruling here. Place-based and dated, so Mecca has a voice even though the Ottoman
 *       survey carries no religion.</li>
 *   <li><b>machine</b> — the year the technology reached this country: York's railway 1827,
 *       Delhi's 1864, Tokyo's 1872.</li>
 *   <li><b>ground</b> — whether the shore is here.</li>
 * </ul>
 *
 * <p><b>The crowd is not here.</b> {@link AmbientCrowdSoundManager} already does it, and does it
 * well: it scales a murmur by the simulated population rather than the number of bodies drawn, and
 * step 17 reworked it from once a tick to once a second after it became the costliest system in
 * {@code /ee performance}. Duplicating it would be the same mistake in a new place.
 *
 * <h3>What it cannot do, and says so</h3>
 *
 * <p><b>The mod has no audio assets.</b> This class can decide <i>which</i> sound belongs here and
 * <i>why</i> — which is the half that was nonsense — but it must borrow stock sounds to play it.
 * A bell tower can ring {@code BELL_BLOCK}; a muezzin cannot be synthesised, and no amount of
 * pretending otherwise makes it one. {@link #cannotPlay()} reports every row that is sounding and
 * has no sound to play for it, so the gap is visible instead of silent.
 */
public final class HistoricalAudioDirector {

    /** The register this director now reads. */
    private final Soundscape soundscape = Soundscape.get();

    // ---------------------------------------------------------------- the question

    /**
     * The soundscape of a place in a year. Replaces the old enum, which took {@code x} and
     * {@code z} and never read them.
     */
    public Soundscape.Here determineSoundscape(double lat, double lon, int year,
                                               SettlementRank rank, long population) {
        return soundscape.hereAt(lat, lon, year, rank == null ? SettlementRank.VILLAGE : rank,
                population);
    }

    /** The soundscape where a player is standing. */
    public Soundscape.Here hereAt(ServerPlayer player, int year, long population) {
        if (player == null) {
            return null;
        }
        var geo = TerraCoordinateHelper.toGeoPoint(player.blockPosition());
        if (geo == null) {
            return null;
        }
        return determineSoundscape(geo.latitude(), geo.longitude(), year,
                com.eternalengine.core.SimulationEngine.getInstance() == null
                        ? SettlementRank.VILLAGE : SettlementRank.TOWN,
                population);
    }

    /**
     * Plays what can be played for where the player is standing. No chat message: the player did
     * not ask, and the old one announced a guess as though it were a fact.
     *
     * <p>The crowd layer is deliberately absent — see {@link AmbientCrowdSoundManager}.
     */
    public void playAtmosphere(ServerPlayer player, int year, boolean nearActiveWar) {
        if (player == null) {
            return;
        }
        Soundscape.Here here = hereAt(player, year, 0L);
        if (here == null) {
            return;
        }
        if (nearActiveWar) {
            // A war is a sound with a place of its own and this step does not model it. What it
            // can say is that the machines and the voices go on underneath it.
            player.playNotifySound(SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(0).get(),
                    SoundSource.HOSTILE, 1.0F, 1.0F);
        }
        for (Soundscape.Sound s : here.all()) {
            play(player, s.row().id());
        }
    }

    /** One line naming what is sounding here, for {@code /ee ... audio atmosphere}. */
    public String describe(ServerPlayer player, int year) {
        Soundscape.Here here = hereAt(player, year, 0L);
        if (here == null) {
            return "No place to describe.";
        }
        return here.line();
    }

    // ---------------------------------------------------------------- what exists to play

    /** The stock sounds that stand for a row. A row absent from this map is a row with no sound. */
    private static net.minecraft.sounds.SoundEvent soundFor(String id) {
        return switch (id) {
            case "church_bells", "orthodox_bells", "temple_bells", "shrine_clap", "shofar" ->
                    SoundEvents.BELL_BLOCK;
            case "trains" -> SoundEvents.MINECART_RIDING;
            case "ironworks", "steam_works" -> SoundEvents.ANVIL_USE;
            case "cars", "aircraft" -> SoundEvents.ELYTRA_FLYING;
            case "watermill" -> SoundEvents.WATER_AMBIENT;
            case "firecrackers" -> SoundEvents.FIREWORK_ROCKET_LAUNCH;
            case "sea" -> SoundEvents.AMBIENT_UNDERWATER_LOOP;
            default -> null;   // no asset exists; see cannotPlay()
        };
    }

    private static void play(ServerPlayer player, String id) {
        net.minecraft.sounds.SoundEvent ev = soundFor(id);
        if (ev == null) {
            return;
        }
        player.playNotifySound(ev, SoundSource.AMBIENT, 0.5F, 1.0F);
    }

    // ---------------------------------------------------------------- the honest part

    /**
     * The rows that are sounding somewhere in the world and have no sound to play for them.
     *
     * <p>This is the step's real limit, stated as data rather than buried in a comment: the mod
     * has no audio, so a call to prayer resolves correctly and then plays nothing. Printing the
     * list is the difference between a missing feature and a silent one.
     */
    public java.util.List<String> cannotPlay() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (Soundscape.Row r : soundscape.rows()) {
            if (soundFor(r.id()) == null) {
                out.add(r.id());
            }
        }
        return out;
    }

    /** How much of the pack can actually be heard. */
    public String coverage() {
        int total = Math.max(1, soundscape.rows().size());
        int playable = total - cannotPlay().size();
        return playable + " of " + total + " sounds can be played; the rest are decided correctly "
                + "and heard as nothing";
    }

    /** Shown by {@code /ee sound}. */
    public String summary() {
        return soundscape.rowCount() + " sounds over " + soundscape.rows().size()
                + " rows — " + coverage();
    }

    /** Kept for the command that wants to print it. */
    public static Component title() {
        return Component.literal("Soundscape");
    }
}

package com.eternalengine.skin;

import com.eternalengine.EternalEngineMod;
import com.eternalengine.skin.SkinPoolDatabase.Face;
import com.eternalengine.skin.SkinPoolDatabase.Outfit;
import com.eternalengine.skin.SkinPlaceResolver.Centre;
import com.eternalengine.skin.SkinPlaceResolver.Place;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * What the game calls. One of these per server, beside {@code VillagerManager}.
 *
 * <h3>The whole contract, in four lines</h3>
 * <pre>
 *   Place  p = skin.placeAt(geo.lat(), geo.lon(), civId);   // from TerraCoordinateHelper.toGeo
 *   Face   f = skin.faceFor(npc.getUUID(), female, p);      // never changes
 *   Outfit o = skin.outfitFor(npc.getUUID(), female, npc.getRole(), p, year, day);  // changes daily
 *   Skin   s = skin.paint(f, o, p, year, role, female);     // the 64x64 the renderer needs
 * </pre>
 *
 * <p>Nothing here touches Minecraft classes on purpose - the same reason {@code SkinVariety} does not:
 * it makes the whole system testable from a main method, and it keeps the world out of the arithmetic.
 * The caller has the world clock ({@code SimulationClockManager.getCurrentYear()}) and the bridge
 * ({@code TerraCoordinateHelper.toGeo}); this class has only the decision.
 *
 * <p><b>The year is held.</b> A person keeps the year he was stood up in - that rule was set for the
 * talk system and it holds here: his clothes and his cut follow his own year, not the ticking one.
 */
public final class SkinManager {

    private static SkinManager instance;

    private final SkinPoolDatabase database = new SkinPoolDatabase();
    private final Map<String, int[]> textureCache = new ConcurrentHashMap<>();
    private final Map<String, Face> faceCache = new ConcurrentHashMap<>();
    private final AtomicLong painted = new AtomicLong();

    public SkinManager(Path worldRoot) {
        // a world pack lives beside the names: <world>/eternalengine/skins/skin_pool.json
        database.loadFrom(worldRoot == null ? null : worldRoot.resolve("skins"));
    }

    /** The server's one instance. Null until {@link #boot(Path)} - never lazily built off-thread. */
    public static SkinManager get() {
        return instance;
    }

    public static SkinManager boot(Path worldRoot) {
        instance = new SkinManager(worldRoot);
        instance.logSummary();
        return instance;
    }

    /**
     * The client's own instance, built on demand with no world folder: a client's world folder is the
     * server's, or is not there at all, so it reads the pack shipped in the jar. Called by
     * {@code SkinTextures} when nothing has booted this side - an integrated server boots both, a
     * dedicated client boots neither, and both work.
     */
    public static synchronized SkinManager client() {
        if (instance == null) {
            instance = new SkinManager(null);
        }
        return instance;
    }

    public SkinPoolDatabase pools() {
        return database;
    }

    // ==================== the decision ====================

    /** Which people this ground belongs to. {@code civId} is the place's civ; coordinates are geo. */
    public Place placeAt(double lat, double lon, String civId) {
        return SkinPlaceResolver.resolve(lat, lon, civId);
    }

    /**
     * The face. The tone and the hair colour come from the ground (the sub-pool's own colours where it
     * has them, the culture's otherwise), the style from the person - so a village looks related and a
     * stranger looks like a stranger.
     */
    public Face faceFor(UUID uuid, boolean female, Place place) {
        String key = (uuid == null ? "?" : uuid.toString()) + "|" + (female ? "f" : "m") + "|"
                + place.key();
        return faceCache.computeIfAbsent(key, k -> {
            Centre c = SkinPlaceResolver.centreOf(place.culture(), place.subpool());
            return database.faceFor(uuid, female, place.culture(), place.flavour(),
                    c == null ? null : c.tones(), null);
        });
    }

    /** The clothes for one day. A uniform trade gets one cloth; the year must have reached the wardrobe. */
    public Outfit outfitFor(UUID uuid, boolean female, String role, Place place, int year, long day) {
        return database.outfitFor(uuid, female, role, place.culture(), year, day);
    }

    /**
     * The four cloth colours this person is dressed in: shirt, outer garment, trousers, trim.
     *
     * <p>A town shares a <b>family</b> of dye colours, not one colour: the three pieces are drawn
     * apart from the family, so neighbours look related and nobody is dressed from a single bolt.
     */
    public int[] coloursFor(UUID uuid, boolean female, String role, Place place, int year, long day) {
        Outfit outfit = outfitFor(uuid, female, role, place, year, day);
        boolean uniform = outfit.uniform();
        Centre c = SkinPlaceResolver.centreOf(place.culture(), place.subpool());
        if (c != null && c.cloths() != null) {
            int[] family = c.cloths();
            String who = uuid == null ? "unspawned" : uuid.toString();
            long d = uniform ? 0L : day;
            int trim = database.pool(place.culture()).trim();
            if (uniform) {
                int cloth = family[SkinHashes.pick(place.flavour() + "|cloth|" + who, family.length)];
                return new int[]{cloth, cloth, cloth, trim};
            }
            return new int[]{
                    family[SkinHashes.pick(place.flavour() + "|top|" + who + "|" + d, family.length)],
                    family[SkinHashes.pick(place.flavour() + "|outer|" + who + "|" + d, family.length)],
                    family[SkinHashes.pick(place.flavour() + "|bottom|" + who + "|" + d, family.length)],
                    trim
            };
        }
        return database.paletteFor(place.culture(), place.flavour(), uuid, day, uniform);
    }

    /** Paint the 64x64. Cached by everything that decides the picture: person, day, year, role, place. */
    public SkinPainter.Skin paint(UUID uuid, boolean female, String role, Place place, int year,
                                  long day) {
        return paint(uuid, female, role, place, year, day, true);
    }

    /**
     * The same person, with the accessories shown or left off.
     *
     * <p>Off gives back exactly the same face, cloth, hair and jewellery, without the sash, the
     * shoulder cloth, the pouch, the forehead band, the armlet and the ankle band. The customizer's
     * toggle needs the pair; the world always asks for them.
     */
    public SkinPainter.Skin paint(UUID uuid, boolean female, String role, Place place, int year,
                                  long day, boolean accessories) {
        return paint(uuid, female, role, place, year, day,
                accessories ? SkinPainter.ACC_ALL : SkinPainter.ACC_NONE);
    }

    /**
     * The same person, with any single accessory on or off.
     *
     * @param accessories a mask of {@code SkinPainter.A_*} and {@code SkinPainter.J_*} bits
     */
    public SkinPainter.Skin paint(UUID uuid, boolean female, String role, Place place, int year,
                                  long day, int accessories) {
        Face face = faceFor(uuid, female, place);
        Outfit outfit = outfitFor(uuid, female, role, place, year, day);
        int[] colours = coloursFor(uuid, female, role, place, year, day);
        // the person's own number: the face, the build, the weave and the jewellery are all drawn from
        // it, so one person keeps one skin and two people of the same town do not share one
        long seed = NpcProfile.seedOf(uuid);
        SkinPainter.Skin skin = SkinPainter.paint(database.pool(place.culture()), face, outfit, colours,
                year, role, female, seed, place.subpool(), accessories, place.lat(), place.lon());
        painted.incrementAndGet();
        return skin;
    }

    /**
     * The key a rendered texture is cached under. It is a function of <b>the picture</b>, not of the
     * person: two people who come out the same face in the same clothes share one texture, which is the
     * whole reason a crowd does not cost a crowd's worth of memory. A uniform pins the day out of the
     * key, because a uniform does not change.
     */
    public String textureKey(UUID uuid, boolean female, String role, Place place, int year, long day) {
        return textureKey(uuid, female, role, place, year, day, true);
    }

    /** The same key, told apart by whether the accessories are on. */
    public String textureKey(UUID uuid, boolean female, String role, Place place, int year, long day,
                             boolean accessories) {
        return textureKey(uuid, female, role, place, year, day,
                accessories ? SkinPainter.ACC_ALL : SkinPainter.ACC_NONE);
    }

    /** The same key, told apart by which accessories are on: eleven bits, so two people cannot
     *  share a cached picture while wearing different things. */
    public String textureKey(UUID uuid, boolean female, String role, Place place, int year, long day,
                             int accessories) {
        Face f = faceFor(uuid, female, place);
        Outfit o = outfitFor(uuid, female, role, place, year, day);
        long seed = NpcProfile.seedOf(uuid);
        return f.tone() + "|" + f.hair() + "|" + f.style() + "|" + (female ? "f" : "m") + "|"
                + SkinVariation.of(seed, place.culture(), f.style(), female, year,
                        database.pool(place.culture()).femaleJewels(),
                        Ages.years(seed, year, place.lat(), place.lon(), role)).seed() + "|"
                + NpcProfile.of(seed, place.culture(), role, female, year,
                        database.pool(place.culture()), f.tone()).style() + "|"
                + (role == null ? "citizen" : role) + "|" + place.key() + "|" + year + "|"
                + String.join("+", o.garments()) + "|" + (o.uniform() ? "u" : "d" + day)
                + "|a" + Integer.toHexString(accessories);
    }

    public long painted() {
        return painted.get();
    }

    /**
     * What the log says once, at boot. Honest about the two things this cannot know: how a province was
     * named, and whether the palette pleases the eye.
     */
    public void logSummary() {
        String[] probe = {"deccan", "indic", "chandela", "songhai_empire", "adil_shahi", "mughal"};
        StringBuilder sb = new StringBuilder();
        for (String id : probe) {
            sb.append(id).append("->").append(database.poolFor(id)).append(' ');
        }
        EternalEngineMod.getLogger().info(
                "[SkinManager] {} cultures, {} sub-pools; {} | painted {} skins so far",
                database.cultureCount(), SkinPlaceResolver.subpoolCount(), sb.toString().trim(),
                painted.get());
    }

    /**
     * A census of one place: how many distinct faces a crowd of this size actually needs, how many
     * distinct pictures that is over a week of days, and what those pictures weigh. The number that
     * matters is the last one - it is the answer to "what does a crowd cost".
     */
    public record Census(String place, int crowd, int uniforms, int faces, int pictures, long bytes) {
        public long kb() {
            return bytes / 1024;
        }
    }

    public Census census(double lat, double lon, String civId, int crowd) {
        Place place = placeAt(lat, lon, civId);
        Map<String, Integer> faces = new java.util.HashMap<>();
        Map<String, Integer> pictures = new java.util.HashMap<>();
        int uniforms = 0;
        for (int i = 0; i < crowd; i++) {
            UUID u = UUID.nameUUIDFromBytes(("census-" + i + "|" + place.key())
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            boolean female = i % 2 == 0;
            Face f = faceFor(u, female, place);
            String role = switch (i % 6) {
                case 0 -> "soldier"; case 1 -> "farmer"; case 2 -> "merchant";
                case 3 -> "artisan"; case 4 -> "guard"; default -> "citizen";
            };
            if (outfitFor(u, female, role, place, 1990, 0).uniform()) {
                uniforms++;
            }
            faces.merge(f.tone() + "/" + f.hair() + "/" + f.style(), 1, Integer::sum);
            for (long day = 0; day < 7; day++) {
                pictures.merge(textureKey(u, female, role, place, 1990, day), 1, Integer::sum);
            }
        }
        // a 64x64 skin is 4096 pixels: what the renderer would hold, before any compression
        long bytes = (long) pictures.size() * 64 * 64 * 4;
        return new Census(place.key(), crowd, uniforms, faces.size(), pictures.size(), bytes);
    }
}

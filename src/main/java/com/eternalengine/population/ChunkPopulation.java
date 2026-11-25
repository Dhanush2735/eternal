package com.eternalengine.population;

import com.eternalengine.civilization.SettlementRank;

/**
 * The population of a place, divided over the area it lives in — one chunk at a time.
 *
 * <h3>The idea</h3>
 * A place has a population and an extent. The extent is a real number from the same data the
 * population comes from: Secunderabad is 6 km, Hyderabad 8 km. Divide the one by the other and you
 * get the average number of people standing in any 16×16 block of that place:
 *
 * <table>
 *   <tr><th>place</th><th>people</th><th>extent</th><th>area</th><th>chunks</th><th>people per chunk</th></tr>
 *   <tr><td>Akhanpally Thanda, 1948</td><td>348</td><td>2 km</td><td>12.6 km²</td><td>49,087</td><td>0.007</td></tr>
 *   <tr><td>Secunderabad, 1948</td><td>142,174</td><td>6 km</td><td>113.1 km²</td><td>441,786</td><td>0.32</td></tr>
 *   <tr><td>Hyderabad, 1948</td><td>980,600</td><td>8 km</td><td>201.1 km²</td><td>785,398</td><td>1.25</td></tr>
 * </table>
 *
 * <p>That is an honest number and it is far too thin to look like anything: 0.007 people in a chunk
 * is one person every 140 chunks. Which is the point — <b>a city's population is not spread evenly
 * over its area</b>. It lives in houses, and it gathers. So the equal share is not the answer; it is
 * the <em>budget</em>.
 *
 * <h3>What this class does with the budget</h3>
 * The population is conserved exactly: every person is somewhere in the area, and the sum of all
 * chunks' people is the population. What varies is how the area's people are arranged —
 * {@link #weight} gives a chunk near the place's heart a larger share of them and a chunk at the
 * edge a smaller one, and the shares are normalised so the total does not change. At
 * {@code concentration == 0} every chunk gets exactly the average (the plain division); at the
 * default the heart's chunk can be packed to a real crowd's density while the fields around it
 * carry almost nobody.
 *
 * <p>This is what a real city looks like from above: the mean is small, the variance is enormous,
 * and the market square is paid for by everything else in the district.
 *
 * <h3>What it costs</h3>
 * Nothing per chunk that is not near a player. The crowd is only <em>stood up</em> inside the
 * player's interest radius — a few dozen chunks — and the rest of the area exists as arithmetic.
 * The number of figures drawn is bounded by {@link #CHUNK_CAPACITY} a chunk and by the spawner's
 * drawing budget, so a bigger place does not cost more to stand in.
 */
public final class ChunkPopulation {

    private ChunkPopulation() {}

    /** A chunk is 16×16 blocks. */
    public static final int CHUNK_SIDE = 16;

    /** A chunk's floor area in square metres. */
    public static final double CHUNK_AREA_M2 = CHUNK_SIDE * CHUNK_SIDE;

    /**
     * The most people one chunk can show: a chunk of 256 m², packed at the crowd density the game
     * already draws at ({@link CrowdDensity#TARGET_DENSITY}, 0.3 per square metre), holds
     * seventy-seven people standing shoulder to shoulder — and that is a chunk you cannot walk
     * through.
     */
    public static final int CHUNK_CAPACITY = (int) Math.round(CHUNK_AREA_M2 * CrowdDensity.TARGET_DENSITY);

    /**
     * How far from the heart the gathering reaches, in blocks. A hundred blocks is a market's
     * catchment: the square, the streets feeding it, and the edge of the ordinary town.
     */
    public static final double GATHER_DECAY_BLOCKS = 100.0d;

    /**
     * The default concentration — how much more than the average a chunk at the very heart carries.
     *
     * <p>Zero is the plain equal division. The default was calibrated against what a view can
     * afford, not against what would look best: for Secunderabad, 40 puts thirteen people in the
     * heart's chunk and about five hundred in a sixty-four block view, which is a busy area and
     * about what the client draws today. Raising it packs the heart further — a concentration of
     * 241 fills the heart's chunk to {@link #CHUNK_CAPACITY} — but it also fills every chunk within
     * a hundred blocks of it, and the view then holds thousands. That is the recorded-mesh route's
     * job, not this one's; here the drawing budget decides how many of the planned people are
     * actually stood up.
     */
    public static final double DEFAULT_CONCENTRATION = 40.0d;

    /** Square metres in a square kilometre. */
    private static final double M2_PER_KM2 = 1_000_000.0d;

    /**
     * The density a drawn crowd reaches when the figures are packed into the place's district —
     * 0.551 people per square metre, measured by {@code CeilingProbe} for modern Hyderabad with
     * 2,771 figures inside a 40-block district. It is the number the drawing budget should aim at:
     * above about 0.3 a square metre a crowd reads as busy, and 0.5 to 1.0 is a crowd that is
     * standing still because it cannot move.
     */
    public static final double PACKED_DENSITY_PER_M2 = 0.551d;

    /** The area a place covers, in square metres, for an extent of this many kilometres. */
    public static double areaM2(double radiusKm) {
        double km = Math.max(0.0d, radiusKm);
        return Math.PI * km * km * M2_PER_KM2;
    }

    /** The same area in square kilometres. */
    public static double areaKm2(double radiusKm) {
        return areaM2(radiusKm) / M2_PER_KM2;
    }

    /** How many chunks fit in the place. A disc, counted whole. */
    public static long chunksIn(double radiusKm) {
        return Math.max(1L, Math.round(areaM2(radiusKm) / CHUNK_AREA_M2));
    }

    /** The equal division: every chunk of the place, the same number of people. */
    public static double perChunk(long population, double radiusKm) {
        return population / (double) chunksIn(radiusKm);
    }

    /** The place's average density, in people per square metre. */
    public static double perM2(long population, double radiusKm) {
        double area = areaM2(radiusKm);
        return area <= 0.0d ? 0.0d : population / area;
    }

    /**
     * How much of the place's population a chunk at this distance from its heart carries, relative
     * to a chunk at the edge. One everywhere when nothing gathers.
     *
     * @param distanceBlocks how far the chunk is from the place's heart
     * @param concentration  how strongly people gather; 0 is the plain equal division
     */
    public static double weight(double distanceBlocks, double concentration) {
        if (concentration <= 0.0d) {
            return 1.0d;
        }
        double t = distanceBlocks / GATHER_DECAY_BLOCKS;
        return 1.0d + concentration * Math.exp(-t * t);
    }

    /**
     * The total weight of the whole place, so shares can be normalised to conserve the population.
     *
     * <p>Counted ring by ring rather than chunk by chunk: every chunk in a ring is the same distance
     * from the heart, to within the ring's width, so the ring is one multiplication instead of
     * hundreds. A 6 km place is 375 rings instead of 441,786 chunks — and the error is the ring's
     * width against the place's radius, which for a city is under a thousandth of a percent.
     */
    public static double totalWeight(double radiusKm, double concentration) {
        long chunks = chunksIn(radiusKm);
        if (concentration <= 0.0d) {
            return chunks;
        }
        int rings = (int) Math.ceil(radiusKm * 1000.0d / CHUNK_SIDE);
        double sum = chunks; // the flat part: every chunk at the edge's weight
        for (int ring = 0; ring < rings; ring++) {
            double d = ring * (double) CHUNK_SIDE;
            double cells = ring == 0 ? 1.0d : Math.max(1.0d, 2.0d * Math.PI * d / CHUNK_SIDE);
            sum += concentration * cells * Math.exp(-Math.pow(d / GATHER_DECAY_BLOCKS, 2.0d));
        }
        return sum;
    }

    /**
     * How many people a chunk carries, as the exact average share times its weight — a real number,
     * because a chunk holding a third of a person is the truth about a countryside.
     */
    public static double peopleInChunk(long population, double radiusKm, double distanceBlocks,
                                       double concentration) {
        if (population <= 0L) {
            return 0.0d;
        }
        return population * weight(distanceBlocks, concentration) / totalWeight(radiusKm, concentration);
    }

    /**
     * Figures that chunk should show, rounded to the nearest person and never more than a chunk can
     * hold. For tables and totals; {@link #figuresInChunk} is what a chunk actually stands up.
     */
    public static int figuresExpectedInChunk(long population, double radiusKm, double distanceBlocks,
                                             double concentration) {
        double people = peopleInChunk(population, radiusKm, distanceBlocks, concentration);
        if (people <= 0.0d) {
            return 0;
        }
        return (int) Math.min(CHUNK_CAPACITY, Math.round(people));
    }

    /**
     * What one particular chunk stands up: its share of the population, rounded <em>in the
     * average</em> rather than per chunk.
     *
     * <p>This is the only way the equal division survives arithmetic. An average chunk of
     * Secunderabad holds 0.322 people: round that off per chunk and every chunk of the place holds
     * nobody, and a hundred and forty-two thousand people have silently become zero. So the whole
     * people are handed out first, and the fraction decides a lottery the chunk's own hash runs: a
     * chunk carrying 0.322 people shows one person about a third of the time. Over the place the
     * count is the population, within a person; in any single chunk it is a person or nobody, which
     * is the only thing a chunk can be.
     *
     * <p>Deterministic, like everything else here: the same chunk of the same world always shows the
     * same person.
     *
     * @param chunkHash a stable value for this chunk — the world seed mixed with the chunk's
     *                  coordinates; the same hash always gives the same answer
     */
    public static int figuresInChunk(long population, double radiusKm, double distanceBlocks,
                                     double concentration, long chunkHash) {
        return figuresForPeople(peopleInChunk(population, radiusKm, distanceBlocks, concentration),
                chunkHash);
    }

    /**
     * What one chunk stands up for a given number of people, whole or fractional.
     *
     * <p>The whole people are handed out first and the fraction decides a lottery the chunk's own
     * hash runs, so an average of a third of a person is a person a third of the time rather than
     * nobody always. Over a place the count is the population, within a person.
     */
    static int figuresForPeople(double people, long chunkHash) {
        if (people <= 0.0d) {
            return 0;
        }
        if (people >= CHUNK_CAPACITY) {
            return CHUNK_CAPACITY;
        }
        int whole = (int) people;
        if (people - whole > uniform(chunkHash)) {
            whole++;
        }
        return Math.min(CHUNK_CAPACITY, whole);
    }

    /** A stable number in [0, 1) for a chunk: its own private coin. */
    public static double uniform(long chunkHash) {
        long mixed = chunkHash;
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53L;
        mixed ^= mixed >>> 33;
        return (mixed >>> 11) * 0x1.0p-53;
    }

    /** The hash a chunk's coin is tossed with: the world's seed and the chunk's coordinates. */
    public static long chunkHash(long worldSeed, int chunkX, int chunkZ) {
        return worldSeed ^ ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

    /**
     * How many figures stand within a view radius of a player who is this far from the place's heart
     * — the number that decides what the frame costs.
     *
     * <p>Summed over the chunks actually in range, capped the same way each chunk is, so it is the
     * real count and not an estimate.
     */
    public static long inView(long population, double radiusKm, double playerFromHeartBlocks,
                              double viewRadiusBlocks, double concentration) {
        if (population <= 0L || viewRadiusBlocks <= 0.0d) {
            return 0L;
        }
        double mass = totalWeight(radiusKm, concentration);
        int reach = (int) Math.ceil(viewRadiusBlocks / CHUNK_SIDE) + 1;
        double viewSq = viewRadiusBlocks * viewRadiusBlocks;
        double total = 0.0d;
        for (int cx = -reach; cx <= reach; cx++) {
            for (int cz = -reach; cz <= reach; cz++) {
                double dx = cx * (double) CHUNK_SIDE;
                double dz = cz * (double) CHUNK_SIDE;
                if (dx * dx + dz * dz > viewSq) {
                    continue;
                }
                double toHeartX = playerFromHeartBlocks + dx;
                double toHeartZ = dz;
                double dist = Math.hypot(toHeartX, toHeartZ);
                double people = population * weight(dist, concentration) / mass;
                total += Math.min(CHUNK_CAPACITY, people);
            }
        }
        return Math.round(total);
    }

    /**
     * How wide the ground in front of the player is, in degrees, for the purpose of standing the
     * crowd up. Minecraft's field of view is set vertically and comes out about a hundred degrees
     * across a 16:9 screen, so a hundred-degree wedge is the ground the player is actually looking
     * at, give or take the vertical angle.
     */
    public static final double DRAWN_WEDGE_DEGREES = 100.0d;

    /** The sixteen points of a chunk the facing test samples: a quarter of the way in from each edge. */
    private static final double[] WEDGE_SAMPLES = {2.0d, 6.0d, 10.0d, 14.0d};

    /**
     * What is stood up when the crowd is drawn <em>where the player is looking</em>.
     *
     * <p>{@link #sampledView} spends the client's budget evenly all round the player, which is
     * honest and wastes most of it: standing in a place, the ground behind and beside you is not on
     * screen. This spends the same budget — the same total number of figures, never one more — on
     * the chunks that are in front, in proportion to how much of each chunk is in front. A chunk
     * seen edge-on gets a few figures; a chunk square in the view gets its full share. The crowd is
     * then as dense as the budget allows along the line of sight, and the place reads as populated
     * from where the player actually stands looking at it.
     *
     * <p>What does not change: the population, the resolver's number, the per-chunk residency the
     * manifest reports for the model, and the ceiling — no chunk is ever given more than
     * {@link #CHUNK_CAPACITY} figures, one person per 3.3 m². Only the choice of which part of the
     * place's own crowd is drawn moves.
     *
     * <p>Ground inside {@code bubbleBlocks} of the player is weighted out, because the spawner
     * refuses a figure that stands there: planning figures into a bubble they cannot use would
     * spend the budget on nobody. If nothing at all is in front, the plain division is used.
     *
     * @param view          the place's view of the player, from {@link #viewOfCore}
     * @param budget        how many figures the frame can afford around this player
     * @param playerX       the player's position in blocks; the wedge is measured from here
     * @param playerZ       the player's position in blocks
     * @param facingDegrees the player's yaw, in Minecraft's convention: 0 looks to +Z, 90 to -X
     * @param bubbleBlocks  vanilla's no-spawn radius, which the spawner enforces per figure
     * @return what to stand up per chunk, indexed as the view
     */
    /**
     * The same drawing, with a note of <em>which place in each chunk's plan</em> every drawn figure
     * stands on.
     *
     * <p>A chunk's plan is fixed by the ground — the same lattice of places on every visit — so a
     * place in it is the same place for ever, and whoever stands there is the same person. That is
     * what lets a crowd be recognised when a player comes back, and it is why a figure has to be told
     * <em>which</em> place it is taking and not merely how far along the view it stands.
     *
     * @param chosenOut one plan slot per drawn figure, in view order; -1 for a place left empty
     */
    public static int[] viewTowards(View view, int budget, double playerX, double playerZ,
                                    double facingDegrees, double bubbleBlocks, long worldSeed,
                                    int[] chosenOut) {
        int[] shown = viewTowards(view, budget, playerX, playerZ, facingDegrees, bubbleBlocks);
        int at = 0;
        for (int i = 0; i < view.size() && at < chosenOut.length; i++) {
            int n = shown[i];
            if (n <= 0) {
                continue;
            }
            int[] slots = nearestSlots(worldSeed, view.chunkX(i), view.chunkZ(i), playerX, playerZ, n);
            for (int k = 0; k < slots.length && at < chosenOut.length; k++) {
                chosenOut[at++] = slots[k];
            }
        }
        while (at < chosenOut.length) {
            chosenOut[at++] = -1;
        }
        return shown;
    }

    /**
     * Which of a chunk's places lie nearest a point, nearest first.
     *
     * <p>The set of places does not depend on the answer, because the plan is the ground's. Only the
     * <em>order</em> they are taken in does: nearest first, so the people a player meets are the ones
     * at the front of the crowd and the crowd behind them fills in the order the player would see it.
     * Walk towards a chunk and watch it come into view and you see the front of it; if the order
     * depended on which way you were looking you would see a different figure in the same place.
     */
    public static int[] nearestSlots(long worldSeed, int chunkX, int chunkZ, double x, double z,
                                     int count) {
        java.util.List<CrowdDensity.Figure> plan = CrowdDensity.planSquare(
                CrowdDensity.placeSeed(worldSeed, chunkX, chunkZ), CHUNK_CAPACITY,
                CrowdDensity.PLAN_HALF);
        int want = Math.max(0, Math.min(count, plan.size()));
        int[] out = new int[want];
        if (want == 0) {
            return out;
        }
        double px = x - (chunkX * CHUNK_SIDE + CHUNK_SIDE / 2.0d);
        double pz = z - (chunkZ * CHUNK_SIDE + CHUNK_SIDE / 2.0d);
        Integer[] order = new Integer[plan.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> Double.compare(
                Math.hypot(plan.get(a).dx() - px, plan.get(a).dz() - pz),
                Math.hypot(plan.get(b).dx() - px, plan.get(b).dz() - pz)));
        for (int i = 0; i < want; i++) {
            out[i] = order[i];
        }
        return out;
    }

    /** The same slots, nearest a point first, so a fill grows towards the watcher and not away. */
    public static int[] nearestFirst(java.util.List<CrowdDensity.Figure> plan, int[] slots,
                                     double x, double z) {
        if (slots.length < 2) {
            return slots;
        }
        Integer[] order = new Integer[slots.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> Double.compare(
                Math.hypot(plan.get(slots[a]).dx() - x, plan.get(slots[a]).dz() - z),
                Math.hypot(plan.get(slots[b]).dx() - x, plan.get(slots[b]).dz() - z)));
        int[] out = new int[slots.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = slots[order[i]];
        }
        return out;
    }

    /**
     * Every drawn figure's place, packed as {@code chunkIndex << 32 | plan slot}, nearest first.
     *
     * <p>This is the order a player meets the crowd in as it comes into view, and it is an answer
     * about the ground only: the same place is always the same person, so the figure standing in the
     * nearest place of a chunk is a fixed answer and not a lottery of whichever cycle ran first.
     */
    public static long[] frontSlots(long worldSeed, View view, int[] shown, double x, double z) {
        int total = 0;
        for (int n : shown) {
            total += Math.max(0, n);
        }
        long[] out = new long[total];
        int at = 0;
        for (int i = 0; i < view.size(); i++) {
            int n = shown[i];
            if (n <= 0) {
                continue;
            }
            int[] slots = nearestSlots(worldSeed, view.chunkX(i), view.chunkZ(i), x, z, n);
            for (int k = 0; k < slots.length; k++) {
                out[at++] = ((long) i << 32) | (slots[k] & 0xFFFFFFFFL);
            }
        }
        java.util.List<Long> boxed = new java.util.ArrayList<>(total);
        for (long packed : out) {
            boxed.add(packed);
        }
        boxed.sort((a, b) -> Double.compare(slotDistance(worldSeed, view, a, x, z),
                slotDistance(worldSeed, view, b, x, z)));
        for (int i = 0; i < out.length; i++) {
            out[i] = boxed.get(i);
        }
        return out;
    }

    /** How far a drawn place stands from a point. */
    public static double slotDistance(long worldSeed, View view, long packed, double x, double z) {
        int i = (int) (packed >>> 32);
        int slot = (int) packed;
        java.util.List<CrowdDensity.Figure> plan = CrowdDensity.planSquare(
                CrowdDensity.placeSeed(worldSeed, view.chunkX(i), view.chunkZ(i)), CHUNK_CAPACITY,
                CrowdDensity.PLAN_HALF);
        if (slot < 0 || slot >= plan.size()) {
            return Double.MAX_VALUE;
        }
        CrowdDensity.Figure f = plan.get(slot);
        double px = view.chunkX(i) * CHUNK_SIDE + CHUNK_SIDE / 2.0d + f.dx();
        double pz = view.chunkZ(i) * CHUNK_SIDE + CHUNK_SIDE / 2.0d + f.dz();
        return Math.hypot(px - x, pz - z);
    }

    public static int[] viewTowards(View view, int budget, double playerX, double playerZ,
                                    double facingDegrees, double bubbleBlocks) {
        int n = view.size();
        int[] shown = new int[n];
        if (n == 0) {
            return shown;
        }
        int want = (int) Math.max(0L, Math.min(budget, view.total()));
        if (want <= 0) {
            return shown;
        }
        double yaw = Math.toRadians(facingDegrees);
        double facingX = -Math.sin(yaw);
        double facingZ = Math.cos(yaw);
        double cosHalf = Math.cos(Math.toRadians(DRAWN_WEDGE_DEGREES / 2.0d));
        double[] weight = new double[n];
        double weightSum = 0.0d;
        for (int i = 0; i < n; i++) {
            weight[i] = wedgeFraction(view.chunkX(i), view.chunkZ(i), playerX, playerZ,
                    facingX, facingZ, cosHalf, bubbleBlocks);
            weightSum += weight[i];
        }
        if (weightSum <= 0.0d) {
            return sampledView(view.planned(), budget);
        }
        // Water-fill: everyone in front takes a share in proportion to how much of them is in front,
        // and a chunk that would go over the ceiling spills to the others rather than being trimmed.
        double[] got = new double[n];
        boolean[] full = new boolean[n];
        int remaining = want;
        for (int round = 0; round < n + 2 && remaining > 0; round++) {
            double active = 0.0d;
            for (int i = 0; i < n; i++) {
                if (!full[i]) {
                    active += weight[i];
                }
            }
            if (active <= 0.0d) {
                break;
            }
            boolean anyCapped = false;
            for (int i = 0; i < n; i++) {
                if (!full[i] && remaining * weight[i] / active > CHUNK_CAPACITY - got[i]) {
                    anyCapped = true;
                    break;
                }
            }
            if (!anyCapped) {
                for (int i = 0; i < n; i++) {
                    if (!full[i]) {
                        got[i] += remaining * weight[i] / active;
                    }
                }
                remaining = 0;
                break;
            }
            for (int i = 0; i < n; i++) {
                if (full[i]) {
                    continue;
                }
                if (remaining * weight[i] / active > CHUNK_CAPACITY - got[i]) {
                    remaining -= (int) Math.round(CHUNK_CAPACITY - got[i]);
                    got[i] = CHUNK_CAPACITY;
                    full[i] = true;
                }
            }
            if (remaining < 0) {
                remaining = 0;
            }
        }
        // Enough people to fill the whole view: the rest stand behind the player rather than vanish.
        if (remaining > 0) {
            Integer[] byDistance = orderByDistance(view, playerX, playerZ);
            for (int k = 0; k < n && remaining > 0; k++) {
                int i = byDistance[k];
                if (full[i]) {
                    continue;
                }
                int room = CHUNK_CAPACITY - (int) Math.ceil(got[i]);
                int give = Math.min(Math.max(room, 0), remaining);
                got[i] += give;
                remaining -= give;
            }
        }
        // Integers, and the total is the budget exactly: largest remainder, fixed order.
        int assigned = 0;
        double[] remainders = new double[n];
        for (int i = 0; i < n; i++) {
            shown[i] = (int) Math.min((long) Math.floor(got[i] + 1.0e-9d), CHUNK_CAPACITY);
            remainders[i] = got[i] - shown[i];
            assigned += shown[i];
        }
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> Double.compare(remainders[b], remainders[a]));
        for (int i = 0; assigned < want && i < order.length; i++) {
            if (shown[order[i]] < CHUNK_CAPACITY) {
                shown[order[i]]++;
                assigned++;
            }
        }
        return shown;
    }

    /** Chunks nearest the player first, for the case where the whole view wants standing up. */
    private static Integer[] orderByDistance(View view, double playerX, double playerZ) {
        Integer[] order = new Integer[view.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> Double.compare(
                distanceSq(view, a, playerX, playerZ), distanceSq(view, b, playerX, playerZ)));
        return order;
    }

    private static double distanceSq(View view, int i, double playerX, double playerZ) {
        double dx = view.chunkX(i) * (double) CHUNK_SIDE + CHUNK_SIDE / 2.0d - playerX;
        double dz = view.chunkZ(i) * (double) CHUNK_SIDE + CHUNK_SIDE / 2.0d - playerZ;
        return dx * dx + dz * dz;
    }

    /**
     * How much of a chunk is in front of the player: the share of sixteen points of its ground that
     * lie outside the no-spawn bubble and inside the wedge the player is looking down.
     */
    private static double wedgeFraction(int chunkX, int chunkZ, double playerX, double playerZ,
                                        double facingX, double facingZ, double cosHalf,
                                        double bubbleBlocks) {
        double baseX = chunkX * (double) CHUNK_SIDE;
        double baseZ = chunkZ * (double) CHUNK_SIDE;
        int inside = 0;
        for (double ox : WEDGE_SAMPLES) {
            for (double oz : WEDGE_SAMPLES) {
                double dx = baseX + ox - playerX;
                double dz = baseZ + oz - playerZ;
                double dist = Math.hypot(dx, dz);
                if (dist <= bubbleBlocks || dist <= 1.0e-6d) {
                    continue;
                }
                if ((dx * facingX + dz * facingZ) / dist < cosHalf) {
                    continue;
                }
                inside++;
            }
        }
        return inside / 16.0d;
    }

    /**
     * What is actually stood up when the view plans more people than the client will draw.
     *
     * <p>Two things are true at once: the model says five hundred people are in this view, and the
     * frame can afford four hundred. The answer is not to draw the nearest four hundred — that makes
     * a crowd whose far half is missing — and not to draw a uniform sample either. Each chunk keeps
     * its <em>relative</em> density: a chunk planned at twice another chunk's count shows twice as
     * many, and the total is the budget exactly. The crowd thins everywhere at once, which is what
     * thinning should look like.
     *
     * <p>Largest-remainder, so the counts stay integers and the total is exact.
     *
     * @param plannedPerChunk the true figures per chunk, from {@link #figuresInChunk}
     * @param budget          how many figures the client will draw in this view
     * @return what to stand up per chunk, indexed as the input
     */
    public static int[] sampledView(int[] plannedPerChunk, int budget) {
        int[] shown = new int[plannedPerChunk.length];
        long planned = 0L;
        for (int count : plannedPerChunk) {
            planned += Math.max(0, count);
        }
        int want = (int) Math.max(0L, Math.min(budget, planned));
        if (want == 0) {
            return shown;
        }
        if (want >= planned) {
            for (int i = 0; i < shown.length; i++) {
                shown[i] = Math.max(0, plannedPerChunk[i]);
            }
            return shown;
        }
        double scale = want / (double) planned;
        int assigned = 0;
        double[] remainders = new double[shown.length];
        for (int i = 0; i < shown.length; i++) {
            double exact = Math.max(0, plannedPerChunk[i]) * scale;
            shown[i] = (int) Math.floor(exact);
            remainders[i] = exact - shown[i];
            assigned += shown[i];
        }
        // hand out what rounding left over, to the chunks that lost the most, in a fixed order
        Integer[] order = new Integer[shown.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> Double.compare(remainders[b], remainders[a]));
        for (int i = 0; assigned < want && i < order.length; i++) {
            shown[order[i]]++;
            assigned++;
        }
        return shown;
    }

    /**
     * One player's view of the place: the chunks in reach, and how many people each of them carries.
     *
     * <p>Chunks are in the arrays in a fixed order, and the arrays are the plan — the spawner stands
     * the crowd up from them and never recomputes the shape itself.
     */
    public record View(int[] chunksX, int[] chunksZ, int[] counts, long total) {
        public int size() {
            return chunksX.length;
        }

        /** The chunk the i-th entry sits in, and what it carries. */
        public int chunkX(int i) {
            return chunksX[i];
        }

        public int chunkZ(int i) {
            return chunksZ[i];
        }

        public int planned(int i) {
            return counts[i];
        }

        /** Every chunk's count at once, for the thinning step. */
        public int[] planned() {
            return counts;
        }
    }

    /**
     * The place's people, divided over the chunks in one player's view.
     *
     * <p>Distance is measured from the place's heart — the market, the fort, the point the place is
     * named for — so the division is a property of the place and not of where the player happens to
     * be standing. Walk from the heart to the edge and the same chunks keep the same shares; walk out
     * of the place entirely and the crowd behind you is simply not in your view any more.
     *
     * @param worldSeed       the world's seed: the chunks' coins are tossed with it, so the same
     *                        world always shows the same crowd
     * @param population      the resolver's number for this place this year
     * @param radiusKm        the place's extent, from the same data the number came from
     * @param concentration   how much of the population gathers at the heart; 0 is the equal division
     * @param heartX,heartZ   the place's heart, in blocks
     * @param playerX,playerZ where the player is, in blocks
     * @param interestBlocks  how far from the player the crowd is stood up at all
     */
    public static View viewOf(long worldSeed, long population, double radiusKm, double concentration,
                              int heartX, int heartZ, double playerX, double playerZ,
                              double interestBlocks) {
        if (population <= 0L || interestBlocks <= 0.0d) {
            return new View(new int[0], new int[0], new int[0], 0L);
        }
        double mass = totalWeight(radiusKm, concentration);
        int reach = (int) Math.ceil(interestBlocks / CHUNK_SIDE) + 1;
        int playerChunkX = (int) Math.floor(playerX / CHUNK_SIDE);
        int playerChunkZ = (int) Math.floor(playerZ / CHUNK_SIDE);
        double interestSq = interestBlocks * interestBlocks;
        java.util.List<int[]> kept = new java.util.ArrayList<>();
        long total = 0L;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                int cx = playerChunkX + dx;
                int cz = playerChunkZ + dz;
                if (dx * dx + dz * dz > (reach - 1) * (reach - 1)) {
                    continue; // the disc of chunks the interest radius covers, in whole chunks
                }
                double centreX = cx * CHUNK_SIDE + CHUNK_SIDE / 2.0d;
                double centreZ = cz * CHUNK_SIDE + CHUNK_SIDE / 2.0d;
                double fromPlayerX = centreX - playerX;
                double fromPlayerZ = centreZ - playerZ;
                if (fromPlayerX * fromPlayerX + fromPlayerZ * fromPlayerZ > interestSq) {
                    continue;
                }
                double distance = Math.hypot(centreX - heartX, centreZ - heartZ);
                int figures = 0;
                if (distance <= radiusKm * 1000.0d) {
                    // the average share, and then the chunk's own coin for the fraction — so a
                    // countryside chunk holding a third of a person shows a person a third of the
                    // time, and the fields are not rounded down to nobody
                    figures = figuresInChunk(population, radiusKm, distance, concentration,
                            chunkHash(worldSeed, cx, cz));
                }
                if (figures <= 0) {
                    continue;
                }
                kept.add(new int[] {cx, cz, figures});
                total += figures;
            }
        }
        int[] xs = new int[kept.size()];
        int[] zs = new int[kept.size()];
        int[] counts = new int[kept.size()];
        for (int i = 0; i < kept.size(); i++) {
            xs[i] = kept.get(i)[0];
            zs[i] = kept.get(i)[1];
            counts[i] = kept.get(i)[2];
        }
        return new View(xs, zs, counts, total);
    }

    /**
     * How much ground a drawn crowd covers, in blocks of radius, when it is standing at a real
     * crowd's density.
     *
     * <p>This is the number that decides whether the crowd reads as a crowd: not how many figures
     * exist, but how tightly they stand when you look at them. A hundred and fifty figures three
     * metres apart are a street; five hundred at half a metre are the same street at once.
     */
    public static double footprintRadiusBlocks(int figures, double densityPerM2) {
        if (figures <= 0 || densityPerM2 <= 0.0d) {
            return 0.0d;
        }
        return Math.sqrt(figures / (densityPerM2 * Math.PI));
    }

    /**
     * Everything a client needs to draw one view's crowd — and nothing else.
     *
     * <p>The whole point of the division is that a crowd of thousands costs arithmetic, not bodies.
     * So what crosses the wire is not the crowd: it is the <em>recipe</em> for it. The client is
     * given the world seed, the year, the place's population and extent, the concentration, the
     * heart, and one line per chunk in the view — where the chunk is and how many people stand in
     * it. From that it builds the same figures the server would have, at the same places, because
     * every step of the placement is a pure function of the seed.
     *
     * <p>Measured, this is a couple of hundred bytes for a sixty-four block view. Four hundred
     * spawned entities are many kilobytes of packet, sent again whenever a player walks back to
     * them.
     */
    public record Manifest(long worldSeed, int year, long population, double radiusKm,
                           double concentration, int heartX, int heartZ,
                           int[] chunksX, int[] chunksZ, int[] counts) {

        /** The chunk a block coordinate falls in — floored, so negative coordinates are chunks too. */
        private static int chunkOf(int blockCoordinate) {
            return Math.floorDiv(blockCoordinate, CHUNK_SIDE);
        }

        /** Encodes the manifest: 21 bytes of header, then three bytes a chunk. */
        public byte[] encode() {
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(sizeBytes());
            buffer.put((byte) 1);                              // version
            buffer.putLong(worldSeed);
            buffer.putShort((short) year);
            buffer.putInt((int) Math.min(Integer.MAX_VALUE, Math.max(0L, population)));
            buffer.putShort((short) Math.round(radiusKm * 100.0d));
            buffer.putShort((short) Math.round(concentration * 10.0d));
            buffer.putInt(heartX);
            buffer.putInt(heartZ);
            buffer.putInt(chunksX.length);
            for (int i = 0; i < chunksX.length; i++) {
                buffer.put(relative(chunksX[i] - chunkOf(heartX)));
                buffer.put(relative(chunksZ[i] - chunkOf(heartZ)));
                buffer.put((byte) Math.min(Byte.MAX_VALUE, counts[i]));
            }
            return buffer.array();
        }

        /** A chunk's distance from the heart's chunk, clamped to a byte: ±127 chunks is ±2 km. */
        private static byte relative(int chunks) {
            return (byte) Math.max(Byte.MIN_VALUE, Math.min(Byte.MAX_VALUE, chunks));
        }

        /**
         * The inverse of {@link #encode()} — so the recipe can be checked by round trip here rather
         * than only on a machine that can run the game.
         *
         * @throws IllegalArgumentException if the bytes are not a manifest
         */
        public static Manifest decode(byte[] bytes) {
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
            if (buffer.remaining() < 27 || buffer.get() != 1) {
                throw new IllegalArgumentException("not a crowd manifest");
            }
            long seed = buffer.getLong();
            int year = buffer.getShort();
            long population = buffer.getInt();
            double radiusKm = buffer.getShort() / 100.0d;
            double concentration = buffer.getShort() / 10.0d;
            int heartX = buffer.getInt();
            int heartZ = buffer.getInt();
            int count = buffer.getInt();
            if (count < 0 || buffer.remaining() < 3 * count) {
                throw new IllegalArgumentException("crowd manifest is short: " + count + " chunks");
            }
            int[] xs = new int[count];
            int[] zs = new int[count];
            int[] counts = new int[count];
            for (int i = 0; i < count; i++) {
                xs[i] = chunkOf(heartX) + buffer.get();
                zs[i] = chunkOf(heartZ) + buffer.get();
                counts[i] = buffer.get();
            }
            return new Manifest(seed, year, population, radiusKm, concentration, heartX, heartZ,
                    xs, zs, counts);
        }

        /** The exact size of {@link #encode()}, so the cost can be stated without sending anything. */
        public int sizeBytes() {
            return 1 + 8 + 2 + 4 + 2 + 2 + 4 + 4 + 4 + 3 * chunksX.length;
        }

        public int size() {
            return chunksX.length;
        }

        /** The chunk the i-th entry sits in, and how many people stand in it. */
        public int chunkX(int i) {
            return chunksX[i];
        }

        public int chunkZ(int i) {
            return chunksZ[i];
        }

        public int planned(int i) {
            return counts[i];
        }

        /** Every chunk's count at once, for a client that wants the array. */
        public int[] planned() {
            return counts;
        }

        /** The crowd the recipe describes: the figures in the view, the budget already applied. */
        public int total() {
            int sum = 0;
            for (int count : counts) {
                sum += Math.max(0, count);
            }
            return sum;
        }
    }

    /**
     * The recipe for one view's crowd (see {@link Manifest}). Takes the same arguments as
     * {@link #viewOf} and thins the plan to the client's budget, so the manifest describes what is
     * actually drawn rather than what the place holds.
     */
    public static Manifest manifestOf(long worldSeed, long population, double radiusKm,
                                      double concentration, int heartX, int heartZ,
                                      double playerX, double playerZ, double interestBlocks,
                                      int figuresBudget, int year) {
        View view = viewOf(worldSeed, population, radiusKm, concentration, heartX, heartZ,
                playerX, playerZ, interestBlocks);
        int[] shown = sampledView(view.planned(), figuresBudget);
        int kept = 0;
        for (int count : shown) {
            if (count > 0) {
                kept++;
            }
        }
        int[] xs = new int[kept];
        int[] zs = new int[kept];
        int[] counts = new int[kept];
        int at = 0;
        for (int i = 0; i < shown.length; i++) {
            if (shown[i] > 0) {
                xs[at] = view.chunkX(i);
                zs[at] = view.chunkZ(i);
                counts[at] = shown[i];
                at++;
            }
        }
        return new Manifest(worldSeed, year, population, radiusKm, concentration, heartX, heartZ,
                xs, zs, counts);
    }

    /** Chunks within a view radius: what the spawner has to walk when standing somewhere. */
    public static int chunksInView(double viewRadiusBlocks) {
        int reach = (int) Math.ceil(viewRadiusBlocks / CHUNK_SIDE) + 1;
        double viewSq = viewRadiusBlocks * viewRadiusBlocks;
        int count = 0;
        for (int cx = -reach; cx <= reach; cx++) {
            for (int cz = -reach; cz <= reach; cz++) {
                double dx = cx * (double) CHUNK_SIDE;
                double dz = cz * (double) CHUNK_SIDE;
                if (dx * dx + dz * dz <= viewSq) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * How much of the place's population one view holds — the share of a city that is ever on
     * screen. The smaller this is, the more of the place the player is not being shown, and it is
     * why a crowd must be spread over the area rather than gathered into one square.
     */
    public static double shareOfPlace(double viewRadiusBlocks, double radiusKm) {
        double place = areaM2(radiusKm);
        return place <= 0.0d ? 0.0d : Math.PI * viewRadiusBlocks * viewRadiusBlocks / place;
    }

    // ==================== the built core ====================

    /**
     * The built-area densities the three ranks show, in people a square kilometre.
     *
     * <p>A place's population is not spread over its administrative extent. A village of a thousand
     * people does not stand at one person every eleven hectares of its revenue land; it stands in an
     * abadi a few hundred metres across. These are the figures the game puts a rank's built area at:
     * 167 m² a person in a village, 83 in a town, 25 in a city — dense lanes, a busy small town, a
     * city core, all three of them real numbers for real places.
     */
    public static final double DENSITY_VILLAGE_PER_KM2 = 6_000.0d;
    public static final double DENSITY_TOWN_PER_KM2 = 12_000.0d;
    public static final double DENSITY_CITY_PER_KM2 = 30_000.0d;

    /** The density a rank's built area shows. */
    public static double rankDensityPerKm2(SettlementRank rank) {
        if (rank == SettlementRank.CITY) {
            return DENSITY_CITY_PER_KM2;
        }
        return rank == SettlementRank.TOWN ? DENSITY_TOWN_PER_KM2 : DENSITY_VILLAGE_PER_KM2;
    }

    /**
     * The density this place's people actually stand at: the rank's built density, but never less
     * than the place's own average over its extent.
     *
     * <p>The second half matters for the biggest places. Hyderabad's seven and a half million people
     * sit inside an eight kilometre radius, which is 38,000 people a square kilometre — denser than
     * the city figure. A place that is genuinely that dense everywhere shows its own number; a place
     * with a large administrative extent and a small town inside it shows the town's.
     */
    public static double builtDensityPerKm2(SettlementRank rank, long population, double radiusKm) {
        double areaKm2 = areaKm2(radiusKm);
        double average = areaKm2 <= 0.0d ? 0.0d : population / areaKm2;
        return Math.max(rankDensityPerKm2(rank), average);
    }

    /**
     * The densest a crowd can stand before a chunk's capacity starts eating the population.
     *
     * <p>A chunk holds {@value #CHUNK_CAPACITY} figures over its 256 m² — one person every 3.3 m².
     * Past that the cap is not a budget any more, it is a loss: the measured sum over a city's built
     * core fell 21% short of its population when the density was pushed there. So the density is held
     * at this figure, and the knob stops buying anything above it.
     */
    public static final double MAX_DENSITY_PER_KM2 = CHUNK_CAPACITY * M2_PER_KM2 / CHUNK_AREA_M2;

    /** That ceiling, said the way a person reads it: the least ground one figure can stand on. */
    public static double densestSquareMetresPerPerson() {
        return squareMetresPerPerson(MAX_DENSITY_PER_KM2);
    }

    /** The density, held at the most a chunk can carry — above it the cap would eat the population. */
    public static double cappedDensityPerKm2(double densityPerKm2) {
        return Math.min(densityPerKm2, MAX_DENSITY_PER_KM2);
    }

    /** People a square kilometre at this density: the inverse, for tables. */
    public static double squareMetresPerPerson(double densityPerKm2) {
        return densityPerKm2 <= 0.0d ? 0.0d : M2_PER_KM2 / densityPerKm2;
    }

    /**
     * How far the built area reaches from its centre, when it holds this population at this density.
     *
     * <p>Never further than the place itself: a place whose whole extent is denser than the rank's
     * figure is built up all the way to its edge.
     */
    public static double builtRadiusKm(long population, double radiusKm, double densityPerKm2) {
        if (population <= 0L || densityPerKm2 <= 0.0d) {
            return 0.0d;
        }
        return Math.min(radiusKm, Math.sqrt(population / densityPerKm2 / Math.PI));
    }

    /**
     * The crowd as a player actually meets it: the place's built area, anchored where the player is.
     *
     * <h3>Why this exists next to {@link #viewOf}</h3>
     * {@code viewOf} divides the population over the whole extent and gathers it at the place's
     * heart. Measured, that is honest and it is invisible: a player standing anywhere but the heart
     * sees the district's average, which for Secunderabad is one person every 457 m² and 23 figures
     * in a sixty-four block view. People do not live at district averages — they live in the built
     * part, at thousands to tens of thousands a square kilometre, and the fields are somewhere else.
     *
     * <p>So the crowd here is the same population at the place's own built density, and the built
     * area is anchored on the player's chunk. A player inside the place is standing in its built
     * part; a player outside it is not, and the crowd falls away behind them. The population is
     * conserved: a chunk's people are the density times the chunk's ground, the whole place's built
     * area holds exactly the population, and what one player can see at once is a small window of it
     * — 65,536 m² of a 113 km² place.
     *
     * <p>The player's own 24 blocks are kept clear per figure, where the figure is actually stood up
     * (see the spawner's {@code figureSpot}), and not by excluding whole chunks here: a chunk is 16
     * blocks across, so excluding the chunks whose centre falls inside a 24-block circle leaves a
     * 48-block square hole around the player — a visible gap in the middle of the crowd, and one that
     * vanished nine chunks' worth of people the population had given them.
     *
     * @param densityPerKm2   the built density, from {@link #builtDensityPerKm2}
     * @param anchorX,anchorZ the centre of the built area, in blocks — the player's chunk centre
     */
    public static View viewOfCore(long worldSeed, long population, double radiusKm,
                                  double densityPerKm2, int anchorX, int anchorZ,
                                  double playerX, double playerZ, double interestBlocks) {
        double density = cappedDensityPerKm2(densityPerKm2);
        if (population <= 0L || interestBlocks <= 0.0d || density <= 0.0d) {
            return new View(new int[0], new int[0], new int[0], 0L);
        }
        double builtBlocks = builtRadiusKm(population, radiusKm, density) * 1000.0d;
        double perChunk = density * CHUNK_AREA_M2 / M2_PER_KM2;
        int reach = (int) Math.ceil(interestBlocks / CHUNK_SIDE) + 1;
        int playerChunkX = (int) Math.floor(playerX / CHUNK_SIDE);
        int playerChunkZ = (int) Math.floor(playerZ / CHUNK_SIDE);
        double interestSq = interestBlocks * interestBlocks;
        java.util.List<int[]> kept = new java.util.ArrayList<>();
        long total = 0L;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                int cx = playerChunkX + dx;
                int cz = playerChunkZ + dz;
                if (dx * dx + dz * dz > (reach - 1) * (reach - 1)) {
                    continue; // the disc of chunks the interest radius covers, in whole chunks
                }
                double centreX = cx * CHUNK_SIDE + CHUNK_SIDE / 2.0d;
                double centreZ = cz * CHUNK_SIDE + CHUNK_SIDE / 2.0d;
                // Any part of the chunk inside the interest radius counts, not only its centre.
                // Testing the centre leaves the crowd with a staircase edge — 72 blocks away along
                // the axes but 56 at the diagonals — which reads as gaps at the rim of the crowd;
                // the nearest point of the chunk gives an edge the shape of the radius itself.
                double nx = Math.max(Math.abs(playerX - centreX) - CHUNK_SIDE / 2.0d, 0.0d);
                double nz = Math.max(Math.abs(playerZ - centreZ) - CHUNK_SIDE / 2.0d, 0.0d);
                if (nx * nx + nz * nz > interestSq) {
                    continue;
                }
                if (Math.hypot(centreX - anchorX, centreZ - anchorZ) > builtBlocks) {
                    continue; // outside the built area: fields, and nobody stands in them
                }
                int figures = figuresForPeople(perChunk, chunkHash(worldSeed, cx, cz));
                if (figures <= 0) {
                    continue;
                }
                kept.add(new int[] {cx, cz, figures});
                total += figures;
            }
        }
        int[] xs = new int[kept.size()];
        int[] zs = new int[kept.size()];
        int[] counts = new int[kept.size()];
        for (int i = 0; i < kept.size(); i++) {
            xs[i] = kept.get(i)[0];
            zs[i] = kept.get(i)[1];
            counts[i] = kept.get(i)[2];
        }
        return new View(xs, zs, counts, total);
    }

    /**
     * The people the whole built core holds, chunk by chunk, through the same lottery the crowd uses.
     *
     * <p>A conservation check with teeth: the placement hands out whole people first and lets each
     * chunk's own hash decide the fractions, so this sum is the population to within a person — and
     * it is the number no crowd size, density or anchor can change. If a change to this class ever
     * makes a place hold more people than the resolver gave it, this is what catches it.
     */
    public static long corePopulation(long worldSeed, long population, double radiusKm,
                                      double densityPerKm2) {
        double density = cappedDensityPerKm2(densityPerKm2);
        if (population <= 0L || density <= 0.0d) {
            return 0L;
        }
        double builtBlocks = builtRadiusKm(population, radiusKm, density) * 1000.0d;
        double perChunk = density * CHUNK_AREA_M2 / M2_PER_KM2;
        int reach = (int) Math.ceil(builtBlocks / CHUNK_SIDE) + 1;
        long total = 0L;
        for (int cx = -reach; cx <= reach; cx++) {
            for (int cz = -reach; cz <= reach; cz++) {
                double centreX = cx * CHUNK_SIDE + CHUNK_SIDE / 2.0d;
                double centreZ = cz * CHUNK_SIDE + CHUNK_SIDE / 2.0d;
                if (Math.hypot(centreX, centreZ) > builtBlocks) {
                    continue;
                }
                total += figuresForPeople(perChunk, chunkHash(worldSeed, cx, cz));
            }
        }
        return total;
    }

    /**
     * The recipe for one view of the built-core crowd. Same as {@link #manifestOf}, and its
     * {@code concentration} argument is what the manifest carries in that field — the per-chunk
     * counts, not that number, are what a client builds figures from.
     *
     * <p>The manifest's two block coordinates are the crowd's <em>anchor</em> — the player's chunk
     * centre — rather than the place's heart, because that is what the crowd is built around.
     */
    /**
     * The manifest sent to the client: the same crowd {@link #viewTowards} stands up, chunk by chunk,
     * spent where the player is looking. It carries the counts and the anchor, not the positions — the
     * client lays the figures out from the same plan.
     */
    public static Manifest manifestOfCore(long worldSeed, long population, double radiusKm,
                                          double densityPerKm2, int anchorX, int anchorZ,
                                          double playerX, double playerZ, double interestBlocks,
                                          double concentration, int figuresBudget, int year,
                                          double facingDegrees, double bubbleBlocks) {
        View view = viewOfCore(worldSeed, population, radiusKm, densityPerKm2, anchorX, anchorZ,
                playerX, playerZ, interestBlocks);
        int[] shown = viewTowards(view, figuresBudget, playerX, playerZ, facingDegrees, bubbleBlocks);
        int kept = 0;
        for (int count : shown) {
            if (count > 0) {
                kept++;
            }
        }
        int[] xs = new int[kept];
        int[] zs = new int[kept];
        int[] counts = new int[kept];
        int at = 0;
        for (int i = 0; i < shown.length; i++) {
            if (shown[i] > 0) {
                xs[at] = view.chunkX(i);
                zs[at] = view.chunkZ(i);
                counts[at] = shown[i];
                at++;
            }
        }
        return new Manifest(worldSeed, year, population, radiusKm, concentration, anchorX, anchorZ,
                xs, zs, counts);
    }
}

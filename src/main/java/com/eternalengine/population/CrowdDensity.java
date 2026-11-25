package com.eternalengine.population;

import com.eternalengine.civilization.SettlementRank;

/**
 * How many people a place <em>shows</em>, as opposed to how many it can afford to think.
 *
 * <h3>Why this exists separately from {@link SettlementDensity}</h3>
 * {@code SettlementDensity} answers "how many live people can stand here", and the answer is capped
 * by what a server can tick: ten, twenty, forty. That is a group. It is not a crowd, and calling it
 * one was a mistake — forty people in a twenty-eight block district is 0.016 people per square
 * metre, where a real market square runs at 0.5 to 1.
 *
 * <p>A crowd is therefore a <em>drawing</em> problem, not an entity problem. The people who think
 * are the ones you can talk to; the people who only stand there are drawn, cost nothing per tick,
 * and are what the eye actually reads as a crowd. This class decides how many of those there are,
 * how far the crowd spreads, and where in it the living few belong.
 *
 * <h3>The two curves</h3>
 * <table>
 *   <tr><th>place</th><th>population</th><th>living (ticked)</th><th>drawn (free)</th></tr>
 *   <tr><td>Akhanpally Thanda, 1948</td><td>348</td><td>5</td><td>25</td></tr>
 *   <tr><td>Akanpalle, 2011</td><td>1,047</td><td>6</td><td>32</td></tr>
 *   <tr><td>Maheshwaram mandal</td><td>8,795</td><td>15</td><td>94</td></tr>
 *   <tr><td>Secunderabad, 1948</td><td>142,174</td><td>35</td><td>377</td></tr>
 *   <tr><td>Hyderabad, 1948</td><td>980,600</td><td>40</td><td>400</td></tr>
 * </table>
 *
 * <p>The drawn count is the square root of the population, which is not a magic number: for a given
 * density the area of a settlement grows with its population, and the square root is the side of
 * that area. A village of a thousand shows thirty figures, a city of a hundred and forty thousand
 * shows nearly four hundred, and both are the same crowd seen at their own scale.
 *
 * <h3>Constant density, growing extent</h3>
 * The drawn crowd is always the same visual density — about {@value #TARGET_DENSITY} people per
 * square metre, which is a loose but real crowd — so a bigger settlement is a <em>wider</em> crowd
 * rather than a tighter one. A village is a huddle; a city is a square you cannot see the far side
 * of. Both are capped by the district radius, so the crowd never spills out of the place it belongs
 * to.
 */
public final class CrowdDensity {

    private CrowdDensity() {}

    /** Below this the eye counts individuals: a handful of people is a group, not a crowd. */
    public static final int MIN_FIGURES = 25;

    /**
     * The most figures drawn at once. Four hundred is a crowd you cannot count, which is the whole
     * point — and being drawn rather than simulated, it costs no AI, no physics and no ticks.
     */
    public static final int MAX_FIGURES = 400;

    /**
     * Figures per square metre. A real crowd standing at a market runs 0.5–1.0; 0.3 reads as a
     * busy square while leaving room for the living people to move between the drawn ones.
     */
    public static final double TARGET_DENSITY = 0.3d;

    /** A crowd never becomes a single dot: the drawn disc keeps at least this radius. */
    public static final double MIN_RADIUS = 4.0d;

    /**
     * How many figures are drawn for a place: the square root of its population, held between the
     * counting threshold and the drawing ceiling.
     */
    public static int figuresFor(long population) {
        if (population <= 0L) {
            return MIN_FIGURES;
        }
        double root = Math.sqrt((double) population);
        if (root >= MAX_FIGURES) {
            return MAX_FIGURES; // clamped before the cast: rounding a huge double to int overflows
        }
        return Math.max(MIN_FIGURES, (int) Math.round(root));
    }

    /** Drawn figures for a place, with the rank's own preference for a minimum showing. */
    public static int figuresFor(long population, SettlementRank rank) {
        int figures = figuresFor(population);
        SettlementRank r = rank == null ? SettlementRank.VILLAGE : rank;
        int floor = switch (r) {
            case CITY -> 120;
            case TOWN -> 60;
            default -> MIN_FIGURES;
        };
        return Math.min(MAX_FIGURES, Math.max(floor, figures));
    }

    /**
     * How far the drawn crowd spreads, for a given number of figures at the target density — never
     * wider than the district it belongs to.
     */
    public static double radiusFor(int figures, double districtRadius) {
        double area = Math.max(MIN_FIGURES, figures) / TARGET_DENSITY;
        double radius = Math.sqrt(area / Math.PI);
        return Math.max(MIN_RADIUS, Math.min(districtRadius, radius));
    }

    /** The density a crowd of this size actually has when it is spread over this radius. */
    public static double densityOf(int figures, double radius) {
        double area = Math.PI * radius * radius;
        return area <= 0.0d ? 0.0d : figures / area;
    }

    /** A drawn figure: a fixed place in the crowd. Nothing ticks here. */
    public record Figure(double dx, double dz, double distance) {}

    /**
     * Where the crowd stands: a jittered grid inside the disc, so the figures look scattered
     * without clumping or leaving holes.
     *
     * <p>A pure area-uniform draw — which is right for spawning two or three people — looks wrong
     * at four hundred: random points cluster, and a clustered crowd has gaps you can see through.
     * One figure per cell, nudged off-centre, gives a crowd with no holes and no pairs.
     *
     * @param seed    world seed, so the same place always looks the same
     * @param figures how many to place
     * @param radius  the disc to fill
     */
    public static java.util.List<Figure> plan(long seed, int figures, double radius) {
        java.util.List<Figure> out = new java.util.ArrayList<>();
        int want = Math.max(0, Math.min(MAX_FIGURES, figures));
        if (want == 0) {
            return out;
        }
        double area = Math.PI * radius * radius;
        java.util.List<double[]> cells = null;
        double cell = Math.max(1.4d, Math.sqrt(area / want));
        // The pool has to be at least as large as the crowd, or the plan comes up short. Counting
        // cells whose centre falls inside the disc gives very nearly the right number; when it does
        // not, shrink the cell once and rebuild rather than silently showing a thinner crowd.
        for (int pass = 0; pass < 3; pass++) {
            cells = cellsIn(radius, cell, seed);
            if (cells.size() >= want) {
                break;
            }
            cell *= Math.sqrt(cells.size() / (double) want) * 0.95d;
        }
        // deterministic shuffle: the same cell always yields the same figure, but which figures
        // survive the cut to `want` does not follow the scan order
        cells.sort((a, b) -> Double.compare(shuffleKey(seed, a), shuffleKey(seed, b)));
        for (double[] c : cells) {
            if (out.size() >= want) {
                break;
            }
            out.add(new Figure(c[0], c[1], Math.hypot(c[0], c[1])));
        }
        return out;
    }

    /**
     * The crowd inside one chunk: exactly the figures the population gave that chunk, spread evenly
     * over the whole chunk, with no gaps between them and none at its edges.
     *
     * <h3>Why not the disc plan, and why not a grid with cells cut away</h3>
     * A chunk is 16x16 blocks. The disc plan fills radius 7 of it, so every chunk boundary leaves a
     * two-block lane and every corner of four chunks a hole over four blocks across: measured, the
     * worst empty distance inside a full chunk was <b>6.7 blocks</b> — a lattice of gaps across a
     * settlement, which is what makes a dense crowd read as sparse.
     *
     * <p>Building a grid and cutting it down to the count is not enough either: cutting cells out
     * leaves the holes where the cells were, and the corners are always the first to go. So the
     * figures are laid out as an <em>exact</em> lattice instead — rows spread evenly over the chunk,
     * each row spread evenly across it — which places precisely {@code figures} of them with even
     * spacing. The jitter is then kept small enough that the even spacing survives it and no two
     * figures stand closer than a shoulder: at the 77-figure cap the lattice step is 1.8 blocks and
     * the jitter is held under 0.3, so the closest pair is still 1.2 apart.
     */
    public static java.util.List<Figure> planSquare(long seed, int figures, double halfExtent) {
        java.util.List<Figure> out = new java.util.ArrayList<>();
        int want = Math.max(0, Math.min(MAX_FIGURES, figures));
        if (want == 0 || halfExtent <= 0.0d) {
            return out;
        }
        double side = 2.0d * halfExtent;
        int rows = Math.max(1, (int) Math.round(Math.sqrt(want)));
        int base = want / rows;
        int extra = want % rows;
        // the rows that carry one figure more are spread through the lattice rather than stacked at
        // one end, so the crowd has no dense side and no thin side; the counts sum to `want` exactly
        int[] counts = new int[rows];
        for (int r = 0; r < rows; r++) {
            counts[r] = base;
        }
        for (int k = 0; k < extra; k++) {
            counts[Math.min(rows - 1, (int) Math.round(k * (double) rows / extra))]++;
        }
        double stepY = side / rows;
        for (int r = 0; r < rows; r++) {
            int inRow = counts[r];
            if (inRow <= 0) {
                continue;
            }
            double stepX = side / inRow;
            double y = -halfExtent + stepY * (r + 0.5d);
            double jitter = Math.min(0.30d * Math.min(stepX, stepY),
                    Math.max(0.0d, (Math.min(stepX, stepY) - 1.25d) / 2.0d));
            for (int c = 0; c < inRow; c++) {
                double x = -halfExtent + stepX * (c + 0.5d);
                double jx = (unit(seed, r, c, 1) - 0.5d) * 2.0d * jitter;
                double jz = (unit(seed, r, c, 2) - 0.5d) * 2.0d * jitter;
                out.add(new Figure(clamp(x + jx, -halfExtent, halfExtent),
                        clamp(y + jz, -halfExtent, halfExtent), 0.0d));
            }
        }
        return out;
    }

    private static double clamp(double value, double low, double high) {
        return value < low ? low : (value > high ? high : value);
    }

    /** One jittered cell per candidate place, keeping only cells whose centre is inside the disc. */
    private static java.util.List<double[]> cellsIn(double radius, double cell, long seed) {
        java.util.List<double[]> cells = new java.util.ArrayList<>();
        int reach = (int) Math.ceil(radius / cell) + 1;
        for (int cx = -reach; cx <= reach; cx++) {
            for (int cz = -reach; cz <= reach; cz++) {
                double bx = cx * cell;
                double bz = cz * cell;
                if (Math.hypot(bx, bz) > radius) {
                    continue;
                }
                // Jitter is kept under a third of a cell each way: two neighbours can then never
                // come closer than about 0.7 blocks, and a person is 0.6 wide, so figures overlap
                // on screen the way a crowd does but never stand inside each other.
                double jx = (unit(seed, cx, cz, 1) - 0.5d) * cell * 0.6d;
                double jz = (unit(seed, cx, cz, 2) - 0.5d) * cell * 0.6d;
                double px = bx + jx;
                double pz = bz + jz;
                if (Math.hypot(px, pz) > radius) {
                    continue; // the nudge carried it past the edge; the figure is simply not placed
                }
                cells.add(new double[] {px, pz});
            }
        }
        return cells;
    }


    /** The plan's half-extent inside a chunk: one constant, so the plan never depends on a caller. */
    public static final double PLAN_HALF = 7.9d;

    /** A Minecraft day, in ticks. The crowd's day is the world's day. */
    public static final int DAY_TICKS = 24_000;

    /** When a person leaves home and when they come back: the morning walk, and the evening one. */
    private static final int DAY_OUT_START = 2_000;
    private static final int DAY_OUT_TICKS = 200;
    private static final int DAY_BACK_START = 13_000;

    /**
     * How far out a person is, on the day's own clock: 0 at home, 1 out for the day, and in between
     * while they are walking. Dawn and the evening are the two walks; the rest of the time they are
     * standing somewhere.
     *
     * <p>This is the answer to a person who never moved. A figure has no goals (that is what makes a
     * crowd affordable), so nothing ever moved one: it stood where the plan put it until the plan
     * changed. Giving the crowd a day means a person's position is a function of the world clock —
     * so two Minecraft days passing changes where they are even if nobody was there to watch them
     * walk.
     */
    public static double dayOut(long gameTime) {
        long t = Math.floorMod(gameTime, (long) DAY_TICKS);
        if (t < DAY_OUT_START || t >= DAY_BACK_START) {
            return 0.0d;
        }
        double out = (t - DAY_OUT_START) / (double) DAY_OUT_TICKS;
        double back = (DAY_BACK_START + DAY_OUT_TICKS - t) / (double) DAY_OUT_TICKS;
        return Math.max(0.0d, Math.min(1.0d, Math.min(out, back)));
    }

    /**
     * Where a person stands at a moment: their own home, walked out along the day's own route.
     *
     * <p>Deterministic in everything: the world's seed, who they are, where home is, and the time.
     * Nothing is stored and nothing ticks, so a person who is not being looked at costs nothing and
     * still moves with the clock — come back after two days and they are somewhere else, because
     * their route is a different one on a different day.
     *
     * @param personSeed who they are; for a figure, the identity the ground gave it
     * @param walkBlocks how far out the walk may go, before any clamping the caller does
     * @return {x, z} in blocks
     */
    /**
     * The day's own walk, as a vector: how far out this person goes today, and which way.
     *
     * <p>Exposed because a figure's walk has to fit inside its own chunk, and only the caller knows
     * where the edges are: it can shrink the walk to what fits, or turn it back if home is against an
     * edge and today's route points out of the world.
     */
    public static double[] routineVector(long worldSeed, long personSeed, long gameTime,
                                         double walkBlocks) {
        long day = Math.floorDiv(gameTime, (long) DAY_TICKS);
        long s = mix(worldSeed ^ personSeed ^ (day * 0x9E3779B97F4A7C15L));
        // The day's direction turns a long way from one day to the next — three quarters of a turn,
        // give or take a jitter — so "the same person, two days later" is never standing in the same
        // corner by accident.
        double jitter = ((s >>> 11) * (1.0d / (1L << 53)) - 0.5d) * 1.2d;
        double angle = day * 2.39996322972865332d + jitter;
        long l = mix(s ^ 0x51ED270B5A5A5A5AL);
        double length = walkBlocks * (0.45d + 0.55d * ((l >>> 11) * (1.0d / (1L << 53))));
        return new double[] { Math.cos(angle) * length, Math.sin(angle) * length };
    }

    /**
     * The same walk, fitted inside a box: shrunk to what fits the room there is, and turned back if
     * what fits is barely a walk at all.
     *
     * <p>A figure's day has to stay inside its own chunk. Clamping to the edge instead would leave a
     * person standing perfectly still whenever their route pointed out of the chunk — which is the
     * very complaint this day exists to answer — so the route is scaled to the room, and reversed if
     * the room only allows a shuffle.
     *
     * @return {x, z} in blocks, inside the box of the given centre and half-extent
     */
    public static double[] routinePointIn(long worldSeed, long personSeed, double homeX, double homeZ,
                                          long gameTime, double walkBlocks, double centreX,
                                          double centreZ, double limit) {
        double[] v = routineVector(worldSeed, personSeed, gameTime, walkBlocks);
        double dx = homeX - centreX;
        double dz = homeZ - centreZ;
        double scale = fitScale(v, dx, dz, limit);
        if (scale < 0.5d) {
            double flippedX = -v[0];
            double flippedZ = -v[1];
            double turned = fitScale(new double[] { flippedX, flippedZ }, dx, dz, limit);
            if (turned > scale) {
                v[0] = flippedX;
                v[1] = flippedZ;
                scale = turned;
            }
        }
        double out = dayOut(gameTime);
        return new double[] { homeX + v[0] * scale * out, homeZ + v[1] * scale * out };
    }

    /** How much of a walk fits before it leaves the box. */
    public static double fitScale(double[] v, double dx, double dz, double limit) {
        double roomX = v[0] > 0.0d ? limit - dx : limit + dx;
        double roomZ = v[1] > 0.0d ? limit - dz : limit + dz;
        double needX = Math.max(1.0e-6d, Math.abs(v[0]));
        double needZ = Math.max(1.0e-6d, Math.abs(v[1]));
        return Math.max(0.0d, Math.min(1.0d, Math.min(roomX / needX, roomZ / needZ)));
    }

    public static double[] routinePoint(long worldSeed, long personSeed, double homeX, double homeZ,
                                        long gameTime, double walkBlocks) {
        double[] v = routineVector(worldSeed, personSeed, gameTime, walkBlocks);
        double out = dayOut(gameTime);
        return new double[] { homeX + v[0] * out, homeZ + v[1] * out };
    }

    /** The seed a chunk's own plan is built from: the world's ground, never the player's position. */
    public static long placeSeed(long worldSeed, int chunkX, int chunkZ) {
        return worldSeed ^ ((long) chunkX << 32) ^ chunkZ;
    }
    /** Deterministic order, so the same place always keeps the same figures. */
    private static double shuffleKey(long seed, double[] cell) {
        return unit(seed, (int) Math.round(cell[0] * 16), (int) Math.round(cell[1] * 16), 3);
    }

    /**
     * Which of the crowd's places the <em>living</em> people should take.
     *
     * <p>The people who can be talked to should be the ones you meet first, not a knot in the middle
     * of a field of figures. Nearest to the player is nearest to the conversation, so the living few
     * go on the near side and the drawn crowd stands behind them.
     *
     * @param figures   the crowd plan
     * @param playerDx  the player's offset from the crowd centre, in blocks
     * @param playerDz  the same, in the other axis
     * @param count     how many living people are coming
     */
    public static int[] nearestSlots(java.util.List<Figure> figures, double playerDx, double playerDz, int count) {
        int want = Math.max(0, Math.min(count, figures.size()));
        Integer[] order = new Integer[figures.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> Double.compare(
                figures.get(a).distance() + approach(playerDx, playerDz, figures.get(a)),
                figures.get(b).distance() + approach(playerDx, playerDz, figures.get(b))));
        int[] slots = new int[want];
        for (int i = 0; i < want; i++) {
            slots[i] = order[i];
        }
        return slots;
    }

    /** How far past the crowd's edge a figure is from the player: the nearer the player, the less. */
    private static double approach(double playerDx, double playerDz, Figure figure) {
        double dx = figure.dx() - playerDx;
        double dz = figure.dz() - playerDz;
        return Math.hypot(dx, dz);
    }

    /**
     * How many figures this place shows, after the server's own drawing cap.
     *
     * <p>The cap is deliberately <em>not</em> {@code presenceCrowdCeiling}: that knob caps the people
     * who think, and letting it cap the people who are merely seen is what made a crowd of forty
     * look like a group of forty. This cap is about the client, not the server.
     */
    public static int target(long population, SettlementRank rank, int configuredMax) {
        int cap = Math.max(MIN_FIGURES, Math.min(MAX_FIGURES, configuredMax));
        return Math.min(cap, figuresFor(population, rank));
    }

    /**
     * Who keeps their place, who is released to take one somewhere else, and who is still wanted:
     * what the crowd does when the drawing moves.
     *
     * <p>Three numbers per chunk, and no fourth. Nothing here deletes a figure: a chunk the drawing
     * no longer wants releases its surplus, and a chunk that is short takes a released figure before
     * a new one is ever stood up. That is why turning round can neither leave a second crowd
     * standing behind the player nor throw a living thing away — the crowd is moved, not unmade.
     *
     * @param want what the drawing wants in each chunk, from {@link ChunkPopulation#viewTowards}
     * @param here how many figures each chunk is actually holding now
     * @return per chunk {kept, released, needed}
     */
    public static int[][] reconcile(int[] want, int[] here) {
        int[][] out = new int[want.length][3];
        for (int i = 0; i < want.length; i++) {
            int wanted = Math.max(0, want[i]);
            int standing = i < here.length ? Math.max(0, here[i]) : 0;
            out[i][0] = Math.min(wanted, standing);
            out[i][1] = Math.max(0, standing - wanted);
            out[i][2] = Math.max(0, wanted - standing);
        }
        return out;
    }

    /**
     * Which crowd places are still free, nearest the crowd's own centre first, for figures that are
     * being added to a crowd that is already partly standing.
     *
     * <p>Filling from the centre outward is what makes a half-built crowd look like a crowd rather
     * than a ring: the middle is the part you see first, and the edge is where you can afford to be
     * sparse while the rest is still arriving.
     *
     * @param plan       the full crowd
     * @param occupied   places already taken, as {dx, dz} pairs — living people and existing figures
     * @param clearance  nobody may be placed within this many blocks of a taken place
     * @param need       how many free places are wanted
     * @return indexes into {@code plan}
     */
    public static int[] freeSlots(java.util.List<Figure> plan, java.util.List<double[]> occupied,
                                  double clearance, int need) {
        int want = Math.max(0, Math.min(need, plan.size()));
        int[] out = new int[want];
        if (want == 0) {
            return out;
        }
        double clear = clearance * clearance;
        int found = 0;
        boolean[] used = new boolean[plan.size()];
        // Two passes: the inner half first, then whatever is left, so a crowd that is being filled
        // over several cycles always has its middle standing before its edges.
        for (int pass = 0; pass < 2 && found < want; pass++) {
            for (int i = 0; i < plan.size() && found < want; i++) {
                if (used[i]) {
                    continue;
                }
                Figure f = plan.get(i);
                if (pass == 0 && f.distance() > radiusOf(plan) * 0.5d) {
                    continue;
                }
                boolean clashes = false;
                for (double[] o : occupied) {
                    double dx = f.dx() - o[0];
                    double dz = f.dz() - o[1];
                    if (dx * dx + dz * dz < clear) {
                        clashes = true;
                        break;
                    }
                }
                if (clashes) {
                    continue;
                }
                used[i] = true;
                out[found++] = i;
            }
        }
        if (found == want) {
            return out;
        }
        int[] shorter = new int[found];
        System.arraycopy(out, 0, shorter, 0, found);
        return shorter;
    }

    /**
     * Who stands at a place in a chunk's crowd: the same chunk and the same place in the plan are
     * always the same person.
     *
     * <p>This is the figure's identity, and it is what makes coming back different from arriving.
     * The mod draws a person by its UUID — the skin is chosen from it — so a figure built fresh with
     * a random UUID is a stranger every time, however carefully it is put back in the same spot.
     * Given the world's seed and the place in the plan, this returns the same identity forever: walk
     * away, come back, and the crowd that stands where it stood is the same crowd, face for face.
     *
     * <p>It is deliberately <em>not</em> a function of the year or of the place: a figure belongs to
     * the ground it stands on, and it should be the same person whether it is counted as this
     * village's or the next one's, this year or the next.
     *
     * @param worldSeed the world's seed, so two worlds do not share inhabitants
     * @param chunkX    the chunk the figure belongs to
     * @param chunkZ    the chunk the figure belongs to
     * @param slot      its place in that chunk's plan
     */
    public static java.util.UUID identity(long worldSeed, int chunkX, int chunkZ, int slot) {
        long h = mix(worldSeed ^ 0x9E3779B97F4A7C15L);
        h = mix(h ^ (((long) chunkX) << 32) ^ (chunkZ & 0xFFFFFFFFL));
        long lo = mix(h ^ (slot * 0xBF58476D1CE4E5B9L));
        long hi = mix(lo ^ ~h);
        // a well-formed identifier: version 4, IETF variant
        hi = (hi & 0xFFFFFFFFFFFF0FFFL) | 0x0000000000004000L;
        lo = (lo & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new java.util.UUID(hi, lo);
    }

    /** SplitMix64: a small, well-spread mixing step, and the same in every Java. */
    private static long mix(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /**
     * The count a chunk should hold, given what the drawing wants and what is already standing.
     *
     * <p>Without this the crowd reshuffles whenever the drawing nudges: the fan's weights move with
     * every step the player takes, so a chunk is asked for five figures, then four, then five, and a
     * figure is built and thrown away on each swing. A chunk that is standing within a quarter of
     * what is asked for keeps exactly what it has; only a real change moves it, and a chunk the
     * drawing has emptied is emptied.
     */
    public static int settledCount(int wanted, int standing) {
        if (wanted <= 0) {
            return 0; // the drawing wants nobody here: the chunk is cleared
        }
        if (standing <= 0) {
            return wanted; // nobody standing: it is filled
        }
        int tolerance = Math.max(3, wanted / 4);
        return Math.abs(wanted - standing) <= tolerance ? standing : wanted;
    }

    /** The farthest figure in a plan, which is the crowd's own radius as drawn. */
    private static double radiusOf(java.util.List<Figure> plan) {
        double max = 0.0d;
        for (Figure f : plan) {
            max = Math.max(max, f.distance());
        }
        return max;
    }

    /**
     * Which way a figure faces, in degrees. A crowd where everyone faces north looks like a parade
     * ground; four hundred people glance in whatever direction they were already looking.
     */
    public static float facing(long seed, int slot) {
        return (float) (unit(seed, slot, 17, 5) * 360.0d);
    }

    /** Deterministic hash in [0,1): the same cell always jitters the same way. */
    static double unit(long seed, int a, int b, int salt) {
        long h = seed;
        h ^= (long) a * 0x9E3779B97F4A7C15L;
        h ^= (long) b * 0xC2B2AE3D27D4EB4FL;
        h ^= (long) salt * 0x165667B19E3779F9L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h >>> 11) * 0x1.0p-53;
    }
}

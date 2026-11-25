package com.eternalengine.entity;

import com.eternalengine.affairs.CurrentAffairsBriefing;
import com.eternalengine.affairs.CurrentAffairsEngine;
import com.eternalengine.capability.EternalEngineCapabilities;
import com.eternalengine.capability.IEntityCivilizationData;
import com.eternalengine.core.SimulationEngine;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * HumanNpcEntity represents a living human in the Eternal Engine historical simulation.
 * It uses standard player-shaped dimensions (0.6 x 1.8) and is rendered client-side
 * using a player model with era- and culture-specific garments.
 */
public class HumanNpcEntity extends PathfinderMob {

    private static final EntityDataAccessor<String> DATA_CIVILIZATION_ID =
        SynchedEntityData.defineId(HumanNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_ROLE =
        SynchedEntityData.defineId(HumanNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_ERA =
        SynchedEntityData.defineId(HumanNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_SKIN_ID =
        SynchedEntityData.defineId(HumanNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_GENDER =
        SynchedEntityData.defineId(HumanNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_LOYALTY =
        SynchedEntityData.defineId(HumanNpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_ROUTINE_STATE =
        SynchedEntityData.defineId(HumanNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_SPEECH_TEXT =
        SynchedEntityData.defineId(HumanNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_SPEECH_TICKS =
        SynchedEntityData.defineId(HumanNpcEntity.class, EntityDataSerializers.INT);

    public HumanNpcEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setCanPickUpLoot(true);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_CIVILIZATION_ID, "neutral");
        this.entityData.define(DATA_ROLE, "citizen");
        this.entityData.define(DATA_ERA, "ancient");
        this.entityData.define(DATA_SKIN_ID, "default");
        this.entityData.define(DATA_GENDER, "neutral");
        this.entityData.define(DATA_LOYALTY, 50);
        this.entityData.define(DATA_ROUTINE_STATE, "idle");
        this.entityData.define(DATA_SPEECH_TEXT, "");
        this.entityData.define(DATA_SPEECH_TICKS, 0);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 20.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.28D)
            .add(Attributes.FOLLOW_RANGE, 32.0D)
            .add(Attributes.ATTACK_DAMAGE, 2.0D);
    }

    /**
     * The one goal of the five that costs real money: picking a spot and walking to it means an
     * A* path search, then a path followed node by node. Kept as a field so a settled crowd
     * member can have it taken away and given back.
     *
     * <p>Assigned inside {@link #registerGoals()} rather than at field declaration on purpose:
     * {@code Mob}'s constructor calls {@code registerGoals()} before subclass field initialisers
     * run, so a field initialiser would still be null when the goal is added.
     */
    private Goal strollGoal;

    private boolean settled;

    /**
     * The presence spawner's people (townspeople and event scenes) leave only through the spawner, which writes
     * them into the place's memory first (PeopleMemory). Vanilla's despawn ran every tick and deleted them without
     * a word, so a place came back mostly with strangers: after a walk away and back, 17 of 37 people were the same
     * (2026-10-12, step 17). The spawner applies vanilla's own two rules itself (PresenceLocalSpawner).
     */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        CompoundTag tag = getPersistentData();
        if (tag.getBoolean(com.eternalengine.villager.PresenceLocalSpawner.TAG_PRESENCE)
                || tag.getBoolean(com.eternalengine.villager.PresenceLocalSpawner.TAG_EVENT)) {
            return false;
        }
        return super.removeWhenFarAway(distanceToClosestPlayer);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new OpenDoorGoal(this, true));
        this.strollGoal = new WaterAvoidingRandomStrollGoal(this, 0.7D);
        this.goalSelector.addGoal(2, this.strollGoal);
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    /**
     * Whether this person stands their ground instead of pacing about.
     *
     * <p>A settled person keeps watching you and glancing around — the two cheap goals — and gives
     * up the one goal that runs a path search. In a district, where people stand three blocks
     * apart, pacing is also the worst thing they could do: they bump, paths block, and the game
     * re-plans, which is the expensive failure mode. Standing still is both cheaper and better
     * behaved.
     *
     * <p>Only the crowd this spawner placed is ever settled. Everyone else — VIPs, plague doctors,
     * caravans, bandits, rival variants — keeps the behaviour it was written with.
     */
    public void setSettled(boolean settled) {
        if (settled == this.settled || this.strollGoal == null) {
            return;
        }
        this.settled = settled;
        if (settled) {
            this.goalSelector.removeGoal(this.strollGoal);
            // Stop mid-step rather than finishing the walk. A district should read as people who
            // are standing there, not as people caught in the act of leaving.
            this.getNavigation().stop();
        } else {
            this.goalSelector.addGoal(2, this.strollGoal);
        }
    }

    public boolean isSettled() {
        return this.settled;
    }

    /** Tick of the last try to step out of a wall (at most one try a second). */
    private int lastWallEscape = -100;

    /**
     * Step 18b: a wall never kills a townsperson. Terra++ puts buildings into a chunk after its ground, in the
     * background and again after a time jump, so someone standing in the open can find a wall around them (two
     * townspeople suffocated in the user's game). They step out instead, see {@link #outOfWall()}.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)) {
            if (!this.level().isClientSide) {
                outOfWall();
            }
            return false;
        }
        return super.hurt(source, amount);
    }

    /**
     * Out of a wall: a crowd figure is taken out (its place in the crowd plan is built over now, and the crowd
     * does not stand anyone on a place that cannot hold them); a townsperson steps to the nearest place with
     * ground under the feet and room for the head, beside them first, then up. At most one try a second.
     */
    private void outOfWall() {
        if (this.tickCount - lastWallEscape < 20) {
            return;
        }
        lastWallEscape = this.tickCount;
        if (com.eternalengine.population.CrowdFigures.isFigure(this)) {
            this.discard();
            return;
        }
        net.minecraft.core.BlockPos base = this.blockPosition();
        net.minecraft.core.BlockPos to = null;
        search:
        for (int r = 1; r <= 4; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    for (int dy : new int[]{0, 1, -1, 2, -2, 3}) {
                        net.minecraft.core.BlockPos p = base.offset(dx, dy, dz);
                        if (canStandAt(p)) {
                            to = p;
                            break search;
                        }
                    }
                }
            }
        }
        for (int dy = 1; to == null && dy <= 32; dy++) {
            if (canStandAt(base.above(dy))) {
                to = base.above(dy);
            }
        }
        if (to != null) {
            this.getNavigation().stop();
            this.moveTo(to.getX() + 0.5d, to.getY(), to.getZ() + 0.5d, this.getYRot(), this.getXRot());
        }
    }

    private boolean canStandAt(net.minecraft.core.BlockPos p) {
        Level lvl = this.level();
        if (!lvl.hasChunkAt(p)) {
            return false;
        }
        return !lvl.getBlockState(p.below()).getCollisionShape(lvl, p.below()).isEmpty()
                && lvl.getBlockState(p).getCollisionShape(lvl, p).isEmpty()
                && lvl.getBlockState(p.above()).getCollisionShape(lvl, p.above()).isEmpty()
                && lvl.getFluidState(p).isEmpty()
                && lvl.getFluidState(p.above()).isEmpty();
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide) {
            // Step 21: somebody being talked to turns to the player. With its AI on, customServerAiStep does it through
            // the look control; a figure (AI off) is turned here directly.
            if (this.isNoAi() && com.eternalengine.knowledge.TalkHold.mayBeHeld(this.getId())) {
                Player talker = com.eternalengine.knowledge.TalkHold.talker(this);
                if (talker != null) {
                    float yaw = (float) (Math.toDegrees(Math.atan2(talker.getZ() - this.getZ(), talker.getX() - this.getX()))) - 90.0f;
                    this.setYRot(yaw);
                    this.setYHeadRot(yaw);
                    this.setYBodyRot(yaw);
                }
            }
            // Step 18b: figures are invulnerable, so the wall check cannot reach them through hurt(); look once a second.
            if ((this.tickCount + this.getId()) % 20 == 0 && this.isInWall()) {
                outOfWall();
            }
            int speechTicks = this.entityData.get(DATA_SPEECH_TICKS);
            if (speechTicks > 0) {
                this.entityData.set(DATA_SPEECH_TICKS, speechTicks - 1);
                if (speechTicks - 1 == 0) {
                    this.entityData.set(DATA_SPEECH_TEXT, "");
                }
            }
        }
    }

    /**
     * Step 21: while a player talks or trades with this person, they stop where they are and look at the player. Runs
     * after the goals and the path for this tick and before the look control, so it wins over strolling and glancing.
     */
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (com.eternalengine.knowledge.TalkHold.mayBeHeld(this.getId())) {
            Player talker = com.eternalengine.knowledge.TalkHold.talker(this);
            if (talker != null) {
                this.getNavigation().stop();
                this.getLookControl().setLookAt(talker, 30.0f, 30.0f);
            }
        }
        // Step R1: the role day — the town's clocks (permission 05). Skipped while a talk holds the
        // person, so a talk still wins; the talk-hold block above never falls through to this one.
        if (!com.eternalengine.knowledge.TalkHold.mayBeHeld(this.getId())) {
            com.eternalengine.villager.RoleDaySystem.step(this);
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND) {
            if (!this.level().isClientSide) {
                speakToPlayer(player);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    /**
     * Right-click: the talk screen (step 18). The villager opens with today's news; the screen lets the
     * player ask questions and follow names. The speech bubble shows the opening line to everyone nearby.
     * Players without a connection (fake players) and any failure fall back to the old one-line briefing.
     */
    private void speakToPlayer(Player player) {
        String name = this.hasCustomName() ? this.getCustomName().getString() : "Citizen";
        String speech = "";
        boolean screen = false;
        try {
            ServerPlayer real = player instanceof ServerPlayer sp && !(player instanceof net.minecraftforge.common.util.FakePlayer) ? sp : null;
            com.eternalengine.knowledge.TalkService.Ctx ctx = com.eternalengine.knowledge.TalkService.context(real, this);
            // Step 21: the person stays for the talk. Only for a player who is in this world (machines with a fake
            // player never hold anybody); a crowd figure becomes a real townsperson with the name the player hears.
            if (player instanceof ServerPlayer holder && this.level().getPlayerByUUID(holder.getUUID()) != null) {
                if (com.eternalengine.population.CrowdFigures.isFigure(this)) {
                    com.eternalengine.villager.PresenceLocalSpawner.adoptFigure(this, ctx.name());
                }
                com.eternalengine.knowledge.TalkHold.hold(holder, this);
                name = ctx.name();
            }
            com.eternalengine.knowledge.TalkView view = com.eternalengine.knowledge.TalkService.open(ctx);
            speech = view.plainText();
            if (real != null) {
                com.eternalengine.network.EternalEnginePacketHandler.sendToPlayer(
                        new com.eternalengine.network.packets.TalkViewPacket(view), real);
                screen = true;
            }
        } catch (Exception ignored) {
            // The old briefing below still gives the player a line.
        }
        if (speech.isBlank()) {
            speech = briefingLine();
        }
        if (speech.isBlank()) {
            speech = String.format("Greetings, traveler. I am %s, serving the %s.", name, getCivilizationId());
        }

        this.entityData.set(DATA_SPEECH_TEXT, speech);
        this.entityData.set(DATA_SPEECH_TICKS, 120); // 6 seconds display

        if (!screen && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.literal(
                String.format("<%s> %s", name, speech)
            ));
        }
    }

    /** The one-line briefing villagers gave before the talk screen (the first "event" fact). */
    private String briefingLine() {
        try {
            CurrentAffairsEngine affairs = SimulationEngine.getInstance().getCurrentAffairsEngine();
            if (affairs != null && this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                CurrentAffairsBriefing briefing = affairs.createBriefing(
                    com.eternalengine.core.WorldStateManager.getInstance(),
                    serverLevel,
                    this.blockPosition(),
                    com.eternalengine.affairs.BotAffairsRole.VILLAGER,
                    com.eternalengine.affairs.AffairsScope.LOCAL,
                    java.util.List.of()
                );
                if (briefing != null && !briefing.facts().isEmpty()) {
                    return briefing.facts().stream()
                        .filter(f -> "event".equals(f.category()))
                        .map(com.eternalengine.affairs.CurrentAffairsFact::text)
                        .findFirst()
                        .orElse(briefing.facts().get(0).text());
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    // ── GETTERS & SETTERS ──────────────────────────────────────────

    public String getCivilizationId() {
        return this.entityData.get(DATA_CIVILIZATION_ID);
    }

    public void setCivilizationId(String civId) {
        this.entityData.set(DATA_CIVILIZATION_ID, civId == null ? "neutral" : civId);
        IEntityCivilizationData data = EternalEngineCapabilities.getEntityData(this);
        if (data != null) {
            data.setCivilizationId(civId);
        }
    }

    public String getRole() {
        return this.entityData.get(DATA_ROLE);
    }

    public void setRole(String role) {
        this.entityData.set(DATA_ROLE, role == null ? "citizen" : role);
        IEntityCivilizationData data = EternalEngineCapabilities.getEntityData(this);
        if (data != null) {
            data.setRole(role);
        }
    }

    /**
     * The nameplate's job mark: the person's name with the role's symbol after it, so the job
     * reads at a glance from the street. The mark is dimmed so the name stays the loud part.
     * A plain citizen (or an unmarked role) carries no mark at all.
     */
    public static String withRoleMark(String name, String role) {
        if (name == null) {
            return null;
        }
        String mark = roleMark(role);
        return mark.isEmpty() ? name : name + " \u00a77" + mark;
    }

    /**
     * The symbol for a role, or the empty string when the role carries no mark.
     * Marks are era-neutral: a tool or a sign of the trade, not a word, so they hold from
     * 3000 BC to 3000 CE without dating themselves.
     */
    /**
     * What the trade carries (the sheet's roles band items), in plain era-neutral words. A string the
     * talk box can show under the roles band; empty when the trade carries nothing to show.
     */
    public static String roleItems(String role) {
        if (role == null || role.isBlank()) {
            return "";
        }
        String items = switch (role.toLowerCase(java.util.Locale.ROOT)) {
            case "farmer" -> "grain · seed · hoe · bullock-cart";
            case "forager" -> "herbs · dried fruit · basket · knife";
            case "artisan" -> "tools · pattern · finished work · gear";
            case "merchant" -> "stock · account book · scales · coin";
            case "trader" -> "stock · scales · road pass · coin";
            case "pedlar" -> "wares · basket · road pass · coin";
            case "money_changer" -> "coin · small change · scale · ledger";
            case "paper_seller" -> "paper · news · ink · twine";
            case "news_writer" -> "ink · quill · paper · news";
            case "town_crier" -> "horn · notice · rope";
            case "weaver" -> "cotton · finished cloth · loom tools · coin";
            case "smith" -> "hammer · anvil · bellows · iron";
            case "mason", "imperial_mason" -> "trowel · chisel · stone · rope";
            case "scribe" -> "pen · ink · paper · seal";
            case "scholar" -> "books · notes · ink · tablet";
            case "clerk" -> "ledgers · seal · ink · the day's orders";
            case "teacher" -> "books · slates · ink · the figures of the bazaar";
            case "priest" -> "prayer mat · the bell · beads · the scripture";
            case "doctor" -> "medicine · mortar · bandage · tincture";
            case "healer" -> "herbs · salve · bandage · tincture";
            case "quarantine_inspector" -> "ledger · bell · chalk · the notice";
            case "soldier" -> "weapon · blade · ammunition · mess tin";
            case "ship_marine" -> "blade · rope · oar · mess tin";
            case "guard", "royal_guard", "caravan_guard" -> "blade · whistle · truncheon · lantern";
            case "commander" -> "sword · map · signal flags · seal";
            case "ruler" -> "seal · standard · ring · the decree";
            case "sovereign_ruler" -> "crown · seal · ring · the decree";
            case "court_chancellor" -> "seal · the decree · ink · the letters";
            case "caravan_master" -> "tent · rope · ledger · water skins";
            case "ship_captain" -> "anchor · chart · bell · compass";
            case "bandit" -> "knife · rope · the mask · coin";
            case "rival_infiltrator" -> "cloak · knife · token · the false seal";
            default -> "";
        };
        return items;
    }

    public static String roleMark(String role) {
        if (role == null || role.isBlank()) {
            return "";
        }
        switch (role.toLowerCase(java.util.Locale.ROOT)) {
            // Street trades - the settlement ranks, the site traits, the market and the news
            case "farmer": return "\u2663";   // club - the growing field
            case "forager": return "\u2698";  // the gathered flower
            case "artisan": return "\u2699";  // the turning gear
            case "merchant": return "\u2696"; // scales - the weighed trade
            case "trader": return "\u21c4";   // the town exchange
            case "pedlar": return "\u27a4";   // the going road
            case "money_changer": return "\u21cc"; // the coin turned over
            case "paper_seller": return "\u00b6";  // the pilcrow - the printed page
            case "news_writer": return "\u270d";   // the writing hand
            case "town_crier": return "\u25c9";    // the voice made public
            case "weaver": return "\u25a6";   // the woven grid
            case "smith": return "\u2692";    // hammer and pick
            case "mason": return "\u25a8";    // the laid block
            case "scribe": return "\u270e";   // the pen
            case "scholar": return "\u2712";  // the pen nib
            case "clerk": return "\u2709";    // the letter and the ledger
            case "teacher": return "\u2605";  // the star
            case "priest": return "\u2726";   // the set-apart star
            case "doctor": return "\u271a";   // the cross
            case "healer": return "\u271a";   // the cross - same trade, older word
            case "quarantine_inspector": return "\u271a"; // the cross - the health gate
            // The street's order
            case "soldier": return "\u2694";  // crossed swords
            case "guard": return "\u265f";    // the standing pawn
            case "royal_guard": return "\u265f";  // the standing pawn - a guard still
            case "commander": return "\u265e";    // the knight
            case "ruler": return "\u2654";    // the king
            case "sovereign_ruler": return "\u265a"; // the crowned black king
            case "court_chancellor": return "\u265b"; // the ruler's right hand
            // Scene people - caravans, ships, raids, the rival's shadow
            case "caravan_master": return "\u26fa";  // the tent
            case "caravan_guard": return "\u265f";   // the standing pawn - a guard still
            case "ship_captain": return "\u2693";    // the anchor
            case "ship_marine": return "\u2694";     // crossed swords - a fighter still
            case "imperial_mason": return "\u25a8";  // the laid block - a mason still
            case "bandit": return "\u26a1";   // the sudden strike
            case "rival_infiltrator": return "\u25cc"; // the dotted circle - the one not seen
            default: return "";
        }
    }

    public String getEra() {
        return this.entityData.get(DATA_ERA);
    }

    public void setEra(String era) {
        this.entityData.set(DATA_ERA, era == null ? "ancient" : era);
    }

    public String getSkinId() {
        return this.entityData.get(DATA_SKIN_ID);
    }

    public void setSkinId(String skinId) {
        this.entityData.set(DATA_SKIN_ID, skinId == null ? "default" : skinId);
    }

    public String getGender() {
        return this.entityData.get(DATA_GENDER);
    }

    public void setGender(String gender) {
        this.entityData.set(DATA_GENDER, gender == null ? "neutral" : gender);
    }

    public int getLoyalty() {
        return this.entityData.get(DATA_LOYALTY);
    }

    public void setLoyalty(int loyalty) {
        this.entityData.set(DATA_LOYALTY, Math.max(0, Math.min(100, loyalty)));
        IEntityCivilizationData data = EternalEngineCapabilities.getEntityData(this);
        if (data != null) {
            data.setLoyalty(loyalty);
        }
    }

    public String getRoutineState() {
        return this.entityData.get(DATA_ROUTINE_STATE);
    }

    public void setRoutineState(String state) {
        this.entityData.set(DATA_ROUTINE_STATE, state == null ? "idle" : state);
    }

    public String getSpeechText() {
        return this.entityData.get(DATA_SPEECH_TEXT);
    }

    public void setSpeech(String speech) {
        String text = speech == null ? "" : speech;
        this.entityData.set(DATA_SPEECH_TEXT, text);
        this.entityData.set(DATA_SPEECH_TICKS, text.isBlank() ? 0 : 160);
    }

    public boolean isSpeaking() {
        return this.entityData.get(DATA_SPEECH_TICKS) > 0 && !getSpeechText().isBlank();
    }

    // ── NBT SERIALIZATION ──────────────────────────────────────────

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("CivilizationId", getCivilizationId());
        tag.putString("Role", getRole());
        tag.putString("Era", getEra());
        tag.putString("SkinId", getSkinId());
        tag.putString("Gender", getGender());
        tag.putInt("Loyalty", getLoyalty());
        tag.putString("RoutineState", getRoutineState());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("CivilizationId")) {
            setCivilizationId(tag.getString("CivilizationId"));
        }
        if (tag.contains("Role")) {
            setRole(tag.getString("Role"));
        }
        if (tag.contains("Era")) {
            setEra(tag.getString("Era"));
        }
        if (tag.contains("SkinId")) {
            setSkinId(tag.getString("SkinId"));
        }
        if (tag.contains("Gender")) {
            setGender(tag.getString("Gender"));
        }
        if (tag.contains("Loyalty")) {
            setLoyalty(tag.getInt("Loyalty"));
        }
        if (tag.contains("RoutineState")) {
            setRoutineState(tag.getString("RoutineState"));
        }
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }
}

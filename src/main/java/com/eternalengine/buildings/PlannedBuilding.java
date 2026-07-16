package com.eternalengine.buildings;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A safe building plan. Contains block placements only; does not mutate world.
 */
public final class PlannedBuilding {
    private final String planId;
    private final ResourceKey<Level> dimension;
    private final BlockPos origin;
    private final Map<BlockPos, BlockState> blocks;
    private final String explanation;

    public PlannedBuilding(String planId, ResourceKey<Level> dimension, BlockPos origin,
                           Map<BlockPos, BlockState> blocks, String explanation) {
        this.planId = planId == null || planId.isBlank() ? "plan_unknown" : planId;
        this.dimension = dimension;
        this.origin = origin == null ? BlockPos.ZERO : origin.immutable();
        this.blocks = Collections.unmodifiableMap(new HashMap<>(blocks == null ? Map.of() : blocks));
        this.explanation = explanation == null ? "No explanation" : explanation;
    }

    public int enqueue(BlockUpdateQueue queue, int priority) {
        if (queue == null || dimension == null || blocks.isEmpty()) {
            return 0;
        }
        queue.enqueueMany(dimension, blocks, priority);
        return blocks.size();
    }

    public String planId() { return planId; }
    public ResourceKey<Level> dimension() { return dimension; }
    public BlockPos origin() { return origin; }
    public Map<BlockPos, BlockState> blocks() { return blocks; }
    public String explanation() { return explanation; }
    public int size() { return blocks.size(); }
}

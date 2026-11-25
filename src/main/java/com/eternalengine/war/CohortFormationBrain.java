package com.eternalengine.war;

import com.eternalengine.terrabridge.TerraCoordinateHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Calculates deterministic 3D formation offsets for soldiers relative to
 * a regiment's standard bearer / commander.
 */
public final class CohortFormationBrain {

    private CohortFormationBrain() {}

    /**
     * Calculates the world target position for a soldier at index 'unitIndex'
     * in a cohort of total size 'totalUnits' around the commander's anchor position.
     */
    public static Vec3 calculateTargetPosition(BlockPos anchorPos, float facingYawDegrees,
                                               FormationType formation, int unitIndex,
                                               int totalUnits, ServerLevel level) {
        if (anchorPos == null) {
            return Vec3.ZERO;
        }
        if (unitIndex <= 0) {
            // Index 0 is the commander / standard bearer at anchor
            int y = level != null ? TerraCoordinateHelper.getPlacementY(level, anchorPos.getX(), anchorPos.getZ()) : anchorPos.getY();
            return new Vec3(anchorPos.getX() + 0.5D, y, anchorPos.getZ() + 0.5D);
        }

        double[] localOffset = calculateLocalOffset(formation, unitIndex, totalUnits);
        double localRight = localOffset[0];   // Positive = right, negative = left
        double localForward = localOffset[1]; // Positive = forward, negative = backward (behind commander)

        // Rotate local offset by facing yaw
        double yawRad = Math.toRadians(-facingYawDegrees);
        double cos = Math.cos(yawRad);
        double sin = Math.sin(yawRad);

        double worldDx = localRight * cos - localForward * sin;
        double worldDz = localRight * sin + localForward * cos;

        double targetX = anchorPos.getX() + 0.5D + worldDx;
        double targetZ = anchorPos.getZ() + 0.5D + worldDz;
        int targetBlockX = (int) Math.floor(targetX);
        int targetBlockZ = (int) Math.floor(targetZ);

        int targetY = level != null
            ? TerraCoordinateHelper.getPlacementY(level, targetBlockX, targetBlockZ)
            : anchorPos.getY();

        return new Vec3(targetX, targetY, targetZ);
    }

    /**
     * Computes the 2D local coordinate offset (right, forward) relative to the commander.
     * The commander stands at (0, 0). Soldiers form behind or around the commander.
     */
    public static double[] calculateLocalOffset(FormationType formation, int soldierIndex, int totalUnits) {
        int idx = soldierIndex - 1; // 0-based soldier index excluding commander
        double fileSpacing = formation.fileSpacing();
        double rankSpacing = formation.rankSpacing();

        switch (formation) {
            case PHALANX: {
                int files = Math.max(4, (int) Math.ceil(Math.sqrt(totalUnits * 2.0D)));
                int file = idx % files;
                int rank = idx / files;
                double x = (file - (files - 1) / 2.0D) * fileSpacing;
                double z = -(rank + 1) * rankSpacing; // ranks behind commander
                return new double[] { x, z };
            }
            case WEDGE: {
                // V-shaped spearhead: commander at apex, soldiers stagger back and outward
                int side = (idx % 2 == 0) ? 1 : -1;
                int rank = (idx / 2) + 1;
                double x = side * rank * fileSpacing;
                double z = -rank * rankSpacing;
                return new double[] { x, z };
            }
            case SQUARE: {
                // Hollow defensive square perimeter
                int sideLength = Math.max(3, (int) Math.ceil(totalUnits / 4.0D));
                int side = idx / sideLength;
                int posInSide = idx % sideLength;
                double half = (sideLength * fileSpacing) / 2.0D;
                double x = 0;
                double z = 0;
                switch (side % 4) {
                    case 0 -> { x = -half + posInSide * fileSpacing; z = -rankSpacing; } // front
                    case 1 -> { x = half; z = -rankSpacing - posInSide * rankSpacing; }  // right
                    case 2 -> { x = half - posInSide * fileSpacing; z = -rankSpacing - sideLength * rankSpacing; } // rear
                    case 3 -> { x = -half; z = -rankSpacing - (sideLength - posInSide) * rankSpacing; } // left
                }
                return new double[] { x, z };
            }
            case SKIRMISH: {
                // Loose dispersed grid with alternating jitter
                int files = Math.max(3, (int) Math.ceil(Math.sqrt(totalUnits)));
                int file = idx % files;
                int rank = idx / files;
                double jitter = ((idx % 3) - 1) * 0.4D;
                double x = (file - (files - 1) / 2.0D) * fileSpacing + jitter;
                double z = -(rank + 1) * rankSpacing + jitter;
                return new double[] { x, z };
            }
            case LINE:
            default: {
                int ranks = Math.max(1, formation.defaultRanks());
                int files = (int) Math.ceil((double) totalUnits / ranks);
                int file = idx % files;
                int rank = idx / files;
                double x = (file - (files - 1) / 2.0D) * fileSpacing;
                double z = -(rank + 1) * rankSpacing;
                return new double[] { x, z };
            }
        }
    }
}

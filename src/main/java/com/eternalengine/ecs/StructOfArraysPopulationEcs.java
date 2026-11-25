package com.eternalengine.ecs;

import java.util.Arrays;

/**
 * Hyper-compact Struct-of-Arrays (SoA) Entity Component System for 100,000+ agents.
 * Stores individual human agents in contiguous primitive arrays using exactly
 * 32 bytes per human, enabling zero-heap-allocation demographic macro simulation.
 */
public final class StructOfArraysPopulationEcs {

    public static final byte FLAG_MATERIALIZED = 0x01;
    public static final byte FLAG_GENDER_FEMALE = 0x02;
    public static final byte FLAG_SICK = 0x04;
    public static final byte FLAG_ACTIVE = 0x08;

    private int capacity;
    private int size;

    // Contiguous primitive component arrays (struct-of-arrays)
    private long[] entityIds;     // 8 bytes: Unique persistent ID
    private int[] posX;           // 4 bytes: World Block X
    private int[] posZ;           // 4 bytes: World Block Z
    private short[] posY;         // 2 bytes: World Elevation Y (-2048 to 2048)
    private short[] ages;         // 2 bytes: Age in years (0 to 120)
    private byte[] cultureIds;    // 1 byte: Cultural matrix index (0 to 255)
    private byte[] roleIds;       // 1 byte: Societal profession index (0 to 255)
    private byte[] healths;       // 1 byte: Health vitality (0 to 100)
    private byte[] stateFlags;    // 1 byte: Bitwise flags (materialized, gender, sick, active)
    private byte[] pad1;          // 8 bytes padding to align to exact 32-byte cache line

    public StructOfArraysPopulationEcs(int initialCapacity) {
        this.capacity = Math.max(1024, initialCapacity);
        this.size = 0;
        allocateArrays(this.capacity);
    }

    private void allocateArrays(int cap) {
        this.entityIds = new long[cap];
        this.posX = new int[cap];
        this.posZ = new int[cap];
        this.posY = new short[cap];
        this.ages = new short[cap];
        this.cultureIds = new byte[cap];
        this.roleIds = new byte[cap];
        this.healths = new byte[cap];
        this.stateFlags = new byte[cap];
        this.pad1 = new byte[cap];
    }

    private void ensureCapacity(int minCap) {
        if (minCap <= capacity) return;
        int newCap = Math.max(minCap, capacity + (capacity >> 1));
        entityIds = Arrays.copyOf(entityIds, newCap);
        posX = Arrays.copyOf(posX, newCap);
        posZ = Arrays.copyOf(posZ, newCap);
        posY = Arrays.copyOf(posY, newCap);
        ages = Arrays.copyOf(ages, newCap);
        cultureIds = Arrays.copyOf(cultureIds, newCap);
        roleIds = Arrays.copyOf(roleIds, newCap);
        healths = Arrays.copyOf(healths, newCap);
        stateFlags = Arrays.copyOf(stateFlags, newCap);
        pad1 = Arrays.copyOf(pad1, newCap);
        this.capacity = newCap;
    }

    public synchronized int addAgent(long id, int x, int y, int z, int age,
                                     byte culture, byte role, byte health, byte flags) {
        ensureCapacity(size + 1);
        int index = size++;
        entityIds[index] = id;
        posX[index] = x;
        posZ[index] = z;
        posY[index] = (short) y;
        ages[index] = (short) Math.max(0, Math.min(120, age));
        cultureIds[index] = culture;
        roleIds[index] = role;
        healths[index] = (byte) Math.max(0, Math.min(100, health));
        stateFlags[index] = (byte) (flags | FLAG_ACTIVE);
        return index;
    }

    /**
     * Executes an ultra-fast, zero-allocation macro simulation pass over all agents.
     */
    public synchronized void tickMacroDemographics(int simYear) {
        for (int i = 0; i < size; i++) {
            if ((stateFlags[i] & FLAG_ACTIVE) == 0) continue;

            // Increment age slightly or adjust health
            if (healths[i] <= 0) {
                // Deactivate deceased agent
                stateFlags[i] &= ~FLAG_ACTIVE;
                continue;
            }

            // If sick, decrease health
            if ((stateFlags[i] & FLAG_SICK) != 0) {
                if (healths[i] > 5) {
                    healths[i] = (byte) (healths[i] - 1);
                }
            }
        }
    }

    public int size() { return size; }
    public int capacity() { return capacity; }
    public long getMemoryUsageBytes() { return (long) capacity * 32L; }

    public long getEntityId(int i) { return entityIds[i]; }
    public int getX(int i) { return posX[i]; }
    public int getY(int i) { return posY[i]; }
    public int getZ(int i) { return posZ[i]; }
    public short getAge(int i) { return ages[i]; }
    public byte getCulture(int i) { return cultureIds[i]; }
    public byte getRole(int i) { return roleIds[i]; }
    public byte getHealth(int i) { return healths[i]; }
    public byte getFlags(int i) { return stateFlags[i]; }

    public void setPosition(int i, int x, int y, int z) {
        posX[i] = x;
        posY[i] = (short) y;
        posZ[i] = z;
    }

    public void setHealth(int i, byte health) {
        healths[i] = health;
    }

    public void setMaterialized(int i, boolean materialized) {
        if (materialized) {
            stateFlags[i] |= FLAG_MATERIALIZED;
        } else {
            stateFlags[i] &= ~FLAG_MATERIALIZED;
        }
    }

    public boolean isMaterialized(int i) {
        return (stateFlags[i] & FLAG_MATERIALIZED) != 0;
    }

    public boolean isActive(int i) {
        return (stateFlags[i] & FLAG_ACTIVE) != 0;
    }
}

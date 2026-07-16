package com.eternalengine.terrabridge;

import com.eternalengine.EternalEngineMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Bridge to Terra++ historical time.
 *
 * Terra++ terrain/structure refresh uses TerraTimeManager, so Eternal Engine
 * must mirror and command that same clock instead of running an unrelated year.
 */
public final class TerraTimeBridge {
    private static final Logger LOGGER = EternalEngineMod.getLogger();
    private static final String TERRA_TIME_MANAGER = "net.buildtheearth.terraplusplus.generator.historical.TerraTimeManager";
    private static final String PERSISTENT_REFRESH = "net.buildtheearth.terraplusplus.generator.historical.PersistentChunkRefreshIndex";

    private TerraTimeBridge() {
    }

    public static boolean isAvailable() {
        try {
            Class.forName(TERRA_TIME_MANAGER);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static int getCurrentYear(int fallback) {
        try {
            Class<?> cls = Class.forName(TERRA_TIME_MANAGER);
            Method method = cls.getMethod("getCurrentYear");
            Object value = method.invoke(null);
            return value instanceof Number number ? number.intValue() : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    public static int getTimeScale(int fallback) {
        try {
            Class<?> cls = Class.forName(TERRA_TIME_MANAGER);
            Method method = cls.getMethod("getTimeScale");
            Object value = method.invoke(null);
            return value instanceof Number number ? number.intValue() : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    public static int setTimeScale(int requestedScale, int fallback) {
        try {
            Class<?> cls = Class.forName(TERRA_TIME_MANAGER);
            Method set = cls.getMethod("setTimeScale", int.class);
            set.invoke(null, requestedScale);
            return getTimeScale(fallback);
        } catch (Throwable t) {
            LOGGER.debug("[TerraTimeBridge] setTimeScale failed: {}", t.getMessage());
            return fallback;
        }
    }

    public static boolean jumpToYear(MinecraftServer server, int targetYear) {
        if (server == null) {
            return false;
        }
        try {
            ServerLevel level = server.overworld();
            if (level == null) {
                return false;
            }
            int scale = Math.max(1, getTimeScale(1));
            int baseYear = readStaticInt(TERRA_TIME_MANAGER, "BASE_YEAR", -3000);
            int daysPerYear = readStaticInt(TERRA_TIME_MANAGER, "DAYS_PER_YEAR", 365);
            long yearsFromBase = (long) targetYear - baseYear;
            if (yearsFromBase < 0) {
                return false;
            }
            long worldDay = (yearsFromBase * daysPerYear) / scale;
            long currentTimeOfDay = level.getDayTime() % 24000L;
            level.setDayTime(worldDay * 24000L + currentTimeOfDay);

            Class<?> timeCls = Class.forName(TERRA_TIME_MANAGER);
            Method recalc = timeCls.getMethod("forceRecalculate", ServerLevel.class);
            recalc.invoke(null, level);

            int actualYear = getCurrentYear(targetYear);
            try {
                Class<?> refreshCls = Class.forName(PERSISTENT_REFRESH);
                Method advance = refreshCls.getMethod("advanceRefreshGeneration", ServerLevel.class, int.class);
                advance.invoke(null, level, actualYear);
            } catch (Throwable refreshError) {
                LOGGER.debug("[TerraTimeBridge] refresh generation unavailable: {}", refreshError.getMessage());
            }
            return true;
        } catch (Throwable t) {
            LOGGER.warn("[TerraTimeBridge] jumpToYear failed: {}", t.getMessage());
            return false;
        }
    }

    private static int readStaticInt(String className, String fieldName, int fallback) {
        try {
            Field field = Class.forName(className).getField(fieldName);
            Object value = field.get(null);
            return value instanceof Number number ? number.intValue() : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }
}

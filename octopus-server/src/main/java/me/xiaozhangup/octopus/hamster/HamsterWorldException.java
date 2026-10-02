package me.xiaozhangup.octopus.hamster;

import net.minecraft.CrashReport;
import net.minecraft.ReportedException;

/** Carries the original world thread through the existing crash-report path. */
public final class HamsterWorldException extends ReportedException {
    final String world;
    final String dimension;
    final String worldThread;

    public HamsterWorldException(CrashReport report, String world, String dimension, String worldThread) {
        super(report);
        this.world = world;
        this.dimension = dimension;
        this.worldThread = worldThread;
    }
}

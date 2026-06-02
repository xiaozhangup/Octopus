package me.xiaozhangup.octopus;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface JsonDataSource {
    void save(@NotNull String json, @NotNull String uuid);
    @Nullable String load(@NotNull String uuid);
}

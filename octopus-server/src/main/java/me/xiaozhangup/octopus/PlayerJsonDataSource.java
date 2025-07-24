package me.xiaozhangup.octopus;

import org.jetbrains.annotations.Nullable;

public interface PlayerJsonDataSource {
    void save(String json, String uuid);
    @Nullable String load(String uuid);
}

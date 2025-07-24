package me.xiaozhangup.octopus;

import org.jetbrains.annotations.Nullable;

public interface JsonDataSource {
    void save(String json, String uuid);
    @Nullable String load(String uuid);
}

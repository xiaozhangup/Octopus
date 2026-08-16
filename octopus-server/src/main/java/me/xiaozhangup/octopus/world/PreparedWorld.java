package me.xiaozhangup.octopus.world;

import io.papermc.paper.world.PaperWorldLoader;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.bukkit.WorldCreator;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.Nullable;

public record PreparedWorld(
    WorldCreator creator,
    @Nullable ChunkGenerator chunkGenerator,
    @Nullable BiomeProvider biomeProvider,
    ResourceKey<LevelStem> actualDimension,
    ResourceKey<Level> dimensionKey,
    RegistryAccess registryAccess,
    PrimaryLevelData primaryLevelData,
    PaperWorldLoader.LoadedWorldData loadedWorldData,
    WorldGenSettings worldGenSettings,
    LevelStem customStem,
    long biomeZoomSeed,
    @Nullable SavedDataStorage savedDataStorage
) {}

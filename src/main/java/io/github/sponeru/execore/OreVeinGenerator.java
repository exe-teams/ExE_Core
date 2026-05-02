package io.github.sponeru.execore;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import com.mojang.serialization.Codec;

public final class OreVeinGenerator extends Feature<NoneFeatureConfiguration>
{
    private static final int CHUNK_GROUP_SIZE = 3;
    private static final int VEIN_RADIUS = 23;

    public OreVeinGenerator(Codec<NoneFeatureConfiguration> codec)
    {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context)
    {
        ChunkPos chunkPos = new ChunkPos(context.origin());
        return generateChunkSlice(context.level(), chunkPos, context.level().getSeed());
    }

    private static boolean generateChunkSlice(net.minecraft.world.level.WorldGenLevel level, ChunkPos chunkPos, long levelSeed)
    {
        ChunkPos centerChunk = getCenterChunk(chunkPos);
        int centerX = centerChunk.getMinBlockX() + 8;
        int centerZ = centerChunk.getMinBlockZ() + 8;
        long seed = levelSeed ^ (((long) centerChunk.x) * 341873128712L) ^ (((long) centerChunk.z) * 132897987541L);
        boolean placedAny = false;

        for (int localX = 0; localX < 16; localX++)
        {
            int x = chunkPos.getMinBlockX() + localX;

            for (int localZ = 0; localZ < 16; localZ++)
            {
                int z = chunkPos.getMinBlockZ() + localZ;
                int dx = x - centerX;
                int dz = z - centerZ;
                double distance = Math.sqrt(dx * dx + dz * dz);
                double edgeNoise = normalizedNoise(seed, x, 0, z) * 4.0D;

                if (distance > VEIN_RADIUS + edgeNoise)
                {
                    continue;
                }

                double density = 1.0D - (distance / (VEIN_RADIUS + 4.0D));

                for (int groupIndex = 0; groupIndex < Config.veinGroups.size(); groupIndex++)
                {
                    Config.VeinGroup group = Config.veinGroups.get(groupIndex);
                    placedAny |= placeLayerColumn(level, x, z, groupIndex, group, density, seed);
                }
            }
        }

        return placedAny;
    }

    private static ChunkPos getCenterChunk(ChunkPos chunkPos)
    {
        int groupX = Math.floorDiv(chunkPos.x, CHUNK_GROUP_SIZE);
        int groupZ = Math.floorDiv(chunkPos.z, CHUNK_GROUP_SIZE);
        return new ChunkPos(groupX * CHUNK_GROUP_SIZE + 1, groupZ * CHUNK_GROUP_SIZE + 1);
    }

    private static boolean placeLayerColumn(net.minecraft.world.level.WorldGenLevel level, int x, int z, int groupIndex, Config.VeinGroup group, double density, long seed)
    {
        if (normalizedNoise(seed ^ 0x3C79AC492BA7B653L, groupIndex, 0, 0) > group.chance())
        {
            return false;
        }

        int centerY = Mth.clamp(group.y(), level.getMinBuildHeight() + 2, level.getMaxBuildHeight() - 3);
        int minY = centerY - (group.thickness() / 2);
        int maxY = minY + group.thickness() - 1;
        boolean placedAny = false;

        for (int y = minY; y <= maxY; y++)
        {
            double layerNoise = normalizedNoise(seed, x, y, z);
            double chance = 0.28D + density * 0.52D;

            if (layerNoise > chance)
            {
                continue;
            }

            BlockPos pos = new BlockPos(x, y, z);
            BlockState currentState = level.getBlockState(pos);

            if (canReplace(currentState))
            {
                Block oreBlock = group.chooseBlock(normalizedNoise(seed ^ 0x5DEECE66DL, x, y, z));
                level.setBlock(pos, oreBlock.defaultBlockState(), Block.UPDATE_CLIENTS);
                placedAny = true;
            }
        }

        return placedAny;
    }

    private static boolean canReplace(BlockState state)
    {
        return state.is(Blocks.STONE)
                || state.is(Blocks.DEEPSLATE)
                || state.is(Blocks.TUFF)
                || state.is(Blocks.GRANITE)
                || state.is(Blocks.DIORITE)
                || state.is(Blocks.ANDESITE);
    }

    private static double normalizedNoise(long seed, int x, int y, int z)
    {
        long value = seed;
        value ^= (long) x * 0x9E3779B97F4A7C15L;
        value ^= (long) y * 0xBF58476D1CE4E5B9L;
        value ^= (long) z * 0x94D049BB133111EBL;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (value >>> 11) * 0x1.0p-53;
    }
}

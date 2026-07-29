package io.github.sponeru.execore.scanner;

import io.github.sponeru.execore.ConfiguredOreBlock;
import io.github.sponeru.execore.network.ModNetwork;
import io.github.sponeru.execore.network.OreScanResultPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class OreScannerItem extends Item
{
    public static final int HORIZONTAL_RANGE = 24;
    public static final int VERTICAL_RANGE = 512;
    public static final int COOLDOWN_TICKS = 80;
    public static final int MAX_HIGHLIGHTS_PER_ORE_TYPE = 10;
    private static final int MAX_RESULTS = 5;

    public OreScannerItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide())
        {
            return InteractionResultHolder.success(stack);
        }

        ServerLevel serverLevel = (ServerLevel) level;
        ScanSummary summary = scan(serverLevel, player.blockPosition());
        List<OreHit> hits = summary.hits();

        if (player instanceof ServerPlayer serverPlayer)
        {
            ModNetwork.sendToPlayer(serverPlayer, new OreScanResultPacket(summary.highlightPositions()));
        }

        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        player.awardStat(Stats.ITEM_USED.get(this));
        level.playSound(null, player.blockPosition(), SoundEvents.SPYGLASS_USE, SoundSource.PLAYERS, 0.8F, 1.1F);

        if (hits.isEmpty())
        {
            player.displayClientMessage(Component.translatable("message.execore.ore_scanner.empty"), true);
        }
        else
        {
            showResults(player, hits);
        }

        if (!player.getAbilities().instabuild)
        {
            stack.hurtAndBreak(1, player, user -> user.broadcastBreakEvent(hand));
        }

        return InteractionResultHolder.consume(stack);
    }

    private static ScanSummary scan(ServerLevel level, BlockPos origin)
    {
        Map<Block, OreHit> hits = new IdentityHashMap<>();
        Map<Block, List<HighlightCandidate>> highlightCandidates = new IdentityHashMap<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int minY = Math.max(level.getMinBuildHeight(), origin.getY() - VERTICAL_RANGE);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, origin.getY() + VERTICAL_RANGE);

        for (int x = origin.getX() - HORIZONTAL_RANGE; x <= origin.getX() + HORIZONTAL_RANGE; x++)
        {
            for (int z = origin.getZ() - HORIZONTAL_RANGE; z <= origin.getZ() + HORIZONTAL_RANGE; z++)
            {
                if (!level.hasChunk(x >> 4, z >> 4))
                {
                    continue;
                }

                for (int y = minY; y <= maxY; y++)
                {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);

                    if (!(state.getBlock() instanceof ConfiguredOreBlock configuredOre) || !configuredOre.isDense())
                    {
                        continue;
                    }

                    Block block = state.getBlock();
                    double distanceSquared = cursor.distSqr(origin);
                    OreHit hit = hits.computeIfAbsent(block, ignored -> new OreHit(block));
                    hit.record(cursor, distanceSquared);
                    highlightCandidates.computeIfAbsent(block, ignored -> new ArrayList<>())
                            .add(new HighlightCandidate(cursor.immutable(), distanceSquared));
                }
            }
        }

        List<OreHit> sortedHits = new ArrayList<>(hits.values());
        sortedHits.sort(Comparator.comparingDouble(OreHit::nearestDistanceSquared));
        List<HighlightCandidate> selectedHighlights = new ArrayList<>();

        for (List<HighlightCandidate> candidates : highlightCandidates.values())
        {
            candidates.sort(Comparator.comparingDouble(HighlightCandidate::distanceSquared));
            selectedHighlights.addAll(candidates.stream()
                    .limit(MAX_HIGHLIGHTS_PER_ORE_TYPE)
                    .toList());
        }

        selectedHighlights.sort(Comparator.comparingDouble(HighlightCandidate::distanceSquared));
        List<BlockPos> highlightPositions = selectedHighlights.stream()
                .limit(OreScanResultPacket.MAX_HIGHLIGHT_BLOCKS)
                .map(HighlightCandidate::position)
                .toList();
        return new ScanSummary(sortedHits, highlightPositions);
    }

    private static void showResults(Player player, List<OreHit> hits)
    {
        int totalBlocks = hits.stream().mapToInt(OreHit::count).sum();
        player.displayClientMessage(Component.translatable(
                "message.execore.ore_scanner.found",
                totalBlocks,
                hits.size()).withStyle(ChatFormatting.AQUA), true);

        int shownResults = Math.min(MAX_RESULTS, hits.size());

        for (int index = 0; index < shownResults; index++)
        {
            OreHit hit = hits.get(index);
            BlockPos nearest = hit.nearest();
            int distance = (int) Math.round(Math.sqrt(hit.nearestDistanceSquared()));
            Component direction = directionFrom(player.blockPosition(), nearest);
            player.sendSystemMessage(Component.translatable(
                    "message.execore.ore_scanner.result",
                    hit.block().getName(),
                    distance,
                    direction,
                    nearest.getY(),
                    hit.count()).withStyle(index == 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        }

        if (hits.size() > shownResults)
        {
            player.sendSystemMessage(Component.translatable(
                    "message.execore.ore_scanner.more",
                    hits.size() - shownResults).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static Component directionFrom(BlockPos origin, BlockPos target)
    {
        int deltaX = target.getX() - origin.getX();
        int deltaZ = target.getZ() - origin.getZ();
        int absX = Math.abs(deltaX);
        int absZ = Math.abs(deltaZ);
        String direction;

        if (absX == 0 && absZ == 0)
        {
            direction = "here";
        }
        else if (absX > absZ * 2)
        {
            direction = deltaX >= 0 ? "east" : "west";
        }
        else if (absZ > absX * 2)
        {
            direction = deltaZ >= 0 ? "south" : "north";
        }
        else if (deltaZ < 0)
        {
            direction = deltaX >= 0 ? "north_east" : "north_west";
        }
        else
        {
            direction = deltaX >= 0 ? "south_east" : "south_west";
        }

        return Component.translatable("direction.execore." + direction);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag)
    {
        tooltip.add(Component.translatable(
                "tooltip.execore.ore_scanner.range",
                HORIZONTAL_RANGE,
                VERTICAL_RANGE).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.execore.ore_scanner.use").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "tooltip.execore.ore_scanner.highlight",
                10,
                MAX_HIGHLIGHTS_PER_ORE_TYPE).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "tooltip.execore.ore_scanner.cooldown",
                COOLDOWN_TICKS / 20).withStyle(ChatFormatting.DARK_GRAY));
    }

    private record ScanSummary(List<OreHit> hits, List<BlockPos> highlightPositions)
    {
    }

    private record HighlightCandidate(BlockPos position, double distanceSquared)
    {
    }

    private static final class OreHit
    {
        private final Block block;
        private BlockPos nearest;
        private double nearestDistanceSquared = Double.MAX_VALUE;
        private int count;

        private OreHit(Block block)
        {
            this.block = block;
        }

        private void record(BlockPos position, double distanceSquared)
        {
            count++;

            if (distanceSquared < nearestDistanceSquared)
            {
                nearest = position.immutable();
                nearestDistanceSquared = distanceSquared;
            }
        }

        private Block block()
        {
            return block;
        }

        private BlockPos nearest()
        {
            return nearest;
        }

        private double nearestDistanceSquared()
        {
            return nearestDistanceSquared;
        }

        private int count()
        {
            return count;
        }
    }
}

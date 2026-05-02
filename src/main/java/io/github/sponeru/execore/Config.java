package io.github.sponeru.execore;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Forge's config APIs
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config
{
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER
            .comment("Whether to log the dirt block on common setup")
            .define("logDirtBlock", true);

    private static final ForgeConfigSpec.IntValue MAGIC_NUMBER = BUILDER
            .comment("A magic number")
            .defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER
            .comment("What you want the introduction message to be for the magic number")
            .define("magicNumberIntroduction", "The magic number is... ");

    // a list of strings that are treated as resource locations for items
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER
            .comment("A list of items to log on common setup.")
            .defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), Config::validateItemName);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static boolean logDirtBlock;
    public static int magicNumber;
    public static String magicNumberIntroduction;
    public static Set<Item> items;
    public static List<VeinGroup> veinGroups = defaultVeinGroups();

    private static boolean validateItemName(final Object obj)
    {
        return obj instanceof final String itemName && ForgeRegistries.ITEMS.containsKey(new ResourceLocation(itemName));
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event)
    {
        logDirtBlock = LOG_DIRT_BLOCK.get();
        magicNumber = MAGIC_NUMBER.get();
        magicNumberIntroduction = MAGIC_NUMBER_INTRODUCTION.get();

        // convert the list of strings into a set of items
        items = ITEM_STRINGS.get().stream()
                .map(itemName -> ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemName)))
                .collect(Collectors.toSet());

        veinGroups = loadOreVeinConfig();
    }

    private static List<VeinGroup> loadOreVeinConfig()
    {
        Path configPath = FMLPaths.CONFIGDIR.get().resolve("execore-ore-veins.toml");

        try
        {
            if (Files.notExists(configPath))
            {
                Files.writeString(configPath, defaultOreVeinToml());
            }

            try (CommentedFileConfig config = CommentedFileConfig.builder(configPath).sync().build())
            {
                config.load();
                List<? extends UnmodifiableConfig> veinConfigs = config.get("veins");
                List<VeinGroup> groups = new ArrayList<>();

                if (veinConfigs != null)
                {
                    for (int index = 0; index < veinConfigs.size(); index++)
                    {
                        parseVeinGroup(veinConfigs.get(index), index).ifPresent(groups::add);
                    }
                }

                return groups.isEmpty() ? defaultVeinGroups() : groups;
            }
        }
        catch (Exception ignored)
        {
            return defaultVeinGroups();
        }
    }

    private static Optional<VeinGroup> parseVeinGroup(UnmodifiableConfig config, int index)
    {
        int y = readInt(config, "y", defaultY(index));
        int thickness = Math.max(1, readInt(config, "thickness", 3));
        double chance = MthLike.clamp(readDouble(config, "chance", 1.0D), 0.0D, 1.0D);
        List<? extends UnmodifiableConfig> oreConfigs = config.get("ores");
        List<WeightedBlock> ores = new ArrayList<>();

        if (oreConfigs != null)
        {
            for (UnmodifiableConfig oreConfig : oreConfigs)
            {
                String blockName = oreConfig.get("block");
                int weight = Math.max(0, readInt(oreConfig, "weight", 0));

                if (blockName == null || weight <= 0)
                {
                    continue;
                }

                ResourceLocation blockId = new ResourceLocation(blockName);

                if (!ForgeRegistries.BLOCKS.containsKey(blockId))
                {
                    continue;
                }

                Block block = ForgeRegistries.BLOCKS.getValue(blockId);

                if (block != null && block != Blocks.AIR)
                {
                    ores.add(new WeightedBlock(block, weight));
                }
            }
        }

        if (ores.isEmpty())
        {
            return Optional.empty();
        }

        return Optional.of(new VeinGroup(y, thickness, chance, ores));
    }

    private static int readInt(UnmodifiableConfig config, String key, int fallback)
    {
        Object value = config.get(key);

        if (value instanceof Number number)
        {
            return number.intValue();
        }

        return fallback;
    }

    private static double readDouble(UnmodifiableConfig config, String key, double fallback)
    {
        Object value = config.get(key);

        if (value instanceof Number number)
        {
            return number.doubleValue();
        }

        return fallback;
    }

    private static int defaultY(int index)
    {
        int[] defaults = {-24, 8, 40};
        return defaults[Math.min(index, defaults.length - 1)];
    }

    private static List<VeinGroup> defaultVeinGroups()
    {
        return List.of(
                new VeinGroup(-24, 3, 1.0D, List.of(
                        new WeightedBlock(Blocks.IRON_ORE, 72),
                        new WeightedBlock(Blocks.GOLD_ORE, 22),
                        new WeightedBlock(Blocks.DIAMOND_ORE, 6))),
                new VeinGroup(8, 3, 1.0D, List.of(
                        new WeightedBlock(Blocks.COPPER_ORE, 60),
                        new WeightedBlock(Blocks.IRON_ORE, 28),
                        new WeightedBlock(Blocks.REDSTONE_ORE, 12))),
                new VeinGroup(40, 3, 1.0D, List.of(
                        new WeightedBlock(Blocks.COAL_ORE, 56),
                        new WeightedBlock(Blocks.COPPER_ORE, 30),
                        new WeightedBlock(Blocks.EMERALD_ORE, 14))));
    }

    private static String defaultOreVeinToml()
    {
        return """
                # ExE Core ore vein groups.
                # chance: 0.0-1.0 probability that this group places blocks in a 3x3 chunk vein.
                # y: center Y level of the layer.
                # thickness: vertical thickness in blocks.
                # ores: weighted block choices used inside this group.

                [[veins]]
                chance = 1.0
                y = -24
                thickness = 3
                ores = [
                  { block = "minecraft:diamond_ore", weight = 1 },
                  { block = "minecraft:diamond_block", weight = 1 }
                ]

                [[veins]]
                chance = 1.0
                y = 8
                thickness = 3
                ores = [
                  { block = "minecraft:iron_ore", weight = 1 },
                  { block = "minecraft:iron_block", weight = 1 }
                ]
                """;
    }

    public record VeinGroup(int y, int thickness, double chance, List<WeightedBlock> ores)
    {
        public Block chooseBlock(double roll)
        {
            int totalWeight = ores.stream().mapToInt(WeightedBlock::weight).sum();
            int target = Math.min((int) (roll * totalWeight), totalWeight - 1);
            int cursor = 0;

            for (WeightedBlock ore : ores)
            {
                cursor += ore.weight();

                if (target < cursor)
                {
                    return ore.block();
                }
            }

            return ores.stream()
                    .max(Comparator.comparingInt(WeightedBlock::weight))
                    .map(WeightedBlock::block)
                    .orElse(Blocks.STONE);
        }
    }

    public record WeightedBlock(Block block, int weight)
    {
    }

    private static final class MthLike
    {
        private static double clamp(double value, double min, double max)
        {
            return Math.max(min, Math.min(max, value));
        }
    }
}

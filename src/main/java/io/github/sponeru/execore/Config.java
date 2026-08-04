package io.github.sponeru.execore;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.mojang.logging.LogUtils;
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
import org.slf4j.Logger;

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
    private static final Logger LOGGER = LogUtils.getLogger();
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

    private static final ForgeConfigSpec.BooleanValue ASTRAL_ORE_PROCESSING_ENABLED;
    private static final ForgeConfigSpec.IntValue ASTRAL_RECONSTRUCTION_OUTPUT;
    private static final ForgeConfigSpec.IntValue ASTRAL_NUCLEOSYNTHESIS_OUTPUT;
    private static final ForgeConfigSpec.IntValue ASTRAL_COMPRESSION_OUTPUT;
    private static final ForgeConfigSpec.IntValue ASTRAL_DISSOLUTION_OUTPUT;
    private static final ForgeConfigSpec.IntValue ASTRAL_WASHING_OUTPUT;
    private static final ForgeConfigSpec.IntValue ASTRAL_CRYSTALLIZING_OUTPUT;
    private static final ForgeConfigSpec.IntValue ASTRAL_INJECTING_OUTPUT;
    private static final ForgeConfigSpec.IntValue ASTRAL_PURIFYING_OUTPUT;
    private static final ForgeConfigSpec.IntValue ASTRAL_CRUSHING_OUTPUT;
    private static final ForgeConfigSpec.IntValue ASTRAL_ENRICHING_OUTPUT;

    static
    {
        BUILDER.push("astralOreProcessing");
        ASTRAL_ORE_PROCESSING_ENABLED = BUILDER
                .comment("Generate the independent ExE Core ore-processing chain for Astral Mekanism.")
                .define("enabled", true);
        ASTRAL_RECONSTRUCTION_OUTPUT = stageOutput("reconstructionOutput", 8);
        ASTRAL_NUCLEOSYNTHESIS_OUTPUT = stageOutput("nucleosynthesisOutput", 6);
        ASTRAL_COMPRESSION_OUTPUT = stageOutput("compressionOutput", 4);
        ASTRAL_DISSOLUTION_OUTPUT = stageOutput("dissolutionSlurryOutput", 100);
        ASTRAL_WASHING_OUTPUT = stageOutput("washingSlurryOutput", 100);
        ASTRAL_CRYSTALLIZING_OUTPUT = stageOutput("crystallizingOutput", 1);
        ASTRAL_INJECTING_OUTPUT = stageOutput("injectingOutput", 3);
        ASTRAL_PURIFYING_OUTPUT = stageOutput("purifyingOutput", 2);
        ASTRAL_CRUSHING_OUTPUT = stageOutput("crushingOutput", 2);
        ASTRAL_ENRICHING_OUTPUT = stageOutput("enrichingOutput", 2);
        BUILDER.pop();
    }

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static boolean logDirtBlock;
    public static int magicNumber;
    public static String magicNumberIntroduction;
    public static Set<Item> items;
    public static List<VeinGroup> veinGroups = defaultVeinGroups();
    public static AstralOreProcessing astralOreProcessing = AstralOreProcessing.defaults();

    private static ForgeConfigSpec.IntValue stageOutput(String name, int defaultValue)
    {
        return BUILDER.comment("Base output for this stage. A material's astral_multiplier is applied afterwards.")
                .defineInRange(name, defaultValue, 1, Integer.MAX_VALUE);
    }

    private static boolean validateItemName(final Object obj)
    {
        return obj instanceof final String itemName && ForgeRegistries.ITEMS.containsKey(new ResourceLocation(itemName));
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event)
    {
        if (event.getConfig().getSpec() != SPEC)
        {
            return;
        }

        logDirtBlock = LOG_DIRT_BLOCK.get();
        magicNumber = MAGIC_NUMBER.get();
        magicNumberIntroduction = MAGIC_NUMBER_INTRODUCTION.get();

        // convert the list of strings into a set of items
        items = ITEM_STRINGS.get().stream()
                .map(itemName -> ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemName)))
                .collect(Collectors.toSet());

        veinGroups = loadOreVeinConfig();
        astralOreProcessing = new AstralOreProcessing(
                ASTRAL_ORE_PROCESSING_ENABLED.get(),
                ASTRAL_RECONSTRUCTION_OUTPUT.get(),
                ASTRAL_NUCLEOSYNTHESIS_OUTPUT.get(),
                ASTRAL_COMPRESSION_OUTPUT.get(),
                ASTRAL_DISSOLUTION_OUTPUT.get(),
                ASTRAL_WASHING_OUTPUT.get(),
                ASTRAL_CRYSTALLIZING_OUTPUT.get(),
                ASTRAL_INJECTING_OUTPUT.get(),
                ASTRAL_PURIFYING_OUTPUT.get(),
                ASTRAL_CRUSHING_OUTPUT.get(),
                ASTRAL_ENRICHING_OUTPUT.get());

        try
        {
            GeneratedAssetPack.refreshIfGenerated(MaterialConfig.load());
        }
        catch (Exception exception)
        {
            LOGGER.error("Failed to refresh ExE Core generated assets after loading config", exception);
        }
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

    public record AstralOreProcessing(boolean enabled, int reconstructionOutput, int nucleosynthesisOutput,
                                      int compressionOutput, int dissolutionOutput, int washingOutput,
                                      int crystallizingOutput, int injectingOutput, int purifyingOutput,
                                      int crushingOutput, int enrichingOutput)
    {
        private static AstralOreProcessing defaults()
        {
            return new AstralOreProcessing(true, 8, 6, 4, 100, 100, 1, 3, 2, 2, 2);
        }
    }

    private static final class MthLike
    {
        private static double clamp(double value, double min, double max)
        {
            return Math.max(min, Math.min(max, value));
        }
    }
}

package io.github.sponeru.execore;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import mekanism.api.chemical.slurry.Slurry;
import mekanism.api.chemical.slurry.SlurryBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Generates the standard Mekanism ore-processing chain only where another mod has not supplied it. */
public final class MekanismOreProcessingPack
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, ProcessingMaterial> MATERIALS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, RegistryObject<Item>> OWN_ITEMS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, Integer> OWN_ITEM_COLORS = new LinkedHashMap<>();
    private static boolean prepared;

    private MekanismOreProcessingPack()
    {
    }

    public static synchronized void prepare(List<MaterialConfig.MaterialDefinition> materials)
    {
        if (prepared)
        {
            return;
        }

        prepared = true;
        InstalledItemTags tags = new InstalledItemTags();

        for (MaterialConfig.MaterialDefinition material : materials)
        {
            if (!material.generateMekanismProcessing() || MATERIALS.containsKey(material.id()))
            {
                continue;
            }

            EnumMap<Stage, ResourceLocation> items = new EnumMap<>(Stage.class);
            Set<Stage> ownedStages = new LinkedHashSet<>();

            for (Stage stage : Stage.values())
            {
                ResourceLocation selected = tags.firstItem(stage.tag(material.id()))
                        .orElseGet(() -> new ResourceLocation(ExampleMod.MODID, stage.itemPrefix + material.id()));
                items.put(stage, selected);

                if (ExampleMod.MODID.equals(selected.getNamespace()))
                {
                    registerFallbackItem(selected, material.color());
                    ownedStages.add(stage);
                }
            }

            ResourceLocation crystal = items.get(Stage.CRYSTAL);
            boolean ownSlurries = ownedStages.contains(Stage.CRYSTAL);
            ResourceLocation dirtySlurry = new ResourceLocation(
                    ownSlurries ? ExampleMod.MODID : crystal.getNamespace(), "dirty_" + material.id());
            ResourceLocation cleanSlurry = new ResourceLocation(
                    ownSlurries ? ExampleMod.MODID : crystal.getNamespace(), "clean_" + material.id());

            if (ownSlurries)
            {
                ExampleMod.SLURRIES.register(dirtySlurry.getPath(),
                        () -> new Slurry(SlurryBuilder.dirty().tint(material.color())));
                ExampleMod.SLURRIES.register(cleanSlurry.getPath(),
                        () -> new Slurry(SlurryBuilder.clean().tint(material.color())));
            }

            ResourceLocation finalProduct = tags.firstItem(new ResourceLocation("forge", "ingots/" + material.id()))
                    .or(() -> tags.firstItem(new ResourceLocation("forge", "gems/" + material.id())))
                    .orElse(material.dropId());
            MATERIALS.put(material.id(), new ProcessingMaterial(
                    material, items, ownedStages, dirtySlurry, cleanSlurry, ownSlurries, finalProduct));
        }
    }

    private static void registerFallbackItem(ResourceLocation id, int color)
    {
        if (OWN_ITEMS.containsKey(id))
        {
            return;
        }

        RegistryObject<Item> item = ExampleMod.ITEMS.register(id.getPath(), () -> new Item(new Item.Properties()));
        OWN_ITEMS.put(id, item);
        OWN_ITEM_COLORS.put(id, color);
    }

    public static void addCreative(BuildCreativeModeTabContentsEvent event)
    {
        OWN_ITEMS.values().forEach(event::accept);
    }

    public static void registerItemColors(RegisterColorHandlersEvent.Item event)
    {
        OWN_ITEMS.forEach((id, item) -> event.register((stack, tintIndex) ->
                tintIndex == 0 ? OWN_ITEM_COLORS.getOrDefault(id, 0xFFFFFF) : 0xFFFFFF, item.get()));
    }

    public static void generate(Path root) throws IOException
    {
        Path itemModels = root.resolve("assets").resolve(ExampleMod.MODID).resolve("models").resolve("item");
        Map<String, String> english = new LinkedHashMap<>();
        Map<String, String> japanese = new LinkedHashMap<>();
        Files.createDirectories(itemModels);

        for (ProcessingMaterial processing : MATERIALS.values())
        {
            if (processing.hasFallback())
            {
                writeFallbackTags(root, processing);
                writeFallbackModels(itemModels, processing);
                writeRecipes(root, processing);
                addLanguageEntries(english, japanese, processing);
            }

            if (processing.material.generateDenseOre())
            {
                writeDenseRecipes(root, processing);
            }
        }

        GeneratedLanguageWriter.write(root, "en_us", english);
        GeneratedLanguageWriter.write(root, "ja_jp", japanese);
    }

    private static void writeFallbackTags(Path root, ProcessingMaterial processing) throws IOException
    {
        for (Stage stage : processing.ownedStages)
        {
            ResourceLocation tag = stage.tag(processing.material.id());
            Path path = root.resolve("data").resolve(tag.getNamespace()).resolve("tags").resolve("items")
                    .resolve(tag.getPath() + ".json");
            Files.createDirectories(path.getParent());
            Files.writeString(path, tagJson(processing.items.get(stage)), StandardCharsets.UTF_8);
        }
    }

    private static void writeFallbackModels(Path itemModels, ProcessingMaterial processing) throws IOException
    {
        for (Stage stage : processing.ownedStages)
        {
            ResourceLocation item = processing.items.get(stage);
            Files.writeString(itemModels.resolve(item.getPath() + ".json"),
                    stage.itemModelJson(),
                    StandardCharsets.UTF_8);
        }
    }

    private static void writeRecipes(Path root, ProcessingMaterial processing) throws IOException
    {
        String id = processing.material.id();
        Path recipeRoot = root.resolve("data").resolve(ExampleMod.MODID).resolve("recipes")
                .resolve("mekanism_processing").resolve(id);
        Files.createDirectories(recipeRoot);
        String oreTag = "forge:ores/" + id;
        String crystalTag = Stage.CRYSTAL.tag(id).toString();
        String shardTag = Stage.SHARD.tag(id).toString();
        String clumpTag = Stage.CLUMP.tag(id).toString();
        String dirtyDustTag = Stage.DIRTY_DUST.tag(id).toString();
        String dustTag = Stage.DUST.tag(id).toString();

        Files.writeString(recipeRoot.resolve("dissolution_from_ore.json"), dissolutionRecipe(
                oreTag, processing.dirtySlurry.toString()), StandardCharsets.UTF_8);
        Files.writeString(recipeRoot.resolve("washing.json"), washingRecipe(
                processing.dirtySlurry.toString(), processing.cleanSlurry.toString()), StandardCharsets.UTF_8);
        Files.writeString(recipeRoot.resolve("crystal_from_slurry.json"), crystallizingRecipe(
                processing.cleanSlurry.toString(), processing.items.get(Stage.CRYSTAL).toString()), StandardCharsets.UTF_8);
        Files.writeString(recipeRoot.resolve("shard_from_ore.json"), chemicalItemRecipe(
                "mekanism:injecting", oreTag, "mekanism:hydrogen_chloride",
                processing.items.get(Stage.SHARD).toString(), 4), StandardCharsets.UTF_8);
        Files.writeString(recipeRoot.resolve("shard_from_crystal.json"), chemicalItemRecipe(
                "mekanism:injecting", crystalTag, "mekanism:hydrogen_chloride",
                processing.items.get(Stage.SHARD).toString(), 1), StandardCharsets.UTF_8);
        Files.writeString(recipeRoot.resolve("clump_from_ore.json"), chemicalItemRecipe(
                "mekanism:purifying", oreTag, "mekanism:oxygen",
                processing.items.get(Stage.CLUMP).toString(), 3), StandardCharsets.UTF_8);
        Files.writeString(recipeRoot.resolve("clump_from_shard.json"), chemicalItemRecipe(
                "mekanism:purifying", shardTag, "mekanism:oxygen",
                processing.items.get(Stage.CLUMP).toString(), 1), StandardCharsets.UTF_8);
        Files.writeString(recipeRoot.resolve("dirty_dust_from_clump.json"), itemRecipe(
                "mekanism:crushing", clumpTag, processing.items.get(Stage.DIRTY_DUST).toString(), 1), StandardCharsets.UTF_8);
        Files.writeString(recipeRoot.resolve("dust_from_dirty_dust.json"), itemRecipe(
                "mekanism:enriching", dirtyDustTag, processing.items.get(Stage.DUST).toString(), 1), StandardCharsets.UTF_8);
        Files.writeString(recipeRoot.resolve("dust_from_ore.json"), itemRecipe(
                "mekanism:enriching", oreTag, processing.items.get(Stage.DUST).toString(), 2), StandardCharsets.UTF_8);
        Files.writeString(recipeRoot.resolve("smelting_from_dust.json"), cookingRecipe(
                "minecraft:smelting", dustTag, processing.finalProduct.toString(), 200), StandardCharsets.UTF_8);
        Files.writeString(recipeRoot.resolve("blasting_from_dust.json"), cookingRecipe(
                "minecraft:blasting", dustTag, processing.finalProduct.toString(), 100), StandardCharsets.UTF_8);
    }

    private static void writeDenseRecipes(Path root, ProcessingMaterial processing) throws IOException
    {
        String id = processing.material.id();
        int factor = processing.material.denseFactor();
        String denseTag = ExampleMod.MODID + ":dense_ores/" + id;
        Path recipeRoot = root.resolve("data").resolve(ExampleMod.MODID).resolve("recipes")
                .resolve("mekanism_processing").resolve(id);
        Files.createDirectories(recipeRoot);

        Files.writeString(recipeRoot.resolve("dense_dissolution.json"), dissolutionRecipe(
                denseTag, processing.dirtySlurry.toString(), scaledDense(1_000, factor)), StandardCharsets.UTF_8);
        Files.writeString(recipeRoot.resolve("dense_shard.json"), chemicalItemRecipe(
                "mekanism:injecting", denseTag, "mekanism:hydrogen_chloride",
                processing.items.get(Stage.SHARD).toString(), scaledDense(4, factor)), StandardCharsets.UTF_8);
        Files.writeString(recipeRoot.resolve("dense_clump.json"), chemicalItemRecipe(
                "mekanism:purifying", denseTag, "mekanism:oxygen",
                processing.items.get(Stage.CLUMP).toString(), scaledDense(3, factor)), StandardCharsets.UTF_8);
        Files.writeString(recipeRoot.resolve("dense_dust.json"), itemRecipe(
                "mekanism:enriching", denseTag, processing.items.get(Stage.DUST).toString(),
                scaledDense(2, factor)), StandardCharsets.UTF_8);
    }

    static int scaledDense(int base, int factor)
    {
        long scaled = (long) base * Math.max(1, factor);
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, scaled));
    }

    static String chemicalItemRecipe(String type, String inputTag, String gas, String outputItem, int outputCount)
    {
        return """
                {
                  "type": "%s",
                  "chemicalInput": {"amount": 1, "gas": "%s"},
                  "itemInput": {"ingredient": {"tag": "%s"}},
                  "output": {"count": %d, "item": "%s"}
                }
                """.formatted(type, gas, inputTag, outputCount, outputItem);
    }

    static String itemRecipe(String type, String inputTag, String outputItem, int outputCount)
    {
        return """
                {
                  "type": "%s",
                  "input": {"ingredient": {"tag": "%s"}},
                  "output": {"count": %d, "item": "%s"}
                }
                """.formatted(type, inputTag, outputCount, outputItem);
    }

    static String dissolutionRecipe(String inputTag, String outputSlurry)
    {
        return dissolutionRecipe(inputTag, outputSlurry, 1_000);
    }

    static String dissolutionRecipe(String inputTag, String outputSlurry, int outputAmount)
    {
        return """
                {
                  "type": "mekanism:dissolution",
                  "gasInput": {"amount": 1, "gas": "mekanism:sulfuric_acid"},
                  "itemInput": {"ingredient": {"tag": "%s"}},
                  "output": {"amount": %d, "chemicalType": "slurry", "slurry": "%s"}
                }
                """.formatted(inputTag, outputAmount, outputSlurry);
    }

    static String washingRecipe(String inputSlurry, String outputSlurry)
    {
        return """
                {
                  "type": "mekanism:washing",
                  "fluidInput": {"amount": 5, "tag": "minecraft:water"},
                  "slurryInput": {"amount": 1, "slurry": "%s"},
                  "output": {"amount": 1, "slurry": "%s"}
                }
                """.formatted(inputSlurry, outputSlurry);
    }

    static String crystallizingRecipe(String inputSlurry, String outputItem)
    {
        return """
                {
                  "type": "mekanism:crystallizing",
                  "chemicalType": "slurry",
                  "input": {"amount": 200, "slurry": "%s"},
                  "output": {"item": "%s"}
                }
                """.formatted(inputSlurry, outputItem);
    }

    static String cookingRecipe(String type, String inputTag, String outputItem, int cookingTime)
    {
        return """
                {
                  "type": "%s",
                  "category": "misc",
                  "cookingtime": %d,
                  "experience": 0.0,
                  "ingredient": {"tag": "%s"},
                  "result": "%s"
                }
                """.formatted(type, cookingTime, inputTag, outputItem);
    }

    private static String tagJson(ResourceLocation item)
    {
        return "{\n  \"replace\": false,\n  \"values\": [\n    \"" + item + "\"\n  ]\n}\n";
    }

    private static void addLanguageEntries(Map<String, String> english, Map<String, String> japanese,
                                           ProcessingMaterial processing)
    {
        String englishMaterial = displayName(processing.material.id());
        String japaneseMaterial = japaneseMaterialName(processing.material.id(), englishMaterial);

        for (Stage stage : processing.ownedStages)
        {
            ResourceLocation item = processing.items.get(stage);
            english.put("item." + ExampleMod.MODID + "." + item.getPath(), stage.englishName(englishMaterial));
            japanese.put("item." + ExampleMod.MODID + "." + item.getPath(), stage.japaneseName(japaneseMaterial));
        }

        if (processing.ownSlurries)
        {
            english.put("slurry.execore." + processing.dirtySlurry.getPath(), "Dirty " + englishMaterial + " Slurry");
            english.put("slurry.execore." + processing.cleanSlurry.getPath(), "Clean " + englishMaterial + " Slurry");
            japanese.put("slurry.execore." + processing.dirtySlurry.getPath(), "汚れた" + japaneseMaterial + "のスラリー");
            japanese.put("slurry.execore." + processing.cleanSlurry.getPath(), "きれいな" + japaneseMaterial + "のスラリー");
        }
    }

    private static String displayName(String id)
    {
        StringBuilder result = new StringBuilder();

        for (String word : id.split("_"))
        {
            if (!result.isEmpty())
            {
                result.append(' ');
            }
            result.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }

        return result.toString();
    }

    private static String japaneseMaterialName(String id, String fallback)
    {
        return switch (id)
        {
            case "iron" -> "鉄";
            case "copper" -> "銅";
            case "gold" -> "金";
            case "redstone" -> "レッドストーン";
            case "lapis" -> "ラピスラズリ";
            case "emerald" -> "エメラルド";
            case "diamond" -> "ダイヤモンド";
            case "coal" -> "石炭";
            case "quartz" -> "ネザークォーツ";
            default -> fallback;
        };
    }

    private enum Stage
    {
        CRYSTAL("mekanism", "crystals", "crystal_", " Crystal", "の結晶"),
        SHARD("mekanism", "shards", "shard_", " Shard", "の欠片"),
        CLUMP("mekanism", "clumps", "clump_", " Clump", "の塊"),
        DIRTY_DUST("mekanism", "dirty_dusts", "dirty_dust_", " Dirty Dust", "の汚れた粉"),
        DUST("forge", "dusts", "dust_", " Dust", "の粉");

        private final String namespace;
        private final String tagFolder;
        private final String itemPrefix;
        private final String englishSuffix;
        private final String japaneseSuffix;

        Stage(String namespace, String tagFolder, String itemPrefix, String englishSuffix, String japaneseSuffix)
        {
            this.namespace = namespace;
            this.tagFolder = tagFolder;
            this.itemPrefix = itemPrefix;
            this.englishSuffix = englishSuffix;
            this.japaneseSuffix = japaneseSuffix;
        }

        private ResourceLocation tag(String material)
        {
            return new ResourceLocation(namespace, tagFolder + "/" + material);
        }

        private String englishName(String material)
        {
            return this == DIRTY_DUST ? "Dirty " + material + " Dust" : material + englishSuffix;
        }

        private String japaneseName(String material)
        {
            return material + japaneseSuffix;
        }

        private String itemModelJson()
        {
            String texture = switch (this)
            {
                case CRYSTAL -> "crystal";
                case SHARD -> "shard";
                case CLUMP -> "clump";
                case DIRTY_DUST -> "dirty_dust";
                case DUST -> "dust";
            };
            return "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\""
                    + ExampleMod.MODID + ":item/mekanism_processing/" + texture + "\"}}\n";
        }
    }

    private record ProcessingMaterial(MaterialConfig.MaterialDefinition material,
                                      EnumMap<Stage, ResourceLocation> items,
                                      Set<Stage> ownedStages,
                                      ResourceLocation dirtySlurry,
                                      ResourceLocation cleanSlurry,
                                      boolean ownSlurries,
                                      ResourceLocation finalProduct)
    {
        private boolean hasFallback()
        {
            return ownSlurries || !ownedStages.isEmpty();
        }
    }

    static final class InstalledItemTags
    {
        private final Map<ResourceLocation, List<TagEntry>> cache = new HashMap<>();

        InstalledItemTags()
        {
        }

        InstalledItemTags(Map<ResourceLocation, List<String>> definitions)
        {
            definitions.forEach((tag, values) -> cache.put(tag, values.stream().map(raw -> {
                boolean tagReference = raw.startsWith("#");
                return new TagEntry(new ResourceLocation(tagReference ? raw.substring(1) : raw), tagReference);
            }).toList()));
        }

        Optional<ResourceLocation> firstItem(ResourceLocation tag)
        {
            return firstItem(tag, new HashSet<>());
        }

        private Optional<ResourceLocation> firstItem(ResourceLocation tag, Set<ResourceLocation> visiting)
        {
            if (!visiting.add(tag))
            {
                return Optional.empty();
            }

            for (TagEntry entry : cache.computeIfAbsent(tag, this::load))
            {
                if (entry.tagReference)
                {
                    Optional<ResourceLocation> nested = firstItem(entry.id, visiting);
                    if (nested.isPresent())
                    {
                        return nested;
                    }
                }
                else
                {
                    return Optional.of(entry.id);
                }
            }

            visiting.remove(tag);
            return Optional.empty();
        }

        private List<TagEntry> load(ResourceLocation tag)
        {
            List<TagEntry> entries = new ArrayList<>();
            ModList.get().forEachModFile(modFile -> {
                Path path = modFile.findResource("data", tag.getNamespace(), "tags", "items", tag.getPath() + ".json");

                if (!Files.isRegularFile(path))
                {
                    return;
                }

                try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8))
                {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

                    if (json.has("replace") && json.get("replace").getAsBoolean())
                    {
                        entries.clear();
                    }

                    for (JsonElement value : json.getAsJsonArray("values"))
                    {
                        String raw = value.isJsonPrimitive()
                                ? value.getAsString()
                                : value.getAsJsonObject().get("id").getAsString();
                        boolean tagReference = raw.startsWith("#");
                        entries.add(new TagEntry(new ResourceLocation(tagReference ? raw.substring(1) : raw), tagReference));
                    }
                }
                catch (Exception exception)
                {
                    LOGGER.warn("Failed to read installed item tag {} from {}", tag, path, exception);
                }
            });
            return List.copyOf(entries);
        }
    }

    private record TagEntry(ResourceLocation id, boolean tagReference)
    {
    }
}

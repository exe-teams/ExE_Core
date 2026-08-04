package io.github.sponeru.execore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Generates ExE Core-owned data resources that target Astral Mekanism's public recipe contract.
 * This is an independent implementation and contains no Astral Mekanism source code.
 */
public final class AstralOreProcessingPack
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String[] ITEM_STAGES = {
            "reconstructed", "enriched", "sparkling", "shining_crystal",
            "shining_shard", "shining_clump", "shining_dust"
    };

    private AstralOreProcessingPack()
    {
    }

    public static void generate(Path root, List<MaterialConfig.MaterialDefinition> materials) throws IOException
    {
        if (!Config.astralOreProcessing.enabled())
        {
            return;
        }

        Path dataRoot = root.resolve("data").resolve(ExampleMod.MODID);
        Path recipeRoot = dataRoot.resolve("recipes").resolve("astral_processing");
        Path feedstockTags = dataRoot.resolve("tags").resolve("items")
                .resolve("astral_processing").resolve("feedstocks");
        Path itemModels = root.resolve("assets").resolve(ExampleMod.MODID).resolve("models").resolve("item");
        Map<String, String> english = new LinkedHashMap<>();
        Map<String, String> japanese = new LinkedHashMap<>();

        Files.createDirectories(recipeRoot);
        Files.createDirectories(feedstockTags);
        Files.createDirectories(itemModels);

        for (MaterialConfig.MaterialDefinition material : materials)
        {
            if (!material.generateAstralProcessing() || !ForgeRegistries.ITEMS.containsKey(material.dropId()))
            {
                continue;
            }

            boolean nativeProcessing = AstralShiningResolver.hasNativeProcessing(material.id());
            Path materialRoot = recipeRoot.resolve(material.id());

            if (!nativeProcessing)
            {
                writeFeedstockTag(feedstockTags.resolve(material.id() + ".json"), material);
                writeItemModels(itemModels, material.id());
                writeRecipes(materialRoot, material);
                addLanguageEntries(english, japanese, material);
            }

            if (material.generateDenseOre())
            {
                writeDenseRecipes(materialRoot, material, nativeProcessing);
            }
        }

        writeLanguage(root, "en_us", english);
        writeLanguage(root, "ja_jp", japanese);

        try
        {
            writeNativeRecipeOverrides(root, materials);
        }
        catch (Exception exception)
        {
            LOGGER.error("Failed to override Astral Mekanism native processing recipes", exception);
        }
    }

    private static void writeNativeRecipeOverrides(
            Path root, List<MaterialConfig.MaterialDefinition> materials) throws IOException
    {
        var astralMod = ModList.get().getModFileById(AstralShiningResolver.ASTRAL_MOD_ID);

        if (astralMod == null)
        {
            return;
        }

        Path sourceRoot = astralMod.getFile().findResource(
                "data", AstralShiningResolver.ASTRAL_MOD_ID, "recipes", "unique_processing");

        if (!Files.isDirectory(sourceRoot))
        {
            return;
        }

        Path targetRoot = root.resolve("data").resolve(AstralShiningResolver.ASTRAL_MOD_ID)
                .resolve("recipes").resolve("unique_processing");
        writeNativeRecipeOverrides(
                sourceRoot, targetRoot, materials, Config.astralOreProcessing);
    }

    static int writeNativeRecipeOverrides(
            Path sourceRoot, Path targetRoot, List<MaterialConfig.MaterialDefinition> materials,
            Config.AstralOreProcessing values) throws IOException
    {
        if (!Files.isDirectory(sourceRoot))
        {
            return 0;
        }

        Map<String, Double> multipliers = new LinkedHashMap<>();
        materials.forEach(material -> multipliers.put(
                AstralShiningResolver.nativeMaterialId(material.id()), material.astralMultiplier()));
        int written = 0;

        try (Stream<Path> paths = Files.walk(sourceRoot))
        {
            for (Path source : paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json")).toList())
            {
                Path relative = sourceRoot.relativize(source);

                if (relative.getNameCount() < 2)
                {
                    continue;
                }

                String fileName = source.getFileName().toString();
                String stage = fileName.substring(0, fileName.length() - ".json".length());
                int baseOutput = nativeStageOutput(stage, values);

                if (baseOutput < 1)
                {
                    continue;
                }

                String materialId = relative.getName(0).toString();
                int output = scaled(baseOutput, multipliers.getOrDefault(materialId, 1.0D));
                JsonObject recipe;

                try (var reader = Files.newBufferedReader(source, StandardCharsets.UTF_8))
                {
                    recipe = JsonParser.parseReader(reader).getAsJsonObject();
                }

                if (!applyNativeOutput(recipe, stage, output))
                {
                    continue;
                }

                Path target = targetRoot.resolve(relative.toString());
                Files.createDirectories(target.getParent());
                Files.writeString(target, GSON.toJson(recipe) + "\n", StandardCharsets.UTF_8);
                written++;
            }
        }

        return written;
    }

    static boolean applyNativeOutput(JsonObject recipe, String stage, int output)
    {
        String outputKey = "reconstruction".equals(stage) ? "itemOutput" : "output";

        if (!recipe.has(outputKey) || !recipe.get(outputKey).isJsonObject())
        {
            return false;
        }

        JsonObject result = recipe.getAsJsonObject(outputKey);
        String amountKey = "dissolution".equals(stage) || "washing".equals(stage) ? "amount" : "count";
        result.addProperty(amountKey, output);
        return true;
    }

    private static int nativeStageOutput(String stage, Config.AstralOreProcessing values)
    {
        return switch (stage)
        {
            case "reconstruction" -> values.reconstructionOutput();
            case "nucleosynthesizing" -> values.nucleosynthesisOutput();
            case "compressing" -> values.compressionOutput();
            case "dissolution" -> values.dissolutionOutput();
            case "washing" -> values.washingOutput();
            case "crystallizing" -> values.crystallizingOutput();
            case "injecting" -> values.injectingOutput();
            case "purifying" -> values.purifyingOutput();
            case "crushing" -> values.crushingOutput();
            case "enriching" -> values.enrichingOutput();
            default -> 0;
        };
    }

    private static void writeFeedstockTag(Path path, MaterialConfig.MaterialDefinition material) throws IOException
    {
        Set<String> values = new LinkedHashSet<>();

        if (material.generateRawOre())
        {
            values.add(ExampleMod.MODID + ":raw_" + material.id());
        }

        if (material.generateOre())
        {
            values.add(ExampleMod.MODID + ":" + material.id() + "_ore");
            values.add(ExampleMod.MODID + ":deepslate_" + material.id() + "_ore");
        }

        values.add(material.dropId().toString());
        Files.writeString(path, tagJson(values));
    }

    private static void writeItemModels(Path itemModels, String materialId) throws IOException
    {
        for (String stage : ITEM_STAGES)
        {
            String itemId = itemId(stage, materialId);
            if (processingItemLocation(stage, materialId).getNamespace().equals(ExampleMod.MODID))
            {
                Files.writeString(itemModels.resolve(itemId + ".json"), itemModelJson(stage));
            }
        }
    }

    static String itemModelJson(String stage)
    {
        String texture = switch (stage)
        {
            case "reconstructed" -> "reconstructed_ore";
            case "enriched" -> "enriched_ore";
            case "sparkling" -> "sparkling_ore";
            case "shining_crystal" -> "shining_crystal";
            case "shining_shard" -> "shining_shard";
            case "shining_clump" -> "shining_clump";
            case "shining_dust" -> "shining_dust";
            default -> throw new IllegalArgumentException("Unknown Astral processing stage: " + stage);
        };
        String base = ExampleMod.MODID + ":item/astral_processing/" + texture;

        if (!stage.startsWith("shining_"))
        {
            return "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"" + base + "\"}}\n";
        }

        return "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"" + base
                + "\",\"layer1\":\"" + base + "_glow\"}}\n";
    }

    private static void writeRecipes(Path materialRoot, MaterialConfig.MaterialDefinition material) throws IOException
    {
        Files.createDirectories(materialRoot);
        Config.AstralOreProcessing values = Config.astralOreProcessing;
        String id = material.id();
        String feedstock = ExampleMod.MODID + ":astral_processing/feedstocks/" + id;
        String reconstructed = processingItemLocation("reconstructed", id).toString();
        String enriched = processingItemLocation("enriched", id).toString();
        String sparkling = processingItemLocation("sparkling", id).toString();
        String crystal = processingItemLocation("shining_crystal", id).toString();
        String shard = processingItemLocation("shining_shard", id).toString();
        String clump = processingItemLocation("shining_clump", id).toString();
        String dust = processingItemLocation("shining_dust", id).toString();
        String specificSlurry = location("astral_specific_" + id + "_slurry");
        String shiningSlurry = location("astral_shining_" + id + "_slurry");
        String reconstructedInputs = itemInputs(feedstock, reconstructed);
        String enrichedInputs = itemInputs(feedstock, enriched);
        String sparklingInputs = itemInputs(feedstock, sparkling);
        String crystalInputs = itemInputs(feedstock, crystal);
        String shardInputs = itemInputs(feedstock, shard);

        Files.writeString(materialRoot.resolve("reconstruction.json"), reconstructionRecipe(
                feedstock, reconstructed, itemOutput(values.reconstructionOutput(), material)));
        Files.writeString(materialRoot.resolve("nucleosynthesizing.json"), nucleosynthesizingRecipeWithInputs(
                reconstructedInputs, enriched, itemOutput(values.nucleosynthesisOutput(), material)));
        Files.writeString(materialRoot.resolve("compressing.json"), chemicalItemRecipeWithInputs(
                "mekanism:compressing", enrichedInputs, "astral_mekanism:sparkling_singularity_rivulet",
                sparkling, itemOutput(values.compressionOutput(), material)));
        Files.writeString(materialRoot.resolve("dissolution.json"), dissolutionRecipeWithInputs(
                sparklingInputs, specificSlurry, chemicalOutput(values.dissolutionOutput(), material)));
        Files.writeString(materialRoot.resolve("washing.json"), washingRecipe(
                specificSlurry, shiningSlurry, chemicalOutput(values.washingOutput(), material)));
        Files.writeString(materialRoot.resolve("crystallizing.json"), crystallizingRecipe(
                shiningSlurry, crystal, itemOutput(values.crystallizingOutput(), material)));
        Files.writeString(materialRoot.resolve("injecting.json"), chemicalItemRecipeWithInputs(
                "mekanism:injecting", crystalInputs, "astral_mekanism:aqua_regia",
                shard, itemOutput(values.injectingOutput(), material)));
        Files.writeString(materialRoot.resolve("purifying.json"), chemicalItemRecipeWithInputs(
                "mekanism:purifying", shardInputs, "mekanismelements:nitric_acid",
                clump, itemOutput(values.purifyingOutput(), material)));
        Files.writeString(materialRoot.resolve("crushing.json"), itemRecipe(
                "mekanism:crushing", clump, dust, itemOutput(values.crushingOutput(), material)));
        Files.writeString(materialRoot.resolve("enriching.json"), itemRecipe(
                "mekanism:enriching", dust, material.astralOutputId().toString(),
                itemOutput(values.enrichingOutput(), material)));
    }

    private static void writeDenseRecipes(Path materialRoot, MaterialConfig.MaterialDefinition material,
                                          boolean nativeProcessing) throws IOException
    {
        Files.createDirectories(materialRoot);
        Config.AstralOreProcessing values = Config.astralOreProcessing;
        String id = material.id();
        String denseFeedstock = ExampleMod.MODID + ":dense_ores/" + id;
        String reconstructed = processingItemLocation("reconstructed", id, nativeProcessing).toString();

        Files.writeString(materialRoot.resolve("dense_reconstruction.json"), reconstructionRecipe(
                denseFeedstock, reconstructed, denseItemOutput(values.reconstructionOutput(), material)));

        String denseInput = tagIngredient(denseFeedstock);
        String enriched = processingItemLocation("enriched", id, nativeProcessing).toString();
        String sparkling = processingItemLocation("sparkling", id, nativeProcessing).toString();
        String shard = processingItemLocation("shining_shard", id, nativeProcessing).toString();
        String clump = processingItemLocation("shining_clump", id, nativeProcessing).toString();
        String specificSlurry = nativeProcessing
                ? nativeSlurry(id, false)
                : location("astral_specific_" + id + "_slurry");

        Files.writeString(materialRoot.resolve("dense_nucleosynthesizing.json"), nucleosynthesizingRecipeWithInputs(
                denseInput, enriched, denseItemOutput(values.nucleosynthesisOutput(), material)));
        Files.writeString(materialRoot.resolve("dense_compressing.json"), chemicalItemRecipeWithInputs(
                "mekanism:compressing", denseInput, "astral_mekanism:sparkling_singularity_rivulet",
                sparkling, denseItemOutput(values.compressionOutput(), material)));
        Files.writeString(materialRoot.resolve("dense_dissolution.json"), dissolutionRecipeWithInputs(
                denseInput, specificSlurry, denseChemicalOutput(values.dissolutionOutput(), material)));
        Files.writeString(materialRoot.resolve("dense_injecting.json"), chemicalItemRecipeWithInputs(
                "mekanism:injecting", denseInput, "astral_mekanism:aqua_regia",
                shard, denseItemOutput(values.injectingOutput(), material)));
        Files.writeString(materialRoot.resolve("dense_purifying.json"), chemicalItemRecipeWithInputs(
                "mekanism:purifying", denseInput, "mekanismelements:nitric_acid",
                clump, denseItemOutput(values.purifyingOutput(), material)));
    }

    private static String reconstructionRecipe(String inputTag, String outputItem, int outputCount)
    {
        return """
                {
                  "type": "astral_mekanism:reconstruction",
                  "duration": 200,
                  "fluidInput": {"amount": 1, "fluid": "astral_mekanism:refined_astral_ether"},
                  "gasInput": {"amount": 1, "gas": "astral_mekanism:interstellar_antimatter"},
                  "gasOutput": {"amount": 1, "gas": "mekanismgenerators:fusion_fuel"},
                  "itemInput": {"ingredient": {"tag": "%s"}},
                  "itemNotConsumed": false,
                  "itemOutput": {"count": %d, "item": "%s"}
                }
                """.formatted(inputTag, outputCount, outputItem);
    }

    private static String nucleosynthesizingRecipe(String inputItem, String outputItem, int outputCount)
    {
        return nucleosynthesizingRecipeWithInputs(itemIngredient(inputItem), outputItem, outputCount);
    }

    private static String nucleosynthesizingRecipeWithInputs(String itemInput, String outputItem, int outputCount)
    {
        return """
                {
                  "type": "mekanism:nucleosynthesizing",
                  "duration": 200,
                  "gasInput": {"amount": 1, "gas": "mekanism:antimatter"},
                  "itemInput": %s,
                  "output": {"count": %d, "item": "%s"}
                }
                """.formatted(itemInput, outputCount, outputItem);
    }

    private static String chemicalItemRecipe(String type, String inputItem, String gas,
                                             String outputItem, int outputCount)
    {
        return chemicalItemRecipeWithInputs(type, itemIngredient(inputItem), gas, outputItem, outputCount);
    }

    private static String chemicalItemRecipeWithInputs(String type, String itemInput, String gas,
                                                       String outputItem, int outputCount)
    {
        return """
                {
                  "type": "%s",
                  "chemicalInput": {"amount": 1, "gas": "%s"},
                  "itemInput": %s,
                  "output": {"count": %d, "item": "%s"}
                }
                """.formatted(type, gas, itemInput, outputCount, outputItem);
    }

    private static String dissolutionRecipe(String inputItem, String outputSlurry, int outputAmount)
    {
        return dissolutionRecipeWithInputs(itemIngredient(inputItem), outputSlurry, outputAmount);
    }

    private static String dissolutionRecipeWithInputs(String itemInput, String outputSlurry, int outputAmount)
    {
        return """
                {
                  "type": "mekanism:dissolution",
                  "gasInput": {"amount": 1, "gas": "astral_mekanism:singularity_acid"},
                  "itemInput": %s,
                  "output": {"amount": %d, "chemicalType": "slurry", "slurry": "%s"}
                }
                """.formatted(itemInput, outputAmount, outputSlurry);
    }

    private static String washingRecipe(String inputSlurry, String outputSlurry, int outputAmount)
    {
        return """
                {
                  "type": "mekanism:washing",
                  "fluidInput": {"amount": 1, "fluid": "astral_mekanism:wisdom_rivulet"},
                  "slurryInput": {"amount": 1, "slurry": "%s"},
                  "output": {"amount": %d, "slurry": "%s"}
                }
                """.formatted(inputSlurry, outputAmount, outputSlurry);
    }

    private static String crystallizingRecipe(String inputSlurry, String outputItem, int outputCount)
    {
        return """
                {
                  "type": "mekanism:crystallizing",
                  "chemicalType": "slurry",
                  "input": {"amount": 1, "slurry": "%s"},
                  "output": {"count": %d, "item": "%s"}
                }
                """.formatted(inputSlurry, outputCount, outputItem);
    }

    private static String itemRecipe(String type, String inputItem, String outputItem, int outputCount)
    {
        return """
                {
                  "type": "%s",
                  "input": {"ingredient": {"item": "%s"}},
                  "output": {"count": %d, "item": "%s"}
                }
                """.formatted(type, inputItem, outputCount, outputItem);
    }

    private static int itemOutput(int base, MaterialConfig.MaterialDefinition material)
    {
        return scaled(base, material.astralMultiplier());
    }

    private static int denseItemOutput(int base, MaterialConfig.MaterialDefinition material)
    {
        return scaled(base, material.astralMultiplier() * material.denseFactor());
    }

    private static int chemicalOutput(int base, MaterialConfig.MaterialDefinition material)
    {
        return scaled(base, material.astralMultiplier());
    }

    private static int denseChemicalOutput(int base, MaterialConfig.MaterialDefinition material)
    {
        return scaled(base, material.astralMultiplier() * material.denseFactor());
    }

    private static int scaled(int base, double multiplier)
    {
        long scaled = Math.round(base * multiplier);
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, scaled));
    }

    private static String itemId(String stage, String materialId)
    {
        return "astral_" + stage + "_" + materialId + (stage.endsWith("crystal") || stage.endsWith("shard")
                || stage.endsWith("clump") || stage.endsWith("dust") ? "" : "_ore");
    }

    private static ResourceLocation processingItemLocation(String stage, String materialId)
    {
        return AstralShiningResolver.resolve(materialId, stage).location();
    }

    private static ResourceLocation processingItemLocation(String stage, String materialId, boolean nativeProcessing)
    {
        return nativeProcessing
                ? AstralShiningResolver.nativeProcessingItem(materialId, stage)
                : processingItemLocation(stage, materialId);
    }

    private static String nativeSlurry(String materialId, boolean shining)
    {
        String prefix = shining ? "shining_" : "specific_";
        return AstralShiningResolver.ASTRAL_MOD_ID + ":" + prefix
                + AstralShiningResolver.nativeMaterialId(materialId) + "_slurry";
    }

    private static String itemInputs(String feedstockTag, String previousItem)
    {
        List<String> ingredients = new ArrayList<>();
        ingredients.add(tagIngredient(feedstockTag));
        ingredients.add(itemIngredient(previousItem));
        return "[" + String.join(",", ingredients) + "]";
    }

    private static String itemIngredient(String item)
    {
        return "{\"ingredient\":{\"item\":\"" + item + "\"}}";
    }

    private static String tagIngredient(String tag)
    {
        return "{\"ingredient\":{\"tag\":\"" + tag + "\"}}";
    }

    static ResourceLocation processingItemLocation(
            String stage, String materialId, Predicate<String> resourceExists)
    {
        return AstralShiningResolver.resolve(materialId, stage, resourceExists).location();
    }

    private static String location(String path)
    {
        return ExampleMod.MODID + ":" + path;
    }

    private static String tagJson(Iterable<String> values)
    {
        List<String> entries = new ArrayList<>();
        values.forEach(entries::add);
        StringBuilder json = new StringBuilder("{\n  \"replace\": false,\n  \"values\": [\n");

        for (int index = 0; index < entries.size(); index++)
        {
            json.append("    \"").append(entries.get(index)).append("\"");
            json.append(index + 1 < entries.size() ? ",\n" : "\n");
        }

        return json.append("  ]\n}\n").toString();
    }

    private static void addLanguageEntries(Map<String, String> english, Map<String, String> japanese,
                                           MaterialConfig.MaterialDefinition material)
    {
        String en = displayName(material.id());
        String ja = japaneseMaterialName(material.id(), en);
        addItemLanguage(english, japanese, itemId("reconstructed", material.id()),
                "Reconstructed " + en + " Ore", "再構築" + ja + "鉱石");
        addItemLanguage(english, japanese, itemId("enriched", material.id()),
                "Astrally Enriched " + en + " Ore", "アストラル濃縮" + ja + "鉱石");
        addItemLanguage(english, japanese, itemId("sparkling", material.id()),
                "Sparkling " + en + " Ore", "煌めく" + ja + "鉱石");
        addFallbackItemLanguage(english, japanese, material, "shining_crystal",
                "Shining " + en + " Crystal", "輝く" + ja + "の結晶");
        addFallbackItemLanguage(english, japanese, material, "shining_shard",
                "Shining " + en + " Shard", "輝く" + ja + "の欠片");
        addFallbackItemLanguage(english, japanese, material, "shining_clump",
                "Shining " + en + " Clump", "輝く" + ja + "の塊");
        addFallbackItemLanguage(english, japanese, material, "shining_dust",
                "Shining " + en + " Dust", "輝く" + ja + "の粉");
        english.put("slurry.execore.astral_specific_" + material.id() + "_slurry", "Specific " + en + " Slurry");
        english.put("slurry.execore.astral_shining_" + material.id() + "_slurry", "Shining " + en + " Slurry");
        japanese.put("slurry.execore.astral_specific_" + material.id() + "_slurry", ja + "の特異スラリー");
        japanese.put("slurry.execore.astral_shining_" + material.id() + "_slurry", "輝く" + ja + "のスラリー");
    }

    private static void addItemLanguage(Map<String, String> english, Map<String, String> japanese,
                                        String itemId, String englishName, String japaneseName)
    {
        english.put("item.execore." + itemId, englishName);
        japanese.put("item.execore." + itemId, japaneseName);
    }

    private static void addFallbackItemLanguage(Map<String, String> english, Map<String, String> japanese,
                                                MaterialConfig.MaterialDefinition material, String stage,
                                                String englishName, String japaneseName)
    {
        if (processingItemLocation(stage, material.id()).getNamespace().equals(ExampleMod.MODID))
        {
            addItemLanguage(english, japanese, itemId(stage, material.id()), englishName, japaneseName);
        }
    }

    private static String displayName(String id)
    {
        String[] words = id.split("_");
        StringBuilder result = new StringBuilder();

        for (String word : words)
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
            case "ancient_debris" -> "古代の残骸";
            case "netherite" -> "ネザライト";
            default -> fallback;
        };
    }

    private static void writeLanguage(Path root, String language, Map<String, String> entries) throws IOException
    {
        GeneratedLanguageWriter.write(root, language, entries);
    }
}

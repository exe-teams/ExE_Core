package io.github.sponeru.execore;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

public final class GeneratedAssetPack
{
    private static Path generatedRoot;

    private static final String PACK_MCMETA = """
            {
              "pack": {
                "description": {
                  "text": "ExE Core generated assets"
                },
                "pack_format": 15
              }
            }
            """;

    private GeneratedAssetPack()
    {
    }

    public static synchronized Path generate(List<MaterialConfig.MaterialDefinition> materials) throws IOException
    {
        Path root = FMLPaths.CONFIGDIR.get().resolve("execore-generated-assets");

        if (generatedRoot != null && Files.exists(generatedRoot))
        {
            return generatedRoot;
        }

        deleteExistingPack(root);
        Files.createDirectories(root);
        Files.writeString(root.resolve("pack.mcmeta"), PACK_MCMETA);

        Path namespace = root.resolve("assets").resolve(ExampleMod.MODID);
        Path blockstates = namespace.resolve("blockstates");
        Path blockModels = namespace.resolve("models").resolve("block");
        Path itemModels = namespace.resolve("models").resolve("item");
        Path lang = namespace.resolve("lang");

        Files.createDirectories(blockstates);
        Files.createDirectories(blockModels);
        Files.createDirectories(itemModels);
        Files.createDirectories(lang);

        Map<String, String> translations = new TreeMap<>();
        List<String> pickaxeMineable = new ArrayList<>();
        List<String> needsStoneTool = new ArrayList<>();
        List<String> needsIronTool = new ArrayList<>();
        List<String> needsDiamondTool = new ArrayList<>();
        Map<String, List<String>> forgeOreTags = new LinkedHashMap<>();

        for (MaterialConfig.MaterialDefinition material : materials)
        {
            List<String> materialBlocks = new ArrayList<>();

            if (material.generateOre())
            {
                addVariant(blockstates, blockModels, itemModels, translations, pickaxeMineable, materialBlocks, material.id() + "_ore", "template_ore_stone");
                addVariant(blockstates, blockModels, itemModels, translations, pickaxeMineable, materialBlocks, "deepslate_" + material.id() + "_ore", "template_ore_deepslate");
            }

            if (material.generateDenseOre())
            {
                addVariant(blockstates, blockModels, itemModels, translations, pickaxeMineable, materialBlocks, "dense_" + material.id() + "_ore", "template_dense_ore_stone");
                addVariant(blockstates, blockModels, itemModels, translations, pickaxeMineable, materialBlocks, "dense_deepslate_" + material.id() + "_ore", "template_dense_ore_deepslate");
            }

            if (!materialBlocks.isEmpty())
            {
                addToolTierBlocks(material.id(), materialBlocks, needsStoneTool, needsIronTool, needsDiamondTool);
                forgeOreTags.put(material.id(), materialBlocks);
            }
        }

        Files.writeString(lang.resolve("en_us.json"), langJson(translations));
        writeTags(root, pickaxeMineable, needsStoneTool, needsIronTool, needsDiamondTool, forgeOreTags);
        generatedRoot = root;
        return root;
    }

    private static void deleteExistingPack(Path root) throws IOException
    {
        if (Files.notExists(root))
        {
            return;
        }

        try (Stream<Path> paths = Files.walk(root))
        {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
            {
                Files.delete(path);
            }
        }
    }

    private static void addVariant(Path blockstates, Path blockModels, Path itemModels, Map<String, String> translations, List<String> pickaxeMineable, List<String> materialBlocks, String blockId, String template) throws IOException
    {
        writeVariant(blockstates, blockModels, itemModels, translations, blockId, template);
        String blockLocation = ExampleMod.MODID + ":" + blockId;
        pickaxeMineable.add(blockLocation);
        materialBlocks.add(blockLocation);
    }

    private static void writeVariant(Path blockstates, Path blockModels, Path itemModels, Map<String, String> translations, String blockId, String template) throws IOException
    {
        Files.writeString(blockstates.resolve(blockId + ".json"), blockstateJson(blockId));
        Files.writeString(blockModels.resolve(blockId + ".json"), blockModelJson(template));
        Files.writeString(itemModels.resolve(blockId + ".json"), itemModelJson(blockId));
        translations.put("block." + ExampleMod.MODID + "." + blockId, englishName(blockId));
    }

    private static void addToolTierBlocks(String materialId, List<String> materialBlocks, List<String> needsStoneTool, List<String> needsIronTool, List<String> needsDiamondTool)
    {
        switch (toolTier(materialId))
        {
            case STONE -> needsStoneTool.addAll(materialBlocks);
            case IRON -> needsIronTool.addAll(materialBlocks);
            case DIAMOND -> needsDiamondTool.addAll(materialBlocks);
            default -> {
            }
        }
    }

    private static ToolTier toolTier(String materialId)
    {
        return switch (materialId)
        {
            case "iron", "copper", "lapis", "quartz" -> ToolTier.STONE;
            case "gold", "redstone", "diamond", "emerald" -> ToolTier.IRON;
            case "ancient_debris", "netherite" -> ToolTier.DIAMOND;
            default -> ToolTier.NONE;
        };
    }

    private static void writeTags(Path root, List<String> pickaxeMineable, List<String> needsStoneTool, List<String> needsIronTool, List<String> needsDiamondTool, Map<String, List<String>> forgeOreTags) throws IOException
    {
        Path minecraftBlockTags = root.resolve("data").resolve("minecraft").resolve("tags").resolve("blocks");
        Path forgeBlockTags = root.resolve("data").resolve("forge").resolve("tags").resolve("blocks");
        Path forgeOreTagPath = forgeBlockTags.resolve("ores");

        Files.createDirectories(minecraftBlockTags.resolve("mineable"));
        Files.createDirectories(forgeOreTagPath);

        Files.writeString(minecraftBlockTags.resolve("mineable").resolve("pickaxe.json"), tagJson(pickaxeMineable));
        Files.writeString(minecraftBlockTags.resolve("needs_stone_tool.json"), tagJson(needsStoneTool));
        Files.writeString(minecraftBlockTags.resolve("needs_iron_tool.json"), tagJson(needsIronTool));
        Files.writeString(minecraftBlockTags.resolve("needs_diamond_tool.json"), tagJson(needsDiamondTool));
        Files.writeString(forgeBlockTags.resolve("ores.json"), tagJson(forgeOreTags.keySet().stream()
                .map(material -> "#forge:ores/" + material)
                .toList()));

        for (Map.Entry<String, List<String>> entry : forgeOreTags.entrySet())
        {
            Files.writeString(forgeOreTagPath.resolve(entry.getKey() + ".json"), tagJson(entry.getValue()));
        }
    }

    private static String tagJson(Iterable<String> values)
    {
        StringBuilder builder = new StringBuilder("{\n  \"replace\": false,\n  \"values\": [\n");
        List<String> entries = new ArrayList<>();

        values.forEach(entries::add);

        for (int index = 0; index < entries.size(); index++)
        {
            builder.append("    \"").append(entries.get(index)).append("\"");

            if (index + 1 < entries.size())
            {
                builder.append(',');
            }

            builder.append('\n');
        }

        return builder.append("  ]\n}\n").toString();
    }

    private static String blockstateJson(String blockId)
    {
        return "{\"variants\":{\"\":{\"model\":\"" + ExampleMod.MODID + ":block/" + blockId + "\"}}}";
    }

    private static String blockModelJson(String template)
    {
        return "{\"parent\":\"" + ExampleMod.MODID + ":block/" + template + "\"}";
    }

    private static String itemModelJson(String blockId)
    {
        return "{\"parent\":\"" + ExampleMod.MODID + ":block/" + blockId + "\"}";
    }

    private static String langJson(Map<String, String> translations)
    {
        StringBuilder builder = new StringBuilder("{\n");
        int index = 0;

        for (Map.Entry<String, String> entry : translations.entrySet())
        {
            builder.append("  \"")
                    .append(entry.getKey())
                    .append("\": \"")
                    .append(entry.getValue())
                    .append("\"");

            if (++index < translations.size())
            {
                builder.append(',');
            }

            builder.append('\n');
        }

        return builder.append("}\n").toString();
    }

    private static String englishName(String blockId)
    {
        String[] words = blockId.split("_");
        StringBuilder builder = new StringBuilder();

        for (String word : words)
        {
            if (word.isBlank())
            {
                continue;
            }

            if (!builder.isEmpty())
            {
                builder.append(' ');
            }

            builder.append(word.substring(0, 1).toUpperCase(Locale.ROOT))
                    .append(word.substring(1));
        }

        return builder.toString();
    }

    private enum ToolTier
    {
        NONE,
        STONE,
        IRON,
        DIAMOND
    }
}

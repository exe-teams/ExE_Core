package io.github.sponeru.execore;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

        Files.createDirectories(blockstates);
        Files.createDirectories(blockModels);
        Files.createDirectories(itemModels);
        List<String> pickaxeMineable = new ArrayList<>();
        List<String> needsStoneTool = new ArrayList<>();
        List<String> needsIronTool = new ArrayList<>();
        List<String> needsDiamondTool = new ArrayList<>();
        Map<String, List<String>> forgeOreTags = new LinkedHashMap<>();
        Map<String, List<String>> forgeRawMaterialTags = new LinkedHashMap<>();
        Map<String, List<String>> denseOreTags = new LinkedHashMap<>();

        for (MaterialConfig.MaterialDefinition material : materials)
        {
            List<String> materialBlocks = new ArrayList<>();

            if (material.generateRawOre())
            {
                writeRawOreItemModel(itemModels, material.id());
                forgeRawMaterialTags.put(material.id(), List.of(ExampleMod.MODID + ":raw_" + material.id()));
            }

            if (material.generateOre())
            {
                addVariant(blockstates, blockModels, itemModels, pickaxeMineable, materialBlocks, material.id() + "_ore", "template_ore_stone");
                addVariant(blockstates, blockModels, itemModels, pickaxeMineable, materialBlocks, "deepslate_" + material.id() + "_ore", "template_ore_deepslate");
            }

            if (material.generateDenseOre())
            {
                String denseOre = "dense_" + material.id() + "_ore";
                String denseDeepslateOre = "dense_deepslate_" + material.id() + "_ore";
                addVariant(blockstates, blockModels, itemModels, pickaxeMineable, materialBlocks, denseOre, "template_dense_ore_stone");
                addVariant(blockstates, blockModels, itemModels, pickaxeMineable, materialBlocks, denseDeepslateOre, "template_dense_ore_deepslate");
                denseOreTags.put(material.id(), List.of(
                        ExampleMod.MODID + ":" + denseOre,
                        ExampleMod.MODID + ":" + denseDeepslateOre));
            }

            if (!materialBlocks.isEmpty())
            {
                addToolTierBlocks(material.id(), materialBlocks, needsStoneTool, needsIronTool, needsDiamondTool);
                forgeOreTags.put(material.id(), materialBlocks);
            }
        }

        writeTags(root, pickaxeMineable, needsStoneTool, needsIronTool, needsDiamondTool,
                forgeOreTags, forgeRawMaterialTags, denseOreTags);
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

    private static void addVariant(Path blockstates, Path blockModels, Path itemModels, List<String> pickaxeMineable, List<String> materialBlocks, String blockId, String template) throws IOException
    {
        writeVariant(blockstates, blockModels, itemModels, blockId, template);
        String blockLocation = ExampleMod.MODID + ":" + blockId;
        pickaxeMineable.add(blockLocation);
        materialBlocks.add(blockLocation);
    }

    private static void writeVariant(Path blockstates, Path blockModels, Path itemModels, String blockId, String template) throws IOException
    {
        Files.writeString(blockstates.resolve(blockId + ".json"), blockstateJson(blockId));
        Files.writeString(blockModels.resolve(blockId + ".json"), blockModelJson(template));
        Files.writeString(itemModels.resolve(blockId + ".json"), itemModelJson(blockId));
    }

    private static void writeRawOreItemModel(Path itemModels, String materialId) throws IOException
    {
        Files.writeString(itemModels.resolve("raw_" + materialId + ".json"), rawOreItemModelJson());
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

    private static void writeTags(Path root, List<String> pickaxeMineable, List<String> needsStoneTool,
                                  List<String> needsIronTool, List<String> needsDiamondTool,
                                  Map<String, List<String>> forgeOreTags,
                                  Map<String, List<String>> forgeRawMaterialTags,
                                  Map<String, List<String>> denseOreTags) throws IOException
    {
        Path minecraftBlockTags = root.resolve("data").resolve("minecraft").resolve("tags").resolve("blocks");
        Path forgeBlockTags = root.resolve("data").resolve("forge").resolve("tags").resolve("blocks");
        Path forgeOreTagPath = forgeBlockTags.resolve("ores");
        Path forgeRawMaterialTagPath = root.resolve("data").resolve("forge").resolve("tags").resolve("items").resolve("raw_materials");
        Path denseOreTagPath = root.resolve("data").resolve(ExampleMod.MODID).resolve("tags").resolve("blocks").resolve("dense_ores");

        Files.createDirectories(minecraftBlockTags.resolve("mineable"));
        Files.createDirectories(forgeOreTagPath);
        Files.createDirectories(forgeRawMaterialTagPath);
        Files.createDirectories(denseOreTagPath);

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

        for (Map.Entry<String, List<String>> entry : forgeRawMaterialTags.entrySet())
        {
            Files.writeString(forgeRawMaterialTagPath.resolve(entry.getKey() + ".json"), tagJson(entry.getValue()));
        }

        for (Map.Entry<String, List<String>> entry : denseOreTags.entrySet())
        {
            Files.writeString(denseOreTagPath.resolve(entry.getKey() + ".json"), tagJson(entry.getValue()));
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

    private static String rawOreItemModelJson()
    {
        return "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"" + ExampleMod.MODID + ":item/raw_ore_template\"}}";
    }

    private enum ToolTier
    {
        NONE,
        STONE,
        IRON,
        DIAMOND
    }
}

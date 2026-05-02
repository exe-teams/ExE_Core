package io.github.sponeru.execore;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class GeneratedAssetPack
{
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

    public static Path generate(List<MaterialConfig.MaterialDefinition> materials) throws IOException
    {
        Path root = FMLPaths.CONFIGDIR.get().resolve("execore-generated-assets");
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

        for (MaterialConfig.MaterialDefinition material : materials)
        {
            if (material.generateOre())
            {
                writeVariant(blockstates, blockModels, itemModels, translations, material.id() + "_ore", "template_ore_stone");
                writeVariant(blockstates, blockModels, itemModels, translations, "deepslate_" + material.id() + "_ore", "template_ore_deepslate");
            }

            if (material.generateDenseOre())
            {
                writeVariant(blockstates, blockModels, itemModels, translations, "dense_" + material.id() + "_ore", "template_dense_ore_stone");
                writeVariant(blockstates, blockModels, itemModels, translations, "dense_deepslate_" + material.id() + "_ore", "template_dense_ore_deepslate");
            }
        }

        Files.writeString(lang.resolve("en_us.json"), langJson(translations));
        return root;
    }

    private static void writeVariant(Path blockstates, Path blockModels, Path itemModels, Map<String, String> translations, String blockId, String template) throws IOException
    {
        Files.writeString(blockstates.resolve(blockId + ".json"), blockstateJson(blockId));
        Files.writeString(blockModels.resolve(blockId + ".json"), blockModelJson(template));
        Files.writeString(itemModels.resolve(blockId + ".json"), itemModelJson(blockId));
        translations.put("block." + ExampleMod.MODID + "." + blockId, englishName(blockId));
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
}

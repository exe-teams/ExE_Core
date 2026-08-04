package io.github.sponeru.execore;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MaterialConfig
{
    private MaterialConfig()
    {
    }

    public static List<MaterialDefinition> load()
    {
        Path configPath = FMLPaths.CONFIGDIR.get().resolve("execore-materials.toml");

        try
        {
            if (Files.notExists(configPath))
            {
                Files.writeString(configPath, defaultMaterialToml());
            }

            List<MaterialDefinition> materials = parse(Files.readString(configPath));
            return materials.isEmpty() ? defaultMaterials() : materials;
        }
        catch (Exception ignored)
        {
            return defaultMaterials();
        }
    }

    private static List<MaterialDefinition> parse(String toml)
    {
        List<Map<String, String>> sections = new ArrayList<>();
        Map<String, String> current = null;
        boolean readingGenerate = false;

        for (String rawLine : toml.split("\\R"))
        {
            String line = stripComment(rawLine).trim();

            if (line.isEmpty())
            {
                continue;
            }

            if (line.equals("[[material]]"))
            {
                current = new LinkedHashMap<>();
                sections.add(current);
                readingGenerate = false;
                continue;
            }

            if (current == null)
            {
                continue;
            }

            if (readingGenerate)
            {
                readGenerateValue(current, line);

                if (line.contains("]"))
                {
                    readingGenerate = false;
                }

                continue;
            }

            int equalsIndex = line.indexOf('=');

            if (equalsIndex < 0)
            {
                readGenerateValue(current, line);
                continue;
            }

            String key = line.substring(0, equalsIndex).trim();
            String value = line.substring(equalsIndex + 1).trim();

            if ("generate".equals(key))
            {
                readGenerateValue(current, value);
                readingGenerate = !value.contains("]") && !value.contains("}");
                continue;
            }

            current.put(key, cleanValue(value));
        }

        List<MaterialDefinition> materials = new ArrayList<>();

        for (Map<String, String> section : sections)
        {
            MaterialDefinition material = readMaterial(section);

            if (material != null)
            {
                materials.add(material);
            }
        }

        return materials;
    }

    private static void readGenerateValue(Map<String, String> current, String line)
    {
        readGenerateBoolean(current, line, "raw_ore");
        readGenerateBoolean(current, line, "dense_ore");
        readGenerateBoolean(current, line, "ore");
        readGenerateBoolean(current, line, "astral_processing");
        readGenerateBoolean(current, line, "mekanism_processing");
    }

    private static void readGenerateBoolean(Map<String, String> current, String line, String key)
    {
        Matcher matcher = Pattern.compile("(?<![A-Za-z0-9_])\"?" + Pattern.quote(key) + "\"?\\s*[:=]\\s*([^,}\\]]+)")
                .matcher(line);

        if (!matcher.find())
        {
            return;
        }

        current.put("generate." + key, cleanValue(matcher.group(1)));
    }

    private static MaterialDefinition readMaterial(Map<String, String> values)
    {
        String id = values.get("id");

        if (id == null || id.isBlank())
        {
            return null;
        }

        id = id.toLowerCase(Locale.ROOT);
        int color = readColor(values.get("color"), 0xFFFFFF);
        int denseFactor = Math.max(1, readInt(values.get("dense_factor"), 4));
        boolean generateRawOre = readBoolean(values.get("generate.raw_ore"), false);
        boolean generateOre = readBoolean(values.get("generate.ore"), true);
        boolean generateDenseOre = readBoolean(values.get("generate.dense_ore"), true);
        boolean generateAstralProcessing = readBoolean(values.get("generate.astral_processing"), true);
        boolean generateMekanismProcessing = readBoolean(values.get("generate.mekanism_processing"), true);
        double astralMultiplier = Math.max(0.01D, readDouble(values.get("astral_multiplier"), 1.0D));
        ResourceLocation drop = readDrop(values.get("drop"));
        ResourceLocation astralOutput = readResourceLocation(values.get("astral_output"));

        return new MaterialDefinition(id, color, denseFactor, drop, astralOutput, generateRawOre, generateOre,
                generateDenseOre, generateAstralProcessing, generateMekanismProcessing, astralMultiplier);
    }

    private static ResourceLocation readResourceLocation(String value)
    {
        if (value == null || value.isBlank())
        {
            return null;
        }

        try
        {
            return new ResourceLocation(value.contains(":") ? value : "minecraft:" + value);
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    private static ResourceLocation readDrop(String value)
    {
        if (value == null || value.isBlank())
        {
            return null;
        }

        try
        {
            ResourceLocation id = new ResourceLocation(value.contains(":") ? value : "minecraft:" + value);
            return ForgeRegistries.ITEMS.containsKey(id) ? id : null;
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    private static String stripComment(String line)
    {
        int commentIndex = line.indexOf('#');
        return commentIndex >= 0 ? line.substring(0, commentIndex) : line;
    }

    private static String cleanValue(String value)
    {
        String cleaned = value.trim()
                .replace(",", "")
                .replace("[", "")
                .replace("]", "")
                .replace("{", "")
                .replace("}", "")
                .trim();

        if ((cleaned.startsWith("\"") && cleaned.endsWith("\"")) || (cleaned.startsWith("'") && cleaned.endsWith("'")))
        {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }

        return cleaned.trim();
    }

    private static int readColor(String value, int fallback)
    {
        if (value == null || value.isBlank())
        {
            return fallback;
        }

        try
        {
            return Integer.decode(value);
        }
        catch (NumberFormatException ignored)
        {
            return fallback;
        }
    }

    private static int readInt(String value, int fallback)
    {
        if (value == null || value.isBlank())
        {
            return fallback;
        }

        try
        {
            return Integer.parseInt(value);
        }
        catch (NumberFormatException ignored)
        {
            return fallback;
        }
    }

    private static double readDouble(String value, double fallback)
    {
        if (value == null || value.isBlank())
        {
            return fallback;
        }

        try
        {
            return Double.parseDouble(value);
        }
        catch (NumberFormatException ignored)
        {
            return fallback;
        }
    }

    private static boolean readBoolean(String value, boolean fallback)
    {
        if (value == null || value.isBlank())
        {
            return fallback;
        }

        return Boolean.parseBoolean(value);
    }

    private static List<MaterialDefinition> defaultMaterials()
    {
        return List.of(
                new MaterialDefinition("iron", 0x6F6F6F, 4, null, null, true, false, true, true, true, 1.0D),
                new MaterialDefinition("diamond", 0x00FF00, 4, null, null, true, false, true, true, true, 1.0D),
                new MaterialDefinition("quartz", 0xFFFFFF, 4, ForgeRegistries.ITEMS.getKey(Items.QUARTZ), null, true, true, true, true, true, 1.0D));
    }

    private static String defaultMaterialToml()
    {
        return """
                # ExE Core material definitions.
                # id: material name used in block ids, such as dense_iron_ore.
                # color: ore overlay color.
                # dense_factor: item count dropped by dense ores without Silk Touch.
                # drop: optional item id. If omitted, ExE Core uses common vanilla raw/drop items or minecraft:<id>.
                # astral_output: optional final item from Astral enriching. If omitted, ExE Core uses drop.
                # generate: raw_ore, ore, dense_ore, astral_processing and mekanism_processing control generated content.
                # astral_multiplier: scales every Astral processing stage for this material (minimum 0.01).
                # When raw_ore is enabled, generated normal and dense ores drop the generated raw item.

                [[material]]
                id = "iron"
                color = 0x6f6f6f
                dense_factor = 4
                astral_output = "minecraft:raw_iron"
                astral_multiplier = 1.0
                generate = { raw_ore = true, ore = false, dense_ore = true, astral_processing = true, mekanism_processing = true }

                [[material]]
                id = "diamond"
                color = 0x00ff00
                dense_factor = 4
                astral_output = "minecraft:diamond"
                astral_multiplier = 1.0
                generate = { raw_ore = true, ore = false, dense_ore = true, astral_processing = true, mekanism_processing = true }

                [[material]]
                id = "quartz"
                color = 0xffffff
                dense_factor = 4
                drop = "minecraft:quartz"
                astral_output = "minecraft:quartz"
                astral_multiplier = 1.0
                generate = { raw_ore = true, ore = true, dense_ore = true, astral_processing = true, mekanism_processing = true }
                """;
    }

    public record MaterialDefinition(String id, int color, int denseFactor, ResourceLocation drop,
                                     ResourceLocation astralOutput,
                                     boolean generateRawOre, boolean generateOre, boolean generateDenseOre,
                                     boolean generateAstralProcessing, boolean generateMekanismProcessing,
                                     double astralMultiplier)
    {
        public ResourceLocation rawOreId()
        {
            return new ResourceLocation(ExampleMod.MODID, "raw_" + id);
        }

        public ResourceLocation dropId()
        {
            if (drop != null)
            {
                return drop;
            }

            return switch (id)
            {
                case "iron" -> ForgeRegistries.ITEMS.getKey(Items.RAW_IRON);
                case "copper" -> ForgeRegistries.ITEMS.getKey(Items.RAW_COPPER);
                case "gold" -> ForgeRegistries.ITEMS.getKey(Items.RAW_GOLD);
                case "redstone" -> ForgeRegistries.ITEMS.getKey(Items.REDSTONE);
                case "lapis" -> ForgeRegistries.ITEMS.getKey(Items.LAPIS_LAZULI);
                case "emerald" -> ForgeRegistries.ITEMS.getKey(Items.EMERALD);
                case "diamond" -> ForgeRegistries.ITEMS.getKey(Items.DIAMOND);
                case "coal" -> ForgeRegistries.ITEMS.getKey(Items.COAL);
                case "quartz" -> ForgeRegistries.ITEMS.getKey(Items.QUARTZ);
                default -> new ResourceLocation("minecraft", id);
            };
        }

        public ResourceLocation astralOutputId()
        {
            return astralOutput != null ? astralOutput : dropId();
        }
    }
}

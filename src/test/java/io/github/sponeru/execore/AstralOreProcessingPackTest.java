package io.github.sponeru.execore;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AstralOreProcessingPackTest
{
    @Test
    void everyIndependentRecipeBuilderProducesValidJson() throws Exception
    {
        assertRecipe("astral_mekanism:reconstruction", invoke("reconstructionRecipe",
                new Class<?>[]{String.class, String.class, int.class},
                "execore:feedstock", "execore:reconstructed", 8));
        assertRecipe("mekanism:nucleosynthesizing", invoke("nucleosynthesizingRecipe",
                new Class<?>[]{String.class, String.class, int.class},
                "execore:reconstructed", "execore:enriched", 6));
        assertRecipe("mekanism:compressing", invoke("chemicalItemRecipe",
                new Class<?>[]{String.class, String.class, String.class, String.class, int.class},
                "mekanism:compressing", "execore:enriched", "astral_mekanism:test_gas", "execore:sparkling", 4));
        assertRecipe("mekanism:dissolution", invoke("dissolutionRecipe",
                new Class<?>[]{String.class, String.class, int.class},
                "execore:sparkling", "execore:specific_slurry", 100));
        assertRecipe("mekanism:washing", invoke("washingRecipe",
                new Class<?>[]{String.class, String.class, int.class},
                "execore:specific_slurry", "execore:shining_slurry", 100));
        assertRecipe("mekanism:crystallizing", invoke("crystallizingRecipe",
                new Class<?>[]{String.class, String.class, int.class},
                "execore:shining_slurry", "execore:crystal", 1));
        assertRecipe("mekanism:crushing", invoke("itemRecipe",
                new Class<?>[]{String.class, String.class, String.class, int.class},
                "mekanism:crushing", "execore:clump", "execore:dust", 2));
    }

    @Test
    @SuppressWarnings("unchecked")
    void materialAstralControlsAreParsed() throws Exception
    {
        Method parse = MaterialConfig.class.getDeclaredMethod("parse", String.class);
        parse.setAccessible(true);
        List<MaterialConfig.MaterialDefinition> materials = (List<MaterialConfig.MaterialDefinition>) parse.invoke(null, """
                [[material]]
                id = "testium"
                color = 0x123456
                astral_multiplier = 2.5
                astral_output = "examplemod:compressed_testium"
                generate = { raw_ore = true, ore = true, dense_ore = true, astral_processing = false }
                """);

        assertEquals(1, materials.size());
        assertEquals(2.5D, materials.get(0).astralMultiplier());
        assertEquals(new ResourceLocation("examplemod", "compressed_testium"), materials.get(0).astralOutputId());
        assertFalse(materials.get(0).generateAstralProcessing());
    }

    @Test
    @SuppressWarnings("unchecked")
    void astralOutputDefaultsToTheMaterialDrop() throws Exception
    {
        Method parse = MaterialConfig.class.getDeclaredMethod("parse", String.class);
        parse.setAccessible(true);
        List<MaterialConfig.MaterialDefinition> materials = (List<MaterialConfig.MaterialDefinition>) parse.invoke(null, """
                [[material]]
                id = "outputless_testium"
                """);

        assertEquals(new ResourceLocation("minecraft", "outputless_testium"), materials.get(0).astralOutputId());
    }

    @Test
    void newGeneratedOreReceivesAstralProcessingWithoutAnExistingDropItem()
    {
        MaterialConfig.MaterialDefinition material = new MaterialConfig.MaterialDefinition(
                "testium", 0x123456, 4, null, null,
                false, true, true, true, true, 1.0D);

        assertTrue(AstralOreProcessingPack.hasProcessingFeedstock(material));
        assertEquals(new ResourceLocation("execore", "testium_ore"),
                AstralOreProcessingPack.processingOutputId(material));
    }

    @Test
    void shiningClumpUsesItsGeneratedBaseAndUntintedGlowLayer()
    {
        JsonObject model = JsonParser.parseString(AstralOreProcessingPack.itemModelJson("shining_clump"))
                .getAsJsonObject();
        JsonObject textures = model.getAsJsonObject("textures");

        assertEquals("execore:item/astral_processing/shining_clump", textures.get("layer0").getAsString());
        assertEquals("execore:item/astral_processing/shining_clump_glow", textures.get("layer1").getAsString());
        assertTrue(model.get("parent").getAsString().endsWith("item/generated"));
    }

    @Test
    void existingAstralShiningItemSkipsTheExecoreFallback()
    {
        AstralShiningResolver.Selection selection = AstralShiningResolver.resolve(
                "iron", "shining_clump",
                path -> path.equals("assets/astral_mekanism/models/item/shining_iron_clump.json"));

        assertFalse(selection.fallback());
        assertEquals(new ResourceLocation("astral_mekanism", "shining_iron_clump"), selection.location());
    }

    @Test
    void missingAstralShiningItemUsesTheExecoreFallback()
    {
        AstralShiningResolver.Selection selection = AstralShiningResolver.resolve(
                "diamond", "shining_clump", path -> false);

        assertTrue(selection.fallback());
        assertEquals(new ResourceLocation("execore", "astral_shining_clump_diamond"), selection.location());
    }

    @Test
    void recipeStageUsesTheSelectedAstralItem()
    {
        ResourceLocation selected = AstralOreProcessingPack.processingItemLocation(
                "shining_crystal", "iron",
                path -> path.equals("assets/astral_mekanism/models/item/shining_iron_crystal.json"));

        assertEquals("astral_mekanism:shining_iron_crystal", selected.toString());
    }

    @Test
    void completeNativeAstralChainSkipsExecoreProcessing()
    {
        assertTrue(AstralShiningResolver.hasNativeProcessing("iron",
                path -> path.startsWith("data/astral_mekanism/recipes/unique_processing/iron/")));
    }

    @Test
    void incompleteNativeAstralChainStillAllowsExecoreProcessing()
    {
        assertFalse(AstralShiningResolver.hasNativeProcessing("testium",
                path -> !path.endsWith("/purifying.json")));
    }

    @Test
    void lapisUsesAstralsLapisLazuliRecipeDirectory()
    {
        assertTrue(AstralShiningResolver.hasNativeProcessing("lapis",
                path -> path.startsWith("data/astral_mekanism/recipes/unique_processing/lapis_lazuli/")));
    }

    @Test
    void regularAstralStagesDoNotConsumeDenseOreThroughTheUnscaledRecipe() throws Exception
    {
        String inputs = invoke("itemInputs",
                new Class<?>[]{String.class, String.class},
                "execore:astral_processing/feedstocks/testium",
                "execore:astral_reconstructed_testium_ore");
        String json = invoke("nucleosynthesizingRecipeWithInputs",
                new Class<?>[]{String.class, String.class, int.class},
                inputs, "execore:astral_enriched_testium_ore", 25);
        JsonObject recipe = JsonParser.parseString(json).getAsJsonObject();

        assertEquals(2, recipe.getAsJsonArray("itemInput").size());
        assertEquals("execore:astral_processing/feedstocks/testium",
                recipe.getAsJsonArray("itemInput").get(0).getAsJsonObject()
                        .getAsJsonObject("ingredient").get("tag").getAsString());
        assertEquals("execore:astral_reconstructed_testium_ore",
                recipe.getAsJsonArray("itemInput").get(1).getAsJsonObject()
                        .getAsJsonObject("ingredient").get("item").getAsString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void denseReconstructionAppliesDenseFactorAndAstralMultiplier() throws Exception
    {
        Method parse = MaterialConfig.class.getDeclaredMethod("parse", String.class);
        parse.setAccessible(true);
        List<MaterialConfig.MaterialDefinition> materials = (List<MaterialConfig.MaterialDefinition>) parse.invoke(null, """
                [[material]]
                id = "dense_testium"
                dense_factor = 4
                astral_multiplier = 2.0
                """);
        Method output = AstralOreProcessingPack.class.getDeclaredMethod(
                "denseItemOutput", int.class, MaterialConfig.MaterialDefinition.class);
        output.setAccessible(true);

        assertEquals(24, output.invoke(null, 3, materials.get(0)));
        assertEquals(400, output.invoke(null, 50, materials.get(0)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyDenseAstralDirectStageAppliesDenseFactorAndAstralMultiplier() throws Exception
    {
        Method parse = MaterialConfig.class.getDeclaredMethod("parse", String.class);
        parse.setAccessible(true);
        List<MaterialConfig.MaterialDefinition> materials = (List<MaterialConfig.MaterialDefinition>) parse.invoke(null, """
                [[material]]
                id = "dense_stage_testium"
                dense_factor = 4
                astral_multiplier = 2.0
                """);
        Method itemOutput = AstralOreProcessingPack.class.getDeclaredMethod(
                "denseItemOutput", int.class, MaterialConfig.MaterialDefinition.class);
        Method chemicalOutput = AstralOreProcessingPack.class.getDeclaredMethod(
                "denseChemicalOutput", int.class, MaterialConfig.MaterialDefinition.class);
        itemOutput.setAccessible(true);
        chemicalOutput.setAccessible(true);

        assertEquals(48, itemOutput.invoke(null, 6, materials.get(0)));
        assertEquals(32, itemOutput.invoke(null, 4, materials.get(0)));
        assertEquals(800, chemicalOutput.invoke(null, 100, materials.get(0)));
        assertEquals(24, itemOutput.invoke(null, 3, materials.get(0)));
        assertEquals(16, itemOutput.invoke(null, 2, materials.get(0)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void denseReconstructionDoesNotCapFiftyTimesFourAtSixtyFour() throws Exception
    {
        Method parse = MaterialConfig.class.getDeclaredMethod("parse", String.class);
        parse.setAccessible(true);
        List<MaterialConfig.MaterialDefinition> materials = (List<MaterialConfig.MaterialDefinition>) parse.invoke(null, """
                [[material]]
                id = "dense_fifty_testium"
                dense_factor = 4
                astral_multiplier = 1.0
                """);
        Method output = AstralOreProcessingPack.class.getDeclaredMethod(
                "denseItemOutput", int.class, MaterialConfig.MaterialDefinition.class);
        output.setAccessible(true);

        int denseOutput = (int) output.invoke(null, 50, materials.get(0));
        String recipeJson = invoke("reconstructionRecipe",
                new Class<?>[]{String.class, String.class, int.class},
                "execore:dense_ores/dense_fifty_testium",
                "execore:astral_reconstructed_dense_fifty_testium_ore", denseOutput);

        assertEquals(200, denseOutput);
        assertEquals(200, JsonParser.parseString(recipeJson).getAsJsonObject()
                .getAsJsonObject("itemOutput").get("count").getAsInt());
    }

    @Test
    @SuppressWarnings("unchecked")
    void astralItemAndChemicalOutputsDoNotUseLegacyCaps() throws Exception
    {
        Method parse = MaterialConfig.class.getDeclaredMethod("parse", String.class);
        parse.setAccessible(true);
        List<MaterialConfig.MaterialDefinition> materials = (List<MaterialConfig.MaterialDefinition>) parse.invoke(null, """
                [[material]]
                id = "uncapped_testium"
                astral_multiplier = 4000.0
                """);
        Method itemOutput = AstralOreProcessingPack.class.getDeclaredMethod(
                "itemOutput", int.class, MaterialConfig.MaterialDefinition.class);
        Method chemicalOutput = AstralOreProcessingPack.class.getDeclaredMethod(
                "chemicalOutput", int.class, MaterialConfig.MaterialDefinition.class);
        itemOutput.setAccessible(true);
        chemicalOutput.setAccessible(true);

        assertEquals(4000.0D, materials.get(0).astralMultiplier());
        assertEquals(200_000, itemOutput.invoke(null, 50, materials.get(0)));
        assertEquals(2_000_000_000, chemicalOutput.invoke(null, 500_000, materials.get(0)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void reconstructionOutputIsReadBeforeGeneratedPackCreation(@TempDir Path tempDir) throws Exception
    {
        Path config = tempDir.resolve("execore-common.toml");
        Files.writeString(config, """
                [astralOreProcessing]
                enabled = true
                reconstructionOutput = 50
                """);

        Config.AstralOreProcessing fallback = new Config.AstralOreProcessing(
                true, 8, 6, 4, 100, 100, 1, 3, 2, 2, 2);
        Config.AstralOreProcessing values = AstralProcessingConfigReader.load(config, fallback);

        Method parse = MaterialConfig.class.getDeclaredMethod("parse", String.class);
        parse.setAccessible(true);
        List<MaterialConfig.MaterialDefinition> materials = (List<MaterialConfig.MaterialDefinition>) parse.invoke(null, """
                [[material]]
                id = "configured_testium"
                dense_factor = 4
                astral_multiplier = 1.0
                """);
        Method output = AstralOreProcessingPack.class.getDeclaredMethod(
                "denseItemOutput", int.class, MaterialConfig.MaterialDefinition.class);
        output.setAccessible(true);

        assertEquals(50, values.reconstructionOutput());
        assertEquals(200, output.invoke(null, values.reconstructionOutput(), materials.get(0)));
    }

    @Test
    void nativeAstralRecipesKeepTheirContractAndUseConfiguredOutputs()
    {
        JsonObject reconstruction = JsonParser.parseString("""
                {
                  "type": "astral_mekanism:reconstruction",
                  "duration": 1,
                  "itemInput": {"ingredient": {"tag": "astral_mekanism:feedstocks/iron"}},
                  "itemOutput": {"count": 50, "item": "astral_mekanism:reconstructed_iron_ore"}
                }
                """).getAsJsonObject();
        JsonObject dissolution = JsonParser.parseString("""
                {
                  "type": "mekanism:dissolution",
                  "output": {"amount": 160, "chemicalType": "slurry", "slurry": "astral_mekanism:specific_iron_slurry"}
                }
                """).getAsJsonObject();
        JsonObject purifying = JsonParser.parseString("""
                {
                  "type": "mekanism:purifying",
                  "output": {"item": "astral_mekanism:shining_iron_clump"}
                }
                """).getAsJsonObject();

        assertTrue(AstralOreProcessingPack.applyNativeOutput(reconstruction, "reconstruction", 200));
        assertTrue(AstralOreProcessingPack.applyNativeOutput(dissolution, "dissolution", 2_000_000));
        assertTrue(AstralOreProcessingPack.applyNativeOutput(purifying, "purifying", 80));

        assertEquals(200, reconstruction.getAsJsonObject("itemOutput").get("count").getAsInt());
        assertEquals(1, reconstruction.get("duration").getAsInt());
        assertEquals("astral_mekanism:feedstocks/iron", reconstruction.getAsJsonObject("itemInput")
                .getAsJsonObject("ingredient").get("tag").getAsString());
        assertEquals(2_000_000, dissolution.getAsJsonObject("output").get("amount").getAsInt());
        assertEquals(80, purifying.getAsJsonObject("output").get("count").getAsInt());
    }

    @Test
    @SuppressWarnings("unchecked")
    void nativeAstralRecipeIsWrittenAtTheSameResourceLocation(@TempDir Path tempDir) throws Exception
    {
        Path targetRoot = tempDir.resolve("generated").resolve("data")
                .resolve("astral_mekanism").resolve("recipes").resolve("unique_processing");
        Method parse = MaterialConfig.class.getDeclaredMethod("parse", String.class);
        parse.setAccessible(true);
        List<MaterialConfig.MaterialDefinition> materials = (List<MaterialConfig.MaterialDefinition>) parse.invoke(null, """
                [[material]]
                id = "iron"
                astral_multiplier = 2.0
                """);
        Config.AstralOreProcessing values = new Config.AstralOreProcessing(
                true, 50, 25, 20, 160, 10, 1, 10, 1, 8, 6);

        Path archive = tempDir.resolve("astral-recipes.zip");
        URI archiveUri = URI.create("jar:" + archive.toUri());

        try (FileSystem zip = FileSystems.newFileSystem(archiveUri, Map.of("create", "true")))
        {
            Path sourceRoot = zip.getPath("/unique_processing");
            Path sourceRecipe = sourceRoot.resolve("iron").resolve("reconstruction.json");
            Files.createDirectories(sourceRecipe.getParent());
            Files.writeString(sourceRecipe, """
                    {
                      "type": "astral_mekanism:reconstruction",
                      "duration": 1,
                      "itemOutput": {"count": 50, "item": "astral_mekanism:reconstructed_iron_ore"}
                    }
                    """);

            assertEquals(1, AstralOreProcessingPack.writeNativeRecipeOverrides(
                    sourceRoot, targetRoot, materials, values));
        }

        JsonObject generated = JsonParser.parseString(Files.readString(
                targetRoot.resolve("iron").resolve("reconstruction.json"))).getAsJsonObject();
        assertEquals(100, generated.getAsJsonObject("itemOutput").get("count").getAsInt());
        assertEquals(1, generated.get("duration").getAsInt());
    }

    @Test
    void nativeStagesUseAstralItemsForDenseRecipes()
    {
        assertEquals(new ResourceLocation("astral_mekanism", "enriched_iron_ore"),
                AstralShiningResolver.nativeProcessingItem("iron", "enriched", path -> false));
        assertEquals(new ResourceLocation("astral_mekanism", "shining_iron_shard"),
                AstralShiningResolver.nativeProcessingItem("iron", "shining_shard", path -> false));
        assertEquals(new ResourceLocation("astral_mekanism", "shining_diamond"),
                AstralShiningResolver.nativeProcessingItem("diamond", "shining_clump",
                        path -> path.endsWith("/shining_diamond.json")));
    }

    private static String invoke(String methodName, Class<?>[] parameterTypes, Object... arguments) throws Exception
    {
        Method method = AstralOreProcessingPack.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return (String) method.invoke(null, arguments);
    }

    private static void assertRecipe(String expectedType, String json)
    {
        JsonObject recipe = JsonParser.parseString(json).getAsJsonObject();
        assertEquals(expectedType, recipe.get("type").getAsString());
    }
}

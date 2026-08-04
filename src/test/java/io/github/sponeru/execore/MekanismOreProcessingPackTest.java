package io.github.sponeru.execore;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MekanismOreProcessingPackTest
{
    @Test
    void standardMekanismRecipeBuildersProduceExpectedContracts()
    {
        JsonObject purifying = parse(MekanismOreProcessingPack.chemicalItemRecipe(
                "mekanism:purifying", "forge:ores/testium", "mekanism:oxygen",
                "execore:clump_testium", 3));
        assertEquals("mekanism:purifying", purifying.get("type").getAsString());
        assertEquals("forge:ores/testium", purifying.getAsJsonObject("itemInput")
                .getAsJsonObject("ingredient").get("tag").getAsString());
        assertEquals(3, purifying.getAsJsonObject("output").get("count").getAsInt());

        JsonObject dissolution = parse(MekanismOreProcessingPack.dissolutionRecipe(
                "forge:ores/testium", "execore:dirty_testium"));
        assertEquals("mekanism:dissolution", dissolution.get("type").getAsString());
        assertEquals(1000, dissolution.getAsJsonObject("output").get("amount").getAsInt());

        JsonObject washing = parse(MekanismOreProcessingPack.washingRecipe(
                "execore:dirty_testium", "execore:clean_testium"));
        assertEquals("minecraft:water", washing.getAsJsonObject("fluidInput").get("tag").getAsString());

        JsonObject smelting = parse(MekanismOreProcessingPack.cookingRecipe(
                "minecraft:smelting", "forge:dusts/testium", "example:testium_ingot", 200));
        assertEquals("forge:dusts/testium", smelting.getAsJsonObject("ingredient").get("tag").getAsString());
        assertEquals("example:testium_ingot", smelting.get("result").getAsString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void materialCanDisableMekanismProcessingIndependently() throws Exception
    {
        Method parse = MaterialConfig.class.getDeclaredMethod("parse", String.class);
        parse.setAccessible(true);
        List<MaterialConfig.MaterialDefinition> materials = (List<MaterialConfig.MaterialDefinition>) parse.invoke(null, """
                [[material]]
                id = "testium"
                color = 0x123456
                generate = { ore = true, astral_processing = true, mekanism_processing = false }
                """);

        assertEquals(1, materials.size());
        assertFalse(materials.get(0).generateMekanismProcessing());
    }

    @Test
    void installedTagLookupUsesTheFirstItemAndFollowsNestedTags()
    {
        ResourceLocation dusts = new ResourceLocation("forge", "dusts/testium");
        ResourceLocation preferred = new ResourceLocation("example", "preferred_dust");
        MekanismOreProcessingPack.InstalledItemTags tags = new MekanismOreProcessingPack.InstalledItemTags(Map.of(
                dusts, List.of("#example:testium_dusts", "example:later_dust"),
                new ResourceLocation("example", "testium_dusts"), List.of(
                        preferred.toString(), "example:second_dust")));

        assertEquals(preferred, tags.firstItem(dusts).orElseThrow());
    }

    @Test
    void denseMekanismOutputsApplyDenseFactor()
    {
        JsonObject dissolution = parse(MekanismOreProcessingPack.dissolutionRecipe(
                "execore:dense_ores/testium", "execore:dirty_testium",
                MekanismOreProcessingPack.scaledDense(500_000, 4)));

        assertEquals(2_000_000, dissolution.getAsJsonObject("output").get("amount").getAsInt());
        assertEquals(80, MekanismOreProcessingPack.scaledDense(20, 4));
    }

    private static JsonObject parse(String json)
    {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}

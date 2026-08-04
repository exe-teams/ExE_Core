package io.github.sponeru.execore;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

import java.nio.file.Files;
import java.util.function.Predicate;

/** Resolves Astral Mekanism's existing Shining items before ExE Core registers fallbacks. */
final class AstralShiningResolver
{
    static final String ASTRAL_MOD_ID = "astral_mekanism";
    private static final String[] NATIVE_PROCESSING_RECIPES = {
            "reconstruction", "nucleosynthesizing", "compressing", "dissolution", "washing",
            "crystallizing", "injecting", "purifying", "crushing", "enriching"
    };

    private AstralShiningResolver()
    {
    }

    static Selection resolve(String materialId, String stage)
    {
        return resolve(materialId, stage, AstralShiningResolver::resourceExists);
    }

    static Selection resolve(String materialId, String stage, Predicate<String> resourceExists)
    {
        if (!stage.startsWith("shining_"))
        {
            return new Selection(fallbackLocation(materialId, stage), true);
        }

        String suffix = stage.substring("shining_".length());
        ResourceLocation astralLocation = new ResourceLocation(
                ASTRAL_MOD_ID, "shining_" + nativeMaterialId(materialId) + "_" + suffix);
        String modelPath = "assets/" + ASTRAL_MOD_ID + "/models/item/" + astralLocation.getPath() + ".json";

        if (resourceExists.test(modelPath))
        {
            return new Selection(astralLocation, false);
        }

        return new Selection(fallbackLocation(materialId, stage), true);
    }

    static boolean hasNativeProcessing(String materialId)
    {
        return hasNativeProcessing(materialId, AstralShiningResolver::resourceExists);
    }

    static boolean hasNativeProcessing(String materialId, Predicate<String> resourceExists)
    {
        String astralMaterialId = nativeMaterialId(materialId);

        for (String recipe : NATIVE_PROCESSING_RECIPES)
        {
            String path = "data/" + ASTRAL_MOD_ID + "/recipes/unique_processing/"
                    + astralMaterialId + "/" + recipe + ".json";

            if (!resourceExists.test(path))
            {
                return false;
            }
        }

        return true;
    }

    static String nativeMaterialId(String materialId)
    {
        return "lapis".equals(materialId) ? "lapis_lazuli" : materialId;
    }

    static ResourceLocation nativeProcessingItem(String materialId, String stage)
    {
        return nativeProcessingItem(materialId, stage, AstralShiningResolver::resourceExists);
    }

    static ResourceLocation nativeProcessingItem(String materialId, String stage,
                                                 Predicate<String> resourceExists)
    {
        String nativeId = nativeMaterialId(materialId);

        if (stage.startsWith("shining_"))
        {
            String suffix = stage.substring("shining_".length());
            ResourceLocation suffixed = new ResourceLocation(ASTRAL_MOD_ID,
                    "shining_" + nativeId + "_" + suffix);

            if (!"clump".equals(suffix) || resourceExists.test(modelPath(suffixed)))
            {
                return suffixed;
            }

            ResourceLocation unsuffixed = new ResourceLocation(ASTRAL_MOD_ID, "shining_" + nativeId);
            return resourceExists.test(modelPath(unsuffixed)) ? unsuffixed : suffixed;
        }

        return new ResourceLocation(ASTRAL_MOD_ID, stage + "_" + nativeId + "_ore");
    }

    private static String modelPath(ResourceLocation item)
    {
        return "assets/" + item.getNamespace() + "/models/item/" + item.getPath() + ".json";
    }

    static ResourceLocation fallbackLocation(String materialId, String stage)
    {
        boolean isOre = !stage.startsWith("shining_");
        String path = "astral_" + stage + "_" + materialId + (isOre ? "_ore" : "");
        return new ResourceLocation(ExampleMod.MODID, path);
    }

    private static boolean resourceExists(String resourcePath)
    {
        var modFile = ModList.get().getModFileById(ASTRAL_MOD_ID);
        return modFile != null && Files.isRegularFile(modFile.getFile().findResource(resourcePath));
    }

    record Selection(ResourceLocation location, boolean fallback)
    {
    }
}

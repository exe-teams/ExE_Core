package io.github.sponeru.execore;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

public class ConfiguredRawOreItem extends Item
{
    private final MaterialConfig.MaterialDefinition material;

    public ConfiguredRawOreItem(Properties properties, MaterialConfig.MaterialDefinition material)
    {
        super(properties);
        this.material = material;
    }

    public int oreColor()
    {
        return material.color();
    }

    @Override
    public MutableComponent getName(ItemStack stack)
    {
        Component materialName = Component.translatableWithFallback(
                "material." + ExampleMod.MODID + "." + material.id(),
                englishName(material.id()));
        return Component.translatable("item." + ExampleMod.MODID + ".raw_ore", materialName);
    }

    private static String englishName(String id)
    {
        StringBuilder builder = new StringBuilder();

        for (String word : id.split("_"))
        {
            if (word.isBlank())
            {
                continue;
            }
            if (!builder.isEmpty())
            {
                builder.append(' ');
            }
            builder.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }

        return builder.toString();
    }
}

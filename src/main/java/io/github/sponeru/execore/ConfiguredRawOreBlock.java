package io.github.sponeru.execore;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.List;
import java.util.Locale;

public class ConfiguredRawOreBlock extends Block
{
    private final MaterialConfig.MaterialDefinition material;

    public ConfiguredRawOreBlock(Properties properties, MaterialConfig.MaterialDefinition material)
    {
        super(properties);
        this.material = material;
    }

    public int oreColor()
    {
        return material.color();
    }

    @Override
    public MutableComponent getName()
    {
        Component materialName = Component.translatableWithFallback(
                "material." + ExampleMod.MODID + "." + material.id(),
                englishName(material.id()));
        return Component.translatable("block." + ExampleMod.MODID + ".raw_ore_block", materialName);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params)
    {
        ItemStack tool = params.getOptionalParameter(LootContextParams.TOOL);
        return tool != null && !tool.isEmpty() && tool.isCorrectToolForDrops(state)
                ? List.of(new ItemStack(this))
                : List.of();
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

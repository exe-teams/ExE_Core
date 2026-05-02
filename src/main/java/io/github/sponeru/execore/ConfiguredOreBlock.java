package io.github.sponeru.execore;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

public class ConfiguredOreBlock extends Block
{
    private final MaterialConfig.MaterialDefinition material;
    private final boolean dense;

    public ConfiguredOreBlock(Properties properties, MaterialConfig.MaterialDefinition material, boolean dense)
    {
        super(properties);
        this.material = material;
        this.dense = dense;
    }

    public int oreColor()
    {
        return material.color();
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params)
    {
        ItemStack tool = params.getOptionalParameter(LootContextParams.TOOL);

        if (tool == null || tool.isEmpty() || !tool.isCorrectToolForDrops(state))
        {
            return List.of();
        }

        if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, tool) > 0)
        {
            return List.of(new ItemStack(this));
        }

        if (dense && material.drop() == null)
        {
            List<ItemStack> originalOreDrops = getOriginalOreDrops(params);

            if (!originalOreDrops.isEmpty())
            {
                return multiplyStacks(originalOreDrops, material.denseFactor());
            }
        }

        Item drop = ForgeRegistries.ITEMS.getValue(material.dropId());

        if (drop == null || drop == Items.AIR)
        {
            return List.of();
        }

        return splitStacks(drop, dense ? material.denseFactor() : 1);
    }

    private List<ItemStack> getOriginalOreDrops(LootParams.Builder params)
    {
        TagKey<Block> oreTag = TagKey.create(Registries.BLOCK, new ResourceLocation("forge", "ores/" + material.id()));

        for (Block block : ForgeRegistries.BLOCKS.tags().getTag(oreTag))
        {
            ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(block);

            if (block == this || blockId == null || ExampleMod.MODID.equals(blockId.getNamespace()))
            {
                continue;
            }

            List<ItemStack> drops = block.defaultBlockState().getDrops(params);

            if (!drops.isEmpty())
            {
                return drops;
            }
        }

        return List.of();
    }

    private static List<ItemStack> multiplyStacks(List<ItemStack> source, int factor)
    {
        List<ItemStack> multiplied = new ArrayList<>();

        for (ItemStack stack : source)
        {
            int remaining = stack.getCount() * Math.max(1, factor);

            while (remaining > 0)
            {
                ItemStack copy = stack.copy();
                int count = Math.min(copy.getMaxStackSize(), remaining);
                copy.setCount(count);
                multiplied.add(copy);
                remaining -= count;
            }
        }

        return multiplied;
    }

    private static List<ItemStack> splitStacks(Item item, int count)
    {
        List<ItemStack> stacks = new ArrayList<>();
        int remaining = Math.max(1, count);
        int maxStackSize = new ItemStack(item).getMaxStackSize();

        while (remaining > 0)
        {
            int stackSize = Math.min(maxStackSize, remaining);
            stacks.add(new ItemStack(item, stackSize));
            remaining -= stackSize;
        }

        return stacks;
    }
}

package io.github.sponeru.execore.flight;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;
import java.util.Objects;

public final class AirborneMiningCharmItem extends Item implements ICurioItem
{
    public static final String SLOT_ID = "airborne_charm";
    private static final String SETTINGS_TAG = "ExEFlightSettings";
    private static final String SPEED_TAG = "Speed";
    private static final String NO_INERTIA_TAG = "NoInertia";

    public AirborneMiningCharmItem(Properties properties)
    {
        super(properties);
    }

    public static FlightSpeed getFlightSpeed(ItemStack stack)
    {
        CompoundTag settings = getSettings(stack, false);
        return settings == null ? FlightSpeed.NORMAL : FlightSpeed.byOrdinal(settings.getInt(SPEED_TAG));
    }

    public static void setFlightSpeed(ItemStack stack, FlightSpeed speed)
    {
        Objects.requireNonNull(getSettings(stack, true)).putInt(SPEED_TAG, speed.ordinal());
    }

    public static boolean isNoInertia(ItemStack stack)
    {
        CompoundTag settings = getSettings(stack, false);
        return settings != null && settings.getBoolean(NO_INERTIA_TAG);
    }

    public static void setNoInertia(ItemStack stack, boolean enabled)
    {
        Objects.requireNonNull(getSettings(stack, true)).putBoolean(NO_INERTIA_TAG, enabled);
    }

    @Nullable
    private static CompoundTag getSettings(ItemStack stack, boolean create)
    {
        CompoundTag root = create ? stack.getOrCreateTag() : stack.getTag();

        if (root == null)
        {
            return null;
        }

        if (!root.contains(SETTINGS_TAG, Tag.TAG_COMPOUND))
        {
            if (!create)
            {
                return null;
            }

            root.put(SETTINGS_TAG, new CompoundTag());
        }

        return root.getCompound(SETTINGS_TAG);
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack)
    {
        return SLOT_ID.equals(slotContext.identifier());
    }

    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack)
    {
        return canEquip(slotContext, stack);
    }

    @Override
    public boolean canSync(SlotContext slotContext, ItemStack stack)
    {
        return true;
    }

    @Override
    public @NotNull CompoundTag writeSyncData(SlotContext slotContext, ItemStack stack)
    {
        return stack.getOrCreateTag().copy();
    }

    @Override
    public void readSyncData(SlotContext slotContext, CompoundTag tag, ItemStack stack)
    {
        stack.setTag(tag.copy());
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag)
    {
        tooltip.add(Component.translatable("tooltip.execore.airborne_mining_charm.flight").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.execore.airborne_mining_charm.mining").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "tooltip.execore.airborne_mining_charm.speed",
                getFlightSpeed(stack).displayName()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                isNoInertia(stack)
                        ? "tooltip.execore.airborne_mining_charm.no_inertia_on"
                        : "tooltip.execore.airborne_mining_charm.no_inertia_off").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.execore.airborne_mining_charm.menu").withStyle(ChatFormatting.DARK_GRAY));
    }
}

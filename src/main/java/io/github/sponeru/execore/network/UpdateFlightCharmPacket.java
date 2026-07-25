package io.github.sponeru.execore.network;

import io.github.sponeru.execore.flight.AirborneMiningCharmItem;
import io.github.sponeru.execore.flight.FlightCharmService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record UpdateFlightCharmPacket(FlightMenuSelection selection)
{
    public static void encode(UpdateFlightCharmPacket packet, FriendlyByteBuf buffer)
    {
        buffer.writeVarInt(packet.selection.ordinal());
    }

    public static UpdateFlightCharmPacket decode(FriendlyByteBuf buffer)
    {
        return new UpdateFlightCharmPacket(FlightMenuSelection.byOrdinal(buffer.readVarInt()));
    }

    public static void handle(UpdateFlightCharmPacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();

            if (sender == null)
            {
                return;
            }

            FlightCharmService.findEquippedStack(sender).ifPresent(stack -> applySelection(stack, packet.selection));
        });
        context.setPacketHandled(true);
    }

    public static void applySelection(ItemStack stack, FlightMenuSelection selection)
    {
        if (selection == FlightMenuSelection.TOGGLE_NO_INERTIA)
        {
            AirborneMiningCharmItem.setNoInertia(stack, !AirborneMiningCharmItem.isNoInertia(stack));
        }
        else
        {
            AirborneMiningCharmItem.setFlightSpeed(stack, selection.speed());
        }
    }
}

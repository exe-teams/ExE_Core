package io.github.sponeru.execore.network;

import io.github.sponeru.execore.client.ClientOreScanPacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record OreScanResultPacket(List<BlockPos> positions)
{
    public static final int MAX_HIGHLIGHT_BLOCKS = 2048;

    public OreScanResultPacket
    {
        positions = List.copyOf(positions);

        if (positions.size() > MAX_HIGHLIGHT_BLOCKS)
        {
            throw new IllegalArgumentException("Too many ore scanner highlight positions: " + positions.size());
        }
    }

    public static void encode(OreScanResultPacket packet, FriendlyByteBuf buffer)
    {
        buffer.writeVarInt(packet.positions.size());
        packet.positions.forEach(buffer::writeBlockPos);
    }

    public static OreScanResultPacket decode(FriendlyByteBuf buffer)
    {
        int size = buffer.readVarInt();

        if (size < 0 || size > MAX_HIGHLIGHT_BLOCKS)
        {
            throw new IllegalArgumentException("Invalid ore scanner highlight count: " + size);
        }

        List<BlockPos> positions = new ArrayList<>(size);

        for (int index = 0; index < size; index++)
        {
            positions.add(buffer.readBlockPos());
        }

        return new OreScanResultPacket(positions);
    }

    public static void handle(OreScanResultPacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ClientOreScanPacketHandler.handle(packet)));
        context.setPacketHandled(true);
    }
}

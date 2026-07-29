package io.github.sponeru.execore.network;

import io.github.sponeru.execore.ExampleMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

@SuppressWarnings("removal")
public final class ModNetwork
{
    private static final String PROTOCOL_VERSION = "2";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ExampleMod.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);
    private static boolean registered;

    private ModNetwork()
    {
    }

    public static synchronized void register()
    {
        if (registered)
        {
            return;
        }

        registered = true;
        CHANNEL.registerMessage(
                0,
                UpdateFlightCharmPacket.class,
                UpdateFlightCharmPacket::encode,
                UpdateFlightCharmPacket::decode,
                UpdateFlightCharmPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(
                1,
                OreScanResultPacket.class,
                OreScanResultPacket::encode,
                OreScanResultPacket::decode,
                OreScanResultPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendToServer(UpdateFlightCharmPacket packet)
    {
        CHANNEL.sendToServer(packet);
    }

    public static void sendToPlayer(ServerPlayer player, OreScanResultPacket packet)
    {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}

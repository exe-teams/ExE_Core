package io.github.sponeru.execore.network;

import io.github.sponeru.execore.ExampleMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

@SuppressWarnings("removal")
public final class ModNetwork
{
    private static final String PROTOCOL_VERSION = "1";
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
    }

    public static void sendToServer(UpdateFlightCharmPacket packet)
    {
        CHANNEL.sendToServer(packet);
    }
}

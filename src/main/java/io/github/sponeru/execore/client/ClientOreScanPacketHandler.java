package io.github.sponeru.execore.client;

import io.github.sponeru.execore.network.OreScanResultPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientOreScanPacketHandler
{
    private ClientOreScanPacketHandler()
    {
    }

    public static void handle(OreScanResultPacket packet)
    {
        OreScannerHighlightRenderer.show(packet.positions());
    }
}

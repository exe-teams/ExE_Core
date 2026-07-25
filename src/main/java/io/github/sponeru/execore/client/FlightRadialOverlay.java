package io.github.sponeru.execore.client;

import io.github.sponeru.execore.flight.AirborneMiningCharmItem;
import io.github.sponeru.execore.flight.FlightSpeed;
import io.github.sponeru.execore.network.FlightMenuSelection;
import io.github.sponeru.execore.network.ModNetwork;
import io.github.sponeru.execore.network.UpdateFlightCharmPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;

public final class FlightRadialOverlay
{
    private static final FlightMenuSelection[] OPTIONS = FlightMenuSelection.values();
    private static final double INNER_RADIUS = 30.0D;
    private static final double OUTER_RADIUS = 118.0D;
    private static final double LABEL_RADIUS = 82.0D;
    private static int hoveredOption = -1;

    private FlightRadialOverlay()
    {
    }

    @SuppressWarnings("unused")
    public static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height)
    {
        if (!ClientFlightController.isFlightMenuVisible())
        {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        ItemStack charm = ClientFlightController.getActiveCharm();
        int centerX = width / 2;
        int centerY = height / 2;
        double mouseX = minecraft.mouseHandler.xpos()
                * width / minecraft.getWindow().getScreenWidth();
        double mouseY = minecraft.mouseHandler.ypos()
                * height / minecraft.getWindow().getScreenHeight();
        hoveredOption = findHoveredOption(mouseX - centerX, mouseY - centerY);

        graphics.fill(0, 0, width, height, 0x50000000);
        graphics.fill(centerX - 34, centerY - 20, centerX + 34, centerY + 20, 0xD0202530);
        graphics.drawCenteredString(font, Component.translatable("screen.execore.flight_menu"), centerX, centerY - 14, 0xFFFFFF);
        graphics.drawCenteredString(
                font,
                AirborneMiningCharmItem.getFlightSpeed(charm).displayName(),
                centerX,
                centerY - 2,
                0x80D8FF);
        graphics.drawCenteredString(
                font,
                Component.translatable(AirborneMiningCharmItem.isNoInertia(charm)
                        ? "screen.execore.no_inertia.on"
                        : "screen.execore.no_inertia.off"),
                centerX,
                centerY + 10,
                0xB8C6D9);

        double sectorSize = Math.PI * 2.0D / OPTIONS.length;

        for (int index = 0; index < OPTIONS.length; index++)
        {
            double angle = -Math.PI / 2.0D + sectorSize * index;
            int labelX = centerX + (int) Math.round(Math.cos(angle) * LABEL_RADIUS);
            int labelY = centerY + (int) Math.round(Math.sin(angle) * LABEL_RADIUS);
            Component label = labelFor(charm, OPTIONS[index]);
            int halfWidth = font.width(label) / 2 + 7;
            int color = index == hoveredOption ? 0xE04A90E2 : 0xC0202530;
            int textColor = index == hoveredOption ? 0xFFFFFF : 0xC8D4E3;

            graphics.fill(labelX - halfWidth, labelY - 8, labelX + halfWidth, labelY + 8, color);
            graphics.drawCenteredString(font, label, labelX, labelY - 4, textColor);
        }

        graphics.drawCenteredString(
                font,
                Component.translatable("screen.execore.flight_menu.hint"),
                centerX,
                centerY + 132,
                0xA0A8B4);
    }

    static boolean applyHoveredSelection(ItemStack charm)
    {
        if (hoveredOption < 0 || hoveredOption >= OPTIONS.length || charm.isEmpty())
        {
            return false;
        }

        FlightMenuSelection selection = OPTIONS[hoveredOption];
        UpdateFlightCharmPacket.applySelection(charm, selection);
        ModNetwork.sendToServer(new UpdateFlightCharmPacket(selection));
        return true;
    }

    static void clearHoveredOption()
    {
        hoveredOption = -1;
    }

    private static int findHoveredOption(double relativeX, double relativeY)
    {
        double distance = Math.sqrt(relativeX * relativeX + relativeY * relativeY);

        if (distance < INNER_RADIUS || distance > OUTER_RADIUS)
        {
            return -1;
        }

        double sectorSize = Math.PI * 2.0D / OPTIONS.length;
        double angleFromTop = Math.atan2(relativeY, relativeX) + Math.PI / 2.0D;

        if (angleFromTop < 0.0D)
        {
            angleFromTop += Math.PI * 2.0D;
        }

        return (int) Math.floor((angleFromTop + sectorSize / 2.0D) / sectorSize) % OPTIONS.length;
    }

    private static Component labelFor(ItemStack charm, FlightMenuSelection selection)
    {
        if (selection == FlightMenuSelection.TOGGLE_NO_INERTIA)
        {
            return Component.translatable(AirborneMiningCharmItem.isNoInertia(charm)
                    ? "screen.execore.no_inertia.disable"
                    : "screen.execore.no_inertia.enable");
        }

        FlightSpeed speed = selection.speed();
        return speed.displayName();
    }
}

package io.github.sponeru.execore.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.sponeru.execore.ExampleMod;
import io.github.sponeru.execore.flight.AirborneMiningCharmItem;
import io.github.sponeru.execore.flight.FlightCharmService;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientFlightController
{
    private static final int MENU_HOLD_TICKS = 6;
    private static final KeyMapping OPEN_FLIGHT_MENU = new KeyMapping(
            "key.execore.flight_menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.execore");
    private static int menuHoldTicks;
    private static boolean menuOpenAttempted;
    private static boolean flightMenuVisible;
    private static ItemStack activeCharm = ItemStack.EMPTY;

    private ClientFlightController()
    {
    }

    public static void registerKeyMappings(RegisterKeyMappingsEvent event)
    {
        event.register(OPEN_FLIGHT_MENU);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        if (player == null)
        {
            resetFlightMenuState(minecraft);
            return;
        }

        updateFlightMenuHold(minecraft, player);
        applyNoInertia(player);
    }

    private static void updateFlightMenuHold(Minecraft minecraft, LocalPlayer player)
    {
        if (!isFlightMenuKeyPhysicallyDown(minecraft))
        {
            if (flightMenuVisible)
            {
                FlightRadialOverlay.applyHoveredSelection(activeCharm);
            }

            menuHoldTicks = 0;
            menuOpenAttempted = false;
            closeFlightMenu(minecraft, minecraft.screen == null && minecraft.getOverlay() == null);
            return;
        }

        if (minecraft.screen != null || minecraft.getOverlay() != null)
        {
            menuOpenAttempted = true;
            closeFlightMenu(minecraft, false);
            return;
        }

        if (menuHoldTicks < MENU_HOLD_TICKS)
        {
            menuHoldTicks++;
        }

        if (menuHoldTicks >= MENU_HOLD_TICKS && !menuOpenAttempted)
        {
            menuOpenAttempted = true;
            openFlightMenu(minecraft, player);
        }
    }

    private static boolean isFlightMenuKeyPhysicallyDown(Minecraft minecraft)
    {
        InputConstants.Key key = OPEN_FLIGHT_MENU.getKey();
        long window = minecraft.getWindow().getWindow();

        if (key.getType() == InputConstants.Type.KEYSYM)
        {
            return InputConstants.isKeyDown(window, key.getValue());
        }

        if (key.getType() == InputConstants.Type.MOUSE)
        {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }

        return OPEN_FLIGHT_MENU.isDown();
    }

    private static void openFlightMenu(Minecraft minecraft, LocalPlayer player)
    {
        Optional<ItemStack> equipped = FlightCharmService.findEquippedStack(player);

        if (equipped.isEmpty())
        {
            player.displayClientMessage(Component.translatable("message.execore.flight_charm_not_equipped"), true);
            return;
        }

        activeCharm = equipped.get();
        flightMenuVisible = true;
        FlightRadialOverlay.clearHoveredOption();
        minecraft.mouseHandler.releaseMouse();
    }

    @SubscribeEvent
    public static void onMouseButtonPre(InputEvent.MouseButton.Pre event)
    {
        if (!flightMenuVisible)
        {
            return;
        }

        event.setCanceled(true);
    }

    static boolean isFlightMenuVisible()
    {
        return flightMenuVisible;
    }

    static ItemStack getActiveCharm()
    {
        return activeCharm;
    }

    private static void closeFlightMenu(Minecraft minecraft, boolean restoreMouse)
    {
        if (!flightMenuVisible)
        {
            return;
        }

        flightMenuVisible = false;
        activeCharm = ItemStack.EMPTY;
        FlightRadialOverlay.clearHoveredOption();

        if (restoreMouse && minecraft.screen == null && minecraft.isWindowActive())
        {
            minecraft.mouseHandler.grabMouse();
        }
    }

    private static void resetFlightMenuState(Minecraft minecraft)
    {
        menuHoldTicks = 0;
        menuOpenAttempted = false;
        closeFlightMenu(minecraft, false);
    }

    private static void applyNoInertia(LocalPlayer player)
    {
        if (!player.getAbilities().flying)
        {
            return;
        }

        Optional<ItemStack> equipped = FlightCharmService.findEquippedStack(player);

        if (equipped.isEmpty() || !AirborneMiningCharmItem.isNoInertia(equipped.get()))
        {
            return;
        }

        Vec3 movement = player.getDeltaMovement();
        boolean hasHorizontalInput = Math.abs(player.input.leftImpulse) > 0.001F
                || Math.abs(player.input.forwardImpulse) > 0.001F;
        boolean hasVerticalInput = player.input.jumping || player.input.shiftKeyDown;

        player.setDeltaMovement(
                hasHorizontalInput ? movement.x : 0.0D,
                hasVerticalInput ? movement.y : 0.0D,
                hasHorizontalInput ? movement.z : 0.0D);
    }
}

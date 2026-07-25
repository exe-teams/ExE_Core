package io.github.sponeru.execore.flight;

import io.github.sponeru.execore.ExampleMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FlightCharmService
{
    private static final float AIRBORNE_MINING_MULTIPLIER = 5.0F;
    private static final Map<UUID, FlightState> GRANTED_FLIGHT = new HashMap<>();

    private FlightCharmService()
    {
    }

    public static Optional<SlotResult> findEquipped(Player player)
    {
        return CuriosApi.getCuriosInventory(player)
                .resolve()
                .flatMap(curios -> curios.findFirstCurio(stack -> stack.is(ExampleMod.AIRBORNE_MINING_CHARM.get())));
    }

    public static Optional<ItemStack> findEquippedStack(Player player)
    {
        return findEquipped(player).map(SlotResult::stack);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide
                || !(event.player instanceof ServerPlayer player))
        {
            return;
        }

        Optional<ItemStack> equipped = findEquippedStack(player);

        if (equipped.isPresent())
        {
            grantFlight(player, equipped.get());
        }
        else
        {
            revokeFlight(player);
        }
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event)
    {
        Player player = event.getEntity();

        if (player.getAbilities().flying && !player.onGround() && findEquippedStack(player).isPresent())
        {
            event.setNewSpeed(event.getNewSpeed() * AIRBORNE_MINING_MULTIPLIER);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event)
    {
        GRANTED_FLIGHT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event)
    {
        GRANTED_FLIGHT.remove(event.getOriginal().getUUID());
    }

    private static void grantFlight(ServerPlayer player, ItemStack stack)
    {
        Abilities abilities = player.getAbilities();
        GRANTED_FLIGHT.computeIfAbsent(
                player.getUUID(),
                uuid -> new FlightState(abilities.mayfly, abilities.getFlyingSpeed()));
        float desiredSpeed = AirborneMiningCharmItem.getFlightSpeed(stack).value();
        boolean changed = !abilities.mayfly || Math.abs(abilities.getFlyingSpeed() - desiredSpeed) > 0.0001F;

        abilities.mayfly = true;
        abilities.setFlyingSpeed(desiredSpeed);

        if (changed)
        {
            player.onUpdateAbilities();
        }
    }

    private static void revokeFlight(ServerPlayer player)
    {
        FlightState state = GRANTED_FLIGHT.remove(player.getUUID());

        if (state == null)
        {
            return;
        }

        Abilities abilities = player.getAbilities();
        abilities.mayfly = state.mayFly() || player.isCreative() || player.isSpectator();
        abilities.setFlyingSpeed(state.flyingSpeed());

        if (!abilities.mayfly)
        {
            abilities.flying = false;
        }

        player.onUpdateAbilities();
    }

    private record FlightState(boolean mayFly, float flyingSpeed)
    {
    }
}

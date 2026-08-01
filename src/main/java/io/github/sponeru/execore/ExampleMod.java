package io.github.sponeru.execore;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import top.theillusivec4.curios.api.CuriosApi;
import io.github.sponeru.execore.client.ClientFlightController;
import io.github.sponeru.execore.client.FlightRadialOverlay;
import io.github.sponeru.execore.flight.AirborneMiningCharmItem;
import io.github.sponeru.execore.network.ModNetwork;
import io.github.sponeru.execore.scanner.OreScannerItem;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(ExampleMod.MODID)
public class ExampleMod
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "execore";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Blocks which will all be registered under the "examplemod" namespace
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    // Create a Deferred Register to hold Items which will all be registered under the "examplemod" namespace
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(ForgeRegistries.FEATURES, MODID);

    public static final RegistryObject<Feature<NoneFeatureConfiguration>> THREE_LAYER_ORE_VEIN = FEATURES.register(
            "three_layer_ore_vein",
            () -> new OreVeinGenerator(NoneFeatureConfiguration.CODEC));
    public static final RegistryObject<Item> AIRBORNE_MINING_CHARM = ITEMS.register(
            "airborne_mining_charm",
            () -> new AirborneMiningCharmItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> ORE_SCANNER = ITEMS.register(
            "ore_scanner",
            () -> new OreScannerItem(new Item.Properties().durability(256)));
    public static final Map<String, RegistryObject<Block>> MATERIAL_BLOCKS = new LinkedHashMap<>();
    public static final Map<String, RegistryObject<Item>> MATERIAL_ITEMS = new LinkedHashMap<>();
    private static boolean materialBlocksRegistered;

    public ExampleMod(FMLJavaModLoadingContext context)
    {
        IEventBus modEventBus = context.getModEventBus();
        registerMaterialBlocks(MaterialConfig.load());

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::addPackFinders);

        // Register the Deferred Register to the mod event bus so blocks get registered
        BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
        FEATURES.register(modEventBus);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);

        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        LOGGER.info("ExE Core common setup");
        ModNetwork.register();
        event.enqueueWork(() -> {
            AirborneMiningCharmItem charm = (AirborneMiningCharmItem) AIRBORNE_MINING_CHARM.get();
            CuriosApi.registerCurio(charm, charm);
        });

        if (Config.logDirtBlock)
            LOGGER.info("DIRT BLOCK >> {}", ForgeRegistries.BLOCKS.getKey(Blocks.DIRT));

        LOGGER.info(Config.magicNumberIntroduction + Config.magicNumber);

        Config.items.forEach((item) -> LOGGER.info("ITEM >> {}", item.toString()));
    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event)
    {
        if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS)
            MATERIAL_ITEMS.values().forEach(event::accept);

        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES)
        {
            event.accept(AIRBORNE_MINING_CHARM);
            event.accept(ORE_SCANNER);
        }
    }

    private void addPackFinders(AddPackFindersEvent event)
    {
        if (event.getPackType() != PackType.CLIENT_RESOURCES && event.getPackType() != PackType.SERVER_DATA)
        {
            return;
        }

        try
        {
            Path generatedAssets = GeneratedAssetPack.generate(MaterialConfig.load());
            event.addRepositorySource(consumer -> {
                Pack.ResourcesSupplier supplier = packId -> new net.minecraftforge.resource.PathPackResources(packId, true, generatedAssets);
                Pack pack = Pack.readMetaAndCreate(
                        MODID + ":generated_assets",
                        Component.literal("ExE Core Generated Assets"),
                        true,
                        supplier,
                        event.getPackType(),
                        Pack.Position.TOP,
                        PackSource.BUILT_IN);

                if (pack != null)
                {
                    consumer.accept(pack);
                }
            });
        }
        catch (Exception exception)
        {
            LOGGER.error("Failed to generate ExE Core dynamic assets", exception);
        }
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        // Do something when the server starts
        LOGGER.info("ExE Core server starting");
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        @SuppressWarnings("removal")
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            // Some client setup code
            LOGGER.info("ExE Core client setup");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
            event.enqueueWork(() -> MATERIAL_BLOCKS.values().forEach(block -> ItemBlockRenderTypes.setRenderLayer(block.get(), RenderType.cutout())));
        }

        @SubscribeEvent
        public static void registerKeyMappings(RegisterKeyMappingsEvent event)
        {
            ClientFlightController.registerKeyMappings(event);
        }

        @SubscribeEvent
        public static void registerGuiOverlays(RegisterGuiOverlaysEvent event)
        {
            event.registerAboveAll("flight_radial_menu", FlightRadialOverlay::render);
        }

        @SubscribeEvent
        public static void registerBlockColors(RegisterColorHandlersEvent.Block event)
        {
            MATERIAL_BLOCKS.values().forEach(block -> event.register((state, level, pos, tintIndex) -> {
                if (tintIndex == 0 && state.getBlock() instanceof ConfiguredOreBlock oreBlock)
                {
                    return oreBlock.oreColor();
                }

                return 0xFFFFFF;
            }, block.get()));
        }

        @SubscribeEvent
        public static void registerItemColors(RegisterColorHandlersEvent.Item event)
        {
            MATERIAL_ITEMS.forEach((id, item) -> event.register((stack, tintIndex) -> {
                RegistryObject<Block> block = MATERIAL_BLOCKS.get(id);

                if (tintIndex == 0 && block != null && block.get() instanceof ConfiguredOreBlock oreBlock)
                {
                    return oreBlock.oreColor();
                }

                return 0xFFFFFF;
            }, item.get()));
        }
    }

    private static void registerMaterialBlocks(List<MaterialConfig.MaterialDefinition> materials)
    {
        if (materialBlocksRegistered)
        {
            return;
        }

        materialBlocksRegistered = true;

        for (MaterialConfig.MaterialDefinition material : materials)
        {
            if (material.generateOre())
            {
                registerMaterialBlock(material.id() + "_ore", material, false, false);
                registerMaterialBlock("deepslate_" + material.id() + "_ore", material, false, true);
            }

            if (material.generateDenseOre())
            {
                registerMaterialBlock("dense_" + material.id() + "_ore", material, true, false);
                registerMaterialBlock("dense_deepslate_" + material.id() + "_ore", material, true, true);
            }
        }
    }

    private static void registerMaterialBlock(String id, MaterialConfig.MaterialDefinition material, boolean dense, boolean deepslate)
    {
        RegistryObject<Block> block = BLOCKS.register(id, () -> new ConfiguredOreBlock(oreProperties(deepslate), material, dense, deepslate));
        RegistryObject<Item> item = ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));

        MATERIAL_BLOCKS.put(id, block);
        MATERIAL_ITEMS.put(id, item);
    }

    private static BlockBehaviour.Properties oreProperties(boolean deepslate)
    {
        return BlockBehaviour.Properties.copy(deepslate ? Blocks.DEEPSLATE : Blocks.STONE)
                .mapColor(deepslate ? MapColor.DEEPSLATE : MapColor.STONE)
                .requiresCorrectToolForDrops()
                .strength(deepslate ? 4.5F : 3.0F, 3.0F);
    }
}

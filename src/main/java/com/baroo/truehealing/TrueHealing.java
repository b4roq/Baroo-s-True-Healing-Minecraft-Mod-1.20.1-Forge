package com.baroo.truehealing;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(TrueHealing.MODID)
public class TrueHealing {
    public static final String MODID = "truehealing";

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    public static final RegistryObject<Item> RAG = ITEMS.register("rag",
            () -> new MedicalItem(new Item.Properties()));
    public static final RegistryObject<Item> BANDAGE = ITEMS.register("bandage",
            () -> new MedicalItem(new Item.Properties()));
    public static final RegistryObject<Item> BANDAID = ITEMS.register("bandaid",
            () -> new MedicalItem(new Item.Properties()));
    public static final RegistryObject<Item> WIPE = ITEMS.register("disinfectant_wipe",
            () -> new MedicalItem(new Item.Properties()));
    public static final RegistryObject<Item> SUTURE_KIT = ITEMS.register("suture_kit",
            () -> new MedicalItem(new Item.Properties().stacksTo(1).durability(8)));

    public static final RegistryObject<Item> SPLINT = ITEMS.register("splint",
            () -> new MedicalItem(new Item.Properties()));
    public static final RegistryObject<Item> ANTIBIOTICS = ITEMS.register("antibiotics",
            AntibioticsItem::new);
    public static final RegistryObject<Item> DIRTY_RAG = ITEMS.register("dirty_rag",
            () -> new MedicalItem(new Item.Properties()));
    public static final RegistryObject<Item> DIRTY_BANDAGE = ITEMS.register("dirty_bandage",
            () -> new MedicalItem(new Item.Properties()));

    public TrueHealing() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus);
        TrueHealingRecipes.SERIALIZERS.register(bus);
        bus.addListener(this::commonSetup);
        bus.addListener(this::addCreative);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, TrueHealingConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, TrueHealingClientConfig.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(TrueHealingNetwork::register);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(RAG);
            event.accept(BANDAGE);
            event.accept(BANDAID);
            event.accept(WIPE);
            event.accept(SUTURE_KIT);
            event.accept(SPLINT);
            event.accept(ANTIBIOTICS);
            event.accept(DIRTY_RAG);
            event.accept(DIRTY_BANDAGE);
        }
    }
}

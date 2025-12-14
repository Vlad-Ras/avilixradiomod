package com.avilixradiomod;

import com.avilixradiomod.client.ClientInit;
import com.avilixradiomod.network.ModPayloads;
import com.avilixradiomod.registry.*;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;

import net.neoforged.fml.common.Mod;

import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@Mod(AvilixRadioMod.MOD_ID)
public final class AvilixRadioMod {
    public static final String MOD_ID = "avilixradiomod";

    public AvilixRadioMod(IEventBus modBus, Dist dist) {
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModBlockEntities.register(modBus);
        ModMenus.register(modBus);
        ModCreativeTabs.register(modBus);


        modBus.addListener(com.avilixradiomod.network.ModPayloads::register);
        if (dist.isClient()) {
            ClientInit.init(modBus);
        }
    }

    private void buildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            event.accept(ModBlocks.RADIO);
            event.accept(ModBlocks.SPEAKER);
            event.accept(ModItems.REMOTE.get());
        }
    }
}

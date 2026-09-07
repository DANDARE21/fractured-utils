package net.dandare21.fracturedutils.puppet.registry;

import net.dandare21.fracturedutils.FracturedUtils;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, FracturedUtils.MOD_ID);

    public static final RegistryObject<Item> VOID_HERALD_SPAWN_EGG =
            ITEMS.register("void_herald_spawn_egg", () ->
                    new ForgeSpawnEggItem(ModEntities.VOID_HERALD, 0x180B29, 0x7B2CBF, new Item.Properties())
            );

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}

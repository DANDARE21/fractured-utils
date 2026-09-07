package net.dandare21.fracturedutils.puppet.registry;

import net.dandare21.fracturedutils.FracturedUtils;
import net.dandare21.fracturedutils.puppet.boss.VoidHeraldBoss;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, FracturedUtils.MOD_ID);

    public static final RegistryObject<EntityType<VoidHeraldBoss>> VOID_HERALD =
            ENTITY_TYPES.register("void_herald", () ->
                    EntityType.Builder.of(VoidHeraldBoss::new, MobCategory.MONSTER)
                            .sized(1.2F, 2.8F)
                            .clientTrackingRange(10)
                            .build(new ResourceLocation(FracturedUtils.MOD_ID, "void_herald").toString())
            );

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}

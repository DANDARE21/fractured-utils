package net.dandare21.fracturedutils.puppet.capability;

import net.dandare21.fracturedutils.FracturedUtils;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Capability provider exposing thread-safe LazyOptional<IPuppetHandler> for any Mob.
 */
public class PuppetCapabilityProvider implements ICapabilityProvider {
    public static final Capability<IPuppetHandler> PUPPET_HANDLER = CapabilityManager.get(new CapabilityToken<>() {});
    public static final ResourceLocation IDENTIFIER = new ResourceLocation(FracturedUtils.MOD_ID, "puppet_handler");

    private final IPuppetHandler backend;
    private final LazyOptional<IPuppetHandler> optional;

    public PuppetCapabilityProvider(Mob mob) {
        this.backend = new PuppetHandlerImpl(mob);
        this.optional = LazyOptional.of(() -> this.backend);
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == PUPPET_HANDLER) {
            return this.optional.cast();
        }
        return LazyOptional.empty();
    }

    public void invalidate() {
        this.optional.invalidate();
    }
}

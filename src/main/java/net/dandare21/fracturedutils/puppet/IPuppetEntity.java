package net.dandare21.fracturedutils.puppet;

/**
 * Legacy puppet interface.
 *
 * @deprecated Deprecated in Boss Puppet Framework v2.0. Use {@link net.dandare21.fracturedutils.puppet.capability.IPuppetHandler}
 * via {@link net.dandare21.fracturedutils.puppet.capability.PuppetCapabilityProvider#PUPPET_HANDLER} attached to any {@link net.minecraft.world.entity.Mob}
 * instead of interface inheritance.
 */
@Deprecated(forRemoval = true)
public interface IPuppetEntity {
    /**
     * @return The entity's persistent PuppetController instance.
     */
    PuppetController getPuppetController();

    /**
     * Called during entity initialization to register custom actions.
     *
     * @param registry Builder registry mapping ResourceLocations to PuppetActions.
     */
    default void registerPuppetActions(PuppetActionRegistry registry) {
        // Optional override for custom mob actions
    }
}

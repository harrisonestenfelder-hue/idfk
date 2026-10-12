package com.spears.mixin;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ClientPlayerInteractionManager.class)
public interface InteractionManagerAccessor {
    /** Sends the selected-slot packet ONLY if the slot changed since the last one sent (no duplicates). */
    @Invoker("syncSelectedSlot")
    void spears$syncSelectedSlot();
}

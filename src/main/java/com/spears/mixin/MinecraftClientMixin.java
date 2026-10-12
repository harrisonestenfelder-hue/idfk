package com.spears.mixin;

import com.spears.SpearsClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Passive: only OBSERVES your left-clicks for the training tracker. Never cancels or changes an attack. */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Inject(method = "doAttack", at = @At("HEAD"))
    private void spears$observe(CallbackInfoReturnable<Boolean> cir) {
        SpearsClient.onAttackAttempt((MinecraftClient) (Object) this);
    }
}

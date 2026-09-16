package dev.catlean.mixin;

import dev.catlean.util.RotationManager;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class MixinClientPlayerEntity {

    @Inject(method = "sendMovementPackets", at = @At("HEAD"))
    private void cm$preSendMovement(CallbackInfo ci) {
        RotationManager.preSendMovement((ClientPlayerEntity) (Object) this);
    }

    @Inject(method = "sendMovementPackets", at = @At("TAIL"))
    private void cm$postSendMovement(CallbackInfo ci) {
        RotationManager.postSendMovement((ClientPlayerEntity) (Object) this);
    }
}

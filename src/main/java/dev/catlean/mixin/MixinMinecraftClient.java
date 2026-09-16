package dev.catlean.mixin;

import dev.catlean.CatleanClient;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;

@Mixin(MinecraftClient.class)
public abstract class MixinMinecraftClient {

    @Unique
    private final Set<Integer> cm$held = new HashSet<>();

    @Inject(method = "tick", at = @At("HEAD"))
    private void cm$tick(CallbackInfo ci) {
        MinecraftClient mc = (MinecraftClient)(Object)this;
        if (mc.getWindow() == null) return;
        long win = mc.getWindow().getHandle();

        cm$checkKey(win, GLFW.GLFW_KEY_INSERT, () -> {
            if (mc.currentScreen == null && CatleanClient.CLICK_GUI != null) {
                mc.setScreen(CatleanClient.CLICK_GUI);
            }
        });

        if (mc.player == null || CatleanClient.MODULE_MANAGER == null) return;
        for (var mod : CatleanClient.MODULE_MANAGER.getModules()) {
            int key = mod.getKeybind();
            if (key <= GLFW.GLFW_KEY_UNKNOWN) continue;
            cm$checkKey(win, key, mod::toggle);
        }
    }

    @Unique
    private void cm$checkKey(long win, int key, Runnable action) {
        boolean pressed = GLFW.glfwGetKey(win, key) == GLFW.GLFW_PRESS;
        if (pressed  && cm$held.add(key)) action.run();
        if (!pressed)  cm$held.remove(key);
    }
}

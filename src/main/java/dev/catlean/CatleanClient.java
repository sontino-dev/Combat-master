package dev.catlean;

import dev.catlean.gui.ClickGUI;
import dev.catlean.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;

public class CatleanClient implements ClientModInitializer {

    public static final String MOD_ID  = "combatmaster";
    public static final String NAME    = "Combat Master";
    public static final String VERSION = "1.0.0";

    public static CatleanClient   INSTANCE;
    public static MinecraftClient mc;
    public static ModuleManager   MODULE_MANAGER;
    public static ClickGUI        CLICK_GUI;

    @Override
    public void onInitializeClient() {
        INSTANCE       = this;
        mc             = MinecraftClient.getInstance();
        MODULE_MANAGER = new ModuleManager();
        CLICK_GUI      = new ClickGUI();
        MODULE_MANAGER.init();

        // FIX BUG-01: replaced broken MixinGameRenderer (mc.getRenderLayer() did not exist
        // in 1.21.4 Yarn). HudRenderCallback is the correct Fabric hook for HUD rendering.
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.world == null) return;

            // tickCounter.getTickDelta(false) — returns partial tick for smooth rendering
            float delta = tickCounter.getTickDelta(false);

            if (MODULE_MANAGER != null) {
                for (var mod : MODULE_MANAGER.getModules())
                    if (mod.isEnabled()) mod.onRender(drawContext, delta);
            }

            if (client.currentScreen == null) {
                ClickGUI.renderHUD(drawContext, client);
            }
        });

        System.out.println("[" + NAME + "] v" + VERSION + " loaded — INSERT to open GUI");
    }
}

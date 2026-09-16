package dev.catlean.gui;

import dev.catlean.CatleanClient;
import dev.catlean.module.Category;
import dev.catlean.module.Module;
import dev.catlean.module.Module.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class ClickGUI extends Screen {

    // ── Palette ──────────────────────────────────────────────────────────────
    static final int BG_PANEL     = 0xEA08071A;
    static final int BG_SETTING   = 0xEA050412;
    static final int SEP          = 0xFF14122A;
    static final int BORDER       = 0xFF220A38;
    static final int SHADOW       = 0x55000000;
    static final int TEXT_ON      = 0xFFECECFF;
    static final int TEXT_OFF     = 0xFF50507A;
    static final int TEXT_DIM     = 0xFF383860;
    static final int SLIDER_TRACK = 0xFF18163A;
    static final int HANDLE       = 0xFFFFFFFF;
    static final int BOOL_ON_BG   = 0xFF0F3A1A;
    static final int BOOL_OFF_BG  = 0xFF2A0A0A;
    static final int BOOL_ON_TXT  = 0xFF22EE66;
    static final int BOOL_OFF_TXT = 0xFFEE2244;
    static final int ENUM_BG      = 0xFF0A0A2A;
    static final int ENUM_TXT     = 0xFF88BBFF;
    static final int KEY_BG       = 0xFF08082A;
    static final int KEY_TXT      = 0xFF4466BB;

    static final Map<Category, int[]> CAT_AC = new EnumMap<>(Category.class);
    static {
        CAT_AC.put(Category.COMBAT,   new int[]{ 0xFFFF2244, 0xFFFF6644, 0xFF3A0010 });
        CAT_AC.put(Category.PLAYER,   new int[]{ 0xFFAA44FF, 0xFFDD88FF, 0xFF22002A });
        CAT_AC.put(Category.MOVEMENT, new int[]{ 0xFF2266FF, 0xFF44AAFF, 0xFF001030 });
        CAT_AC.put(Category.RENDER,   new int[]{ 0xFF22EE88, 0xFF44FFCC, 0xFF003018 });
        CAT_AC.put(Category.MISC,     new int[]{ 0xFFFFAA22, 0xFFFFDD44, 0xFF302000 });
    }

    static final Map<String, String> MOD_ICON = new HashMap<>();
    static {
        MOD_ICON.put("KillAura",     "\u2716");
        MOD_ICON.put("MacePvP",      "\u25C6");
        MOD_ICON.put("WebPvP",       "\u2297");
        MOD_ICON.put("AimAssistant", "\u25CE");
        MOD_ICON.put("TriggerBot",   "\u25BA");
        MOD_ICON.put("CartPvP",      "\u25CB");
        MOD_ICON.put("AutoTotem",    "\u25C9");
        MOD_ICON.put("AutoArmor",    "\u25A4");
        MOD_ICON.put("AutoGapple",   "\u2299");
    }

    static final int PW = 115;
    static final int MH = 16;
    static final int HH = 19;
    static final int SH = 14;

    private final List<Panel> panels = new ArrayList<>();

    public ClickGUI() {
        super(Text.literal("Combat Master"));
        int x = 8;
        for (Category cat : Category.values()) {
            panels.add(new Panel(cat, x, 8, PW));
            x += PW + 6;
        }
    }

    @Override protected void init() {}

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, 0x99000000);
        String wm = "Combat Master v1.0  |  INSERT";
        int    tw = client.textRenderer.getWidth(wm);
        ctx.drawText(client.textRenderer, wm, width - tw - 4, height - 11, 0xFF2A1A4A, true);
        ctx.drawText(client.textRenderer, wm, width - tw - 4, height - 12, 0xFF554477, false);
        for (Panel p : panels) p.render(ctx, mx, my);
    }

    @Override public boolean mouseClicked (double mx, double my, int btn)                       { for (Panel p : panels) if (p.click(mx, my, btn))     return true; return super.mouseClicked(mx, my, btn); }
    @Override public boolean mouseReleased(double mx, double my, int btn)                       { panels.forEach(p -> p.release(mx, my, btn)); return super.mouseReleased(mx, my, btn); }
    @Override public boolean mouseDragged (double mx, double my, int btn, double dx, double dy) { for (Panel p : panels) if (p.drag(mx, my, btn))      return true; return super.mouseDragged(mx, my, btn, dx, dy); }
    @Override public boolean mouseScrolled(double mx, double my, double hx, double vy)         { for (Panel p : panels) if (p.scroll(mx, my, vy))    return true; return super.mouseScrolled(mx, my, hx, vy); }
    @Override public boolean keyPressed   (int kc, int sc, int mods)                           { if (kc == GLFW.GLFW_KEY_ESCAPE) { client.setScreen(null); return true; } for (Panel p : panels) if (p.key(kc)) return true; return super.keyPressed(kc, sc, mods); }
    @Override public boolean shouldPause() { return false; }

    public static void renderHUD(DrawContext ctx, MinecraftClient mc) {
        if (CatleanClient.MODULE_MANAGER == null || mc.player == null) return;

        List<Module> enabled = CatleanClient.MODULE_MANAGER.getModules()
                .stream().filter(Module::isEnabled).toList();
        if (enabled.isEmpty()) return;

        List<Module> sorted = new ArrayList<>(enabled);
        sorted.sort(Comparator.comparingInt(m -> -mc.textRenderer.getWidth(m.getName())));

        int sw = mc.getWindow().getScaledWidth();
        int y  = 4;

        for (Module mod : sorted) {
            int[]  ac   = CAT_AC.getOrDefault(mod.getCategory(), new int[]{ 0xFFFFFFFF, 0xFFCCCCCC, 0xFF000000 });
            String name = mod.getName();
            int    tw   = mc.textRenderer.getWidth(name);
            int    x    = sw - tw - 8;

            ctx.fill(x - 3, y - 1, sw - 2, y + 9, 0x88000000);
            ctx.fillGradient(sw - 3, y - 1, sw - 2, y + 9, ac[0], ac[1]);
            ctx.drawText(mc.textRenderer, name, x, y, TEXT_ON, true);
            y += 11;
        }
    }

    // =========================================================================
    class Panel {
        final Category cat;
        int     x, y, w;
        boolean collapsed;
        boolean dragging;
        double  dox, doy;
        Module  expanded;
        boolean bindMode;
        Module  bindTarget;
        Setting<?> sliderSetting;

        Panel(Category cat, int x, int y, int w) {
            this.cat = cat; this.x = x; this.y = y; this.w = w;
        }

        int[]        ac()   { return CAT_AC.getOrDefault(cat, new int[]{0xFFFFFFFF,0xFFCCCCCC,0xFF111111}); }
        List<Module> mods() { return CatleanClient.MODULE_MANAGER.getModulesByCategory(cat); }

        int totalH() {
            if (collapsed) return HH;
            int h = HH + mods().size() * MH;
            if (expanded != null) h += expanded.getSettings().size() * SH + 4;
            return h;
        }

        void render(DrawContext ctx, int mx, int my) {
            int[] ac = ac();
            int   h  = totalH();

            ctx.fill(x + 4, y + 4, x + w + 4, y + h + 4, SHADOW);
            ctx.fill(x, y, x + w, y + h, BG_PANEL);
            ctx.fillGradient(x, y, x + w, y + HH, ac[0] | 0xEE000000, ac[2] | 0xFF000000);
            ctx.fill(x, y, x + w, y + 1, lerpC(ac[0], 0xFFFFFFFF, 0.3f) | 0xFF000000);

            String catIcon = getCatIcon(cat);
            ctx.drawText(client.textRenderer, catIcon, x + 4, y + 5, 0xFFFFFFFF, true);
            ctx.drawText(client.textRenderer, cat.displayName, x + 15, y + 5, 0xFFFFFFFF, true);

            String arr = collapsed ? "\u25B6" : "\u25BC";
            ctx.drawText(client.textRenderer, arr, x + w - 11, y + 5, 0xAAFFFFFF, false);

            if (collapsed) { border(ctx, x, y, w, h, BORDER); return; }

            ctx.fill(x, y + HH, x + w, y + HH + 1, ac[0]);

            List<Module> modules = mods();
            int ry = y + HH;

            for (Module mod : modules) {
                boolean hov = in(mx, my, x, ry, w, MH);
                int     bg  = mod.isEnabled() ? darken(ac[2], 0xCC) : (hov ? 0xCC120A22 : BG_PANEL);
                ctx.fill(x, ry, x + w, ry + MH, bg);

                if (mod.isEnabled()) {
                    ctx.fillGradient(x, ry, x + 3, ry + MH, ac[0], ac[1]);
                } else {
                    ctx.fill(x, ry, x + 1, ry + MH, TEXT_DIM);
                }

                String icon      = MOD_ICON.getOrDefault(mod.getName(), "\u2022");
                int    iconColor = mod.isEnabled() ? ac[0] : TEXT_DIM;
                ctx.drawText(client.textRenderer, icon, x + 5, ry + 4, iconColor, false);

                int nameColor = mod.isEnabled() ? TEXT_ON : TEXT_OFF;
                ctx.drawText(client.textRenderer, mod.getName(), x + 16, ry + 4, nameColor, false);

                if (mod.getKeybind() != GLFW.GLFW_KEY_UNKNOWN) {
                    String kn = keyName(mod.getKeybind());
                    int    kw = client.textRenderer.getWidth(kn);
                    ctx.fill(x + w - kw - 7, ry + 3, x + w - 2, ry + MH - 3, KEY_BG);
                    ctx.drawText(client.textRenderer, kn, x + w - kw - 5, ry + 4, KEY_TXT, false);
                }

                if (expanded == mod) {
                    ry += MH;
                    int sh = mod.getSettings().size() * SH + 4;
                    ctx.fill(x, ry, x + w, ry + sh, BG_SETTING);
                    ctx.fillGradient(x, ry, x + 3, ry + sh, ac[0], ac[1]);
                    int sy = ry + 2;
                    for (Setting<?> s : mod.getSettings()) {
                        renderSetting(ctx, s, sy, mx, my);
                        sy += SH;
                    }
                    ry += sh;
                    ctx.fill(x, ry, x + w, ry + 1, ac[0]);
                    continue;
                }

                ctx.fill(x + 4, ry + MH - 1, x + w - 4, ry + MH, SEP);
                ry += MH;
            }

            if (bindMode && bindTarget != null) {
                ctx.fill(x, ry, x + w, ry + 14, 0xFF0A0A1A);
                String hint = "  > Press key for " + bindTarget.getName();
                ctx.drawText(client.textRenderer, hint, x + 2, ry + 3, ac[0], false);
            }

            border(ctx, x, y, w, h, BORDER);
        }

        void renderSetting(DrawContext ctx, Setting<?> s, int sy, int mx, int my) {
            ctx.drawText(client.textRenderer, s.name, x + 6, sy + 3, TEXT_DIM, false);

            if (s instanceof BooleanSetting bs) {
                boolean on  = bs.isEnabled();
                int     bx  = x + w - 24;
                int     bg  = on ? BOOL_ON_BG : BOOL_OFF_BG;
                int     tc  = on ? BOOL_ON_TXT : BOOL_OFF_TXT;
                String  lbl = on ? "ON" : "OFF";
                ctx.fill(bx, sy + 2, x + w - 3, sy + SH - 2, bg);
                ctx.drawText(client.textRenderer, lbl, bx + 3, sy + 3, tc, false);

            } else if (s instanceof FloatSetting fs) {
                int   slx = x + 5, slw = w - 10;
                float t   = (fs.getValue() - fs.min) / (fs.max - fs.min);
                int   fw  = (int)(slw * t);
                ctx.fill(slx, sy + 9, slx + slw, sy + 12, SLIDER_TRACK);
                ctx.fillGradient(slx, sy + 9, slx + fw, sy + 12,
                        ac()[0] | 0xFF000000, ac()[1] | 0xFF000000);
                ctx.fill(slx + fw - 1, sy + 7, slx + fw + 1, sy + 14, HANDLE);
                String val = String.format("%.2f", fs.getValue());
                int    vw  = client.textRenderer.getWidth(val);
                ctx.drawText(client.textRenderer, val, x + w - vw - 4, sy + 3, TEXT_ON, false);

            } else if (s instanceof IntSetting is) {
                int   slx = x + 5, slw = w - 10;
                float t   = (float)(is.getValue() - is.min) / (is.max - is.min);
                int   fw  = (int)(slw * t);
                ctx.fill(slx, sy + 9, slx + slw, sy + 12, SLIDER_TRACK);
                ctx.fillGradient(slx, sy + 9, slx + fw, sy + 12,
                        ac()[0] | 0xFF000000, ac()[1] | 0xFF000000);
                ctx.fill(slx + fw - 1, sy + 7, slx + fw + 1, sy + 14, HANDLE);
                String val = String.valueOf(is.getValue());
                int    vw  = client.textRenderer.getWidth(val);
                ctx.drawText(client.textRenderer, val, x + w - vw - 4, sy + 3, TEXT_ON, false);

            } else if (s instanceof EnumSetting<?> es) {
                String val = es.getValue().name();
                int    vw  = client.textRenderer.getWidth(val);
                ctx.fill(x + w - vw - 8, sy + 2, x + w - 3, sy + SH - 2, ENUM_BG);
                ctx.drawText(client.textRenderer, val, x + w - vw - 5, sy + 3, ENUM_TXT, false);
            }
        }

        boolean click(double mx, double my, int btn) {
            int h = totalH();
            if (!in(mx, my, x, y, w, h)) return false;

            if (in(mx, my, x, y, w, HH)) {
                if (btn == 0) { dragging = true; dox = mx - x; doy = my - y; }
                if (btn == 1) collapsed = !collapsed;
                return true;
            }
            if (collapsed) return true;

            List<Module> modules = mods();
            int ry = y + HH;

            for (Module mod : modules) {
                if (expanded == mod) {
                    // FIX BUG-05: module row click was silently swallowed when settings were open.
                    // The old code jumped straight to checking the settings area, so clicking
                    // the module header had zero effect (no toggle, no collapse). Now we check
                    // the module row first, allowing toggle (LMB) or collapse (RMB) even while
                    // the settings panel is visible.
                    if (in(mx, my, x, ry, w, MH)) {
                        if (!(bindMode && bindTarget == mod)) {
                            switch (btn) {
                                case 0 -> mod.toggle();
                                case 1 -> expanded = null;
                                case 2 -> { bindMode = true; bindTarget = mod; }
                            }
                        }
                        return true;
                    }

                    int sh = mod.getSettings().size() * SH + 4;
                    if (in(mx, my, x, ry + MH, w, sh)) {
                        int sy = ry + MH + 2;
                        for (Setting<?> s : mod.getSettings()) {
                            if (my >= sy && my < sy + SH) {
                                settingClick(s, mx, sy, btn);
                                return true;
                            }
                            sy += SH;
                        }
                    }
                    ry += MH + sh;
                    continue;
                }

                if (in(mx, my, x, ry, w, MH)) {
                    if (bindMode && bindTarget == mod) return true;
                    switch (btn) {
                        case 0 -> mod.toggle();
                        case 1 -> expanded = (expanded == mod) ? null : mod;
                        case 2 -> { bindMode = true; bindTarget = mod; }
                    }
                    return true;
                }
                ry += MH;
            }
            return true;
        }

        void settingClick(Setting<?> s, double mx, int sy, int btn) {
            if      (s instanceof BooleanSetting bs) { bs.toggle(); }
            else if (s instanceof EnumSetting<?> es) { es.next(); }
            else if (s instanceof FloatSetting   fs) { sliderSetting = fs; applySliderF(fs, mx); }
            else if (s instanceof IntSetting     is) { sliderSetting = is; applySliderI(is, mx); }
        }

        void applySliderF(FloatSetting fs, double mx) {
            float t = (float)((mx - (x + 5)) / (w - 10));
            t = Math.max(0f, Math.min(1f, t));
            fs.setValue(fs.min + t * (fs.max - fs.min));
        }

        void applySliderI(IntSetting is, double mx) {
            float t = (float)((mx - (x + 5)) / (w - 10));
            t = Math.max(0f, Math.min(1f, t));
            is.setValue((int)(is.min + t * (is.max - is.min)));
        }

        void release(double mx, double my, int btn) {
            dragging      = false;
            sliderSetting = null;
        }

        boolean drag(double mx, double my, int btn) {
            if (dragging && btn == 0) {
                x = Math.max(0, (int)(mx - dox));
                y = Math.max(0, (int)(my - doy));
                return true;
            }
            if (sliderSetting != null && btn == 0) {
                if (sliderSetting instanceof FloatSetting fs) applySliderF(fs, mx);
                if (sliderSetting instanceof IntSetting   is) applySliderI(is, mx);
                return true;
            }
            return false;
        }

        // FIX BUG-08: was consuming the scroll event (return true) without doing anything
        // with the scroll delta (vy). This blocked the rest of the UI from receiving scroll
        // events while the cursor was over any panel. Now returns false until scroll is
        // properly implemented (scrollOffset + clip).
        boolean scroll(double mx, double my, double vy) {
            return false;
        }

        boolean key(int kc) {
            if (!bindMode || bindTarget == null) return false;
            bindTarget.setKeybind(kc == GLFW.GLFW_KEY_ESCAPE ? GLFW.GLFW_KEY_UNKNOWN : kc);
            bindMode = false; bindTarget = null;
            return true;
        }

        boolean in(double mx, double my, int bx, int by, int bw, int bh) {
            return mx >= bx && mx < bx + bw && my >= by && my < by + bh;
        }

        void border(DrawContext ctx, int bx, int by, int bw, int bh, int col) {
            ctx.fill(bx,          by,          bx + bw,     by + 1,      col);
            ctx.fill(bx,          by + bh - 1, bx + bw,     by + bh,     col);
            ctx.fill(bx,          by,          bx + 1,      by + bh,     col);
            ctx.fill(bx + bw - 1, by,          bx + bw,     by + bh,     col);
        }

        int darken(int c, int alpha) { return (c & 0x00FFFFFF) | (alpha << 24); }

        String keyName(int key) {
            if (key == GLFW.GLFW_KEY_UNKNOWN) return "?";
            String n = GLFW.glfwGetKeyName(key, 0);
            return n != null ? n.toUpperCase() : "K" + key;
        }

        String getCatIcon(Category cat) {
            return switch (cat) {
                case COMBAT   -> "\u2694";
                case PLAYER   -> "\u2699";
                case MOVEMENT -> "\u25B6";
                case RENDER   -> "\u25C8";
                case MISC     -> "\u2736";
            };
        }
    }

    static int lerpC(int a, int b, float t) {
        int aA=(a>>24)&0xFF, aR=(a>>16)&0xFF, aG=(a>>8)&0xFF, aB=a&0xFF;
        int bA=(b>>24)&0xFF, bR=(b>>16)&0xFF, bG=(b>>8)&0xFF, bB=b&0xFF;
        return ((int)(aA+(bA-aA)*t)<<24)|((int)(aR+(bR-aR)*t)<<16)
              |((int)(aG+(bG-aG)*t)<<8) | (int)(aB+(bB-aB)*t);
    }
}

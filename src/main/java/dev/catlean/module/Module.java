package dev.catlean.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {

    protected static final MinecraftClient mc = MinecraftClient.getInstance();

    private final String   name;
    private final String   description;
    private final Category category;
    private       boolean  enabled;
    private       int      keybind;

    protected final List<Setting<?>> settings = new ArrayList<>();

    public Module(String name, String description, Category category, int keybind) {
        this.name        = name;
        this.description = description;
        this.category    = category;
        this.keybind     = keybind;
    }

    public void toggle() {
        enabled = !enabled;
        if (enabled) onEnable(); else onDisable();
    }

    public void setEnabled(boolean state) {
        if (enabled != state) toggle();
    }

    public void onEnable()  {}
    public void onDisable() {}
    public void onTick()    {}
    public void onRender(DrawContext ctx, float delta) {}

    protected <T extends Setting<?>> T addSetting(T s) {
        settings.add(s);
        return s;
    }

    public String           getName()        { return name; }
    public String           getDescription() { return description; }
    public Category         getCategory()    { return category; }
    public boolean          isEnabled()      { return enabled; }
    public int              getKeybind()     { return keybind; }
    public void             setKeybind(int k){ this.keybind = k; }
    public List<Setting<?>> getSettings()    { return settings; }

    public static abstract class Setting<T> {
        public final String name;
        protected T value;

        public Setting(String name, T defaultValue) {
            this.name  = name;
            this.value = defaultValue;
        }

        public T    getValue()      { return value; }
        public void setValue(T val) { this.value = val; }
    }

    public static class BooleanSetting extends Setting<Boolean> {
        public BooleanSetting(String name, boolean def) { super(name, def); }
        public void    toggle()    { value = !value; }
        public boolean isEnabled() { return value; }
    }

    public static class FloatSetting extends Setting<Float> {
        public final float min, max;
        public FloatSetting(String name, float def, float min, float max) {
            super(name, def);
            this.min = min; this.max = max;
        }
    }

    public static class IntSetting extends Setting<Integer> {
        public final int min, max;
        public IntSetting(String name, int def, int min, int max) {
            super(name, def);
            this.min = min; this.max = max;
        }
    }

    public static class EnumSetting<E extends Enum<E>> extends Setting<E> {
        public final E[] values;

        @SuppressWarnings("unchecked")
        public EnumSetting(String name, E def) {
            super(name, def);
            this.values = (E[]) def.getDeclaringClass().getEnumConstants();
        }

        public void next() {
            value = values[(value.ordinal() + 1) % values.length];
        }
    }
}

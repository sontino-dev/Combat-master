package dev.catlean.module;

import dev.catlean.module.modules.combat.*;
import dev.catlean.module.modules.player.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ModuleManager {

    private final List<Module> modules = new ArrayList<>();

    public void init() {
        register(new KillAura());
        register(new MacePvP());
        register(new WebPvP());
        register(new AimAssistant());
        register(new TriggerBot());
        register(new CartPvP());
        register(new AutoTotem());
        register(new AutoArmor());
        register(new AutoGapple());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            for (Module m : modules) {
                if (m.isEnabled()) m.onTick();
            }
        });
    }

    public void register(Module m) { modules.add(m); }

    public Module getModule(String name) {
        return modules.stream()
                .filter(m -> m.getName().equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T getModule(Class<T> cls) {
        return (T) modules.stream().filter(m -> m.getClass() == cls).findFirst().orElse(null);
    }

    public List<Module> getModules() { return modules; }

    public List<Module> getModulesByCategory(Category cat) {
        return modules.stream().filter(m -> m.getCategory() == cat).collect(Collectors.toList());
    }
}

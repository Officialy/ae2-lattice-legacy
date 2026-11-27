package dev.ae2lattice.mixin;
import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;
import cpw.mods.fml.relauncher.FMLLaunchHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@LateMixin
public final class LateMixins implements ILateMixinLoader {
    @Override public String getMixinConfig() { return "mixins.ae2lattice.late.json"; }
    @Override public List<String> getMixins(Set<String> mods) {
        List<String> result = new ArrayList<>();
        if (mods.contains("appliedenergistics2")) {
            result.add("ContainerAccessor");
            result.add("CpuAccessor");
            result.add("TaskAccessor");
            result.add("CpuTrackerMixin");
            if (FMLLaunchHandler.side().isClient()) {
                result.add("ClientCpuMixin");
                result.add("ClientBaseGuiMixin");
                result.add("ClientStatusMixin");
            }
        }
        return result;
    }
}

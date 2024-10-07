package android.content.pm;

import java.util.List;

import dev.rikka.tools.refine.RefineAs;

@RefineAs(PackageManager.class)
public abstract class PackageManagerHidden extends PackageManager {
    public native List<PackageInfo> getInstalledPackagesAsUser(int flags, int userId);
}

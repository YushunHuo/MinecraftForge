/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.fml.loading.targets;

import cpw.mods.jarhandling.SecureJar;
import cpw.mods.modlauncher.api.ServiceRunner;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.LibraryFinder;
import net.minecraftforge.fml.loading.VersionInfo;
import net.minecraftforge.api.distmarker.Dist;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

public abstract class CommonServerLaunchHandler extends CommonLaunchHandler {
    @Override public Dist getDist()  { return Dist.DEDICATED_SERVER; }
    @Override public String getNaming() { return "srg"; }
    @Override public boolean isProduction() { return true; }

    @Override
    public ServiceRunner launchService(String[] arguments, ModuleLayer layer) {
        return () -> {
            Class.forName(layer.findModule("minecraft").orElseThrow(),"net.minecraft.server.Main").getMethod("main", String[].class).invoke(null, (Object)arguments);
        };
    }

    @Override
    public LocatedPaths getMinecraftPaths() {
        final var vers = FMLLoader.versionInfo();
        var mc = LibraryFinder.findPathForMaven("net.minecraft", "server", "", "srg", vers.mcAndMCPVersion());
        var mcextra = LibraryFinder.findPathForMaven("net.minecraft", "server", "", "extra", vers.mcAndMCPVersion());
        var mcextra_filtered = SecureJar.from( // We only want it for it's resources. So filter everything else out.
            (path, base) -> {
                return path.equals("META-INF/versions/") || // This is required because it bypasses our filter for the manifest, and it's a multi-release jar.
                     (!path.endsWith(".class") &&
                      !path.startsWith("META-INF/"));
            }, mcextra
        );

        // Todo in 1.20.2: Change the default filter to null instead of always true.
        BiPredicate<String, String> filter = (path, base) -> true;
        final BiPredicate<String, String> alwaysTrueFilter = filter;

        var mcstream = Stream.<Path>builder().add(mc).add(mcextra_filtered.getRootPath());
        var modstream = Stream.<List<Path>>builder();

        filter = processMCStream(vers, mcstream, filter, modstream);

        // Ensure backwards compatibility if anything overrides processMCStream()
        // and expects the default always true filter by only changing it to null afterwards if left unchanged.
        if (filter == alwaysTrueFilter)
            filter = null;

        var fmlcore = LibraryFinder.findPathForMaven(vers.forgeGroup(), "fmlcore", "", "", vers.mcAndForgeVersion());
        var javafmllang = LibraryFinder.findPathForMaven(vers.forgeGroup(), "javafmllanguage", "", "", vers.mcAndForgeVersion());
        var lowcodelang = LibraryFinder.findPathForMaven(vers.forgeGroup(), "lowcodelanguage", "", "", vers.mcAndForgeVersion());
        var mclang = LibraryFinder.findPathForMaven(vers.forgeGroup(), "mclanguage", "", "", vers.mcAndForgeVersion());

        return new LocatedPaths(mcstream.build().toList(), filter, modstream.build().toList(), List.of(fmlcore, javafmllang, lowcodelang, mclang));
    }

    protected abstract BiPredicate<String, String> processMCStream(VersionInfo versionInfo, Stream.Builder<Path> mc, BiPredicate<String, String> filter, Stream.Builder<List<Path>> mods);
}

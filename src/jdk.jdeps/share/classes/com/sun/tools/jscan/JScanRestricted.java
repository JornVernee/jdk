/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.sun.tools.jscan;

import jdk.internal.joptsimple.*;

import java.io.File;
import java.io.IOException;
import java.lang.constant.ClassDesc;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

class JScanRestricted {

    private final Log log;
    private final List<Path> classPaths;
    private final List<Path> modulePaths;
    private final List<String> rootModules;
    private final Runtime.Version version;
    private final Action action;

    private JScanRestricted(Log log, List<Path> classPaths, List<Path> modulePaths, List<String> rootModules, Runtime.Version version, Action action) {
        this.log = log;
        this.classPaths = classPaths;
        this.modulePaths = modulePaths;
        this.version = version;
        this.action = action;
        this.rootModules = rootModules;
    }

    public void run() throws MalformedURLException {
        List<ScannedModule> modulesToScan = new ArrayList<>();
        for (Path classPath : classPaths) {
            modulesToScan.add(new ScannedModule(classPath, "ALL-UNNAMED"));
        }
        ModuleFinder moduleFinder = ModuleFinder.of(modulePaths.toArray(Path[]::new));
        ModuleFinder systemModuleFinder = ModuleFinder.ofSystem();
        Deque<String> modulesToAdd = new ArrayDeque<>(rootModules);
        while(!modulesToAdd.isEmpty()) {
            String modName = modulesToAdd.poll();
            Optional<ModuleReference> refOpt = moduleFinder.find(modName);
            if (refOpt.isEmpty()) {
                log.error("Module not found: " + modName);
                continue;
            }
            ModuleReference ref = refOpt.get();
            URI location = ref.location().orElseThrow();
            Path path = Path.of(location.getPath());
            ModuleDescriptor descriptor = ref.descriptor();
            modulesToScan.add(new ScannedModule(path, descriptor.name()));
            descriptor.requires().forEach(r -> {
                // system modules are exempt from --enable-native-access (and they are not jar files)
                boolean isSystemModule = systemModuleFinder.find(r.name()).isPresent();
                if (!isSystemModule) {
                    modulesToAdd.add(r.name());
                }
            });
        }

        RestrictedMethodFinder finder = new RestrictedMethodFinder(version);
        Map<ScannedModule, Map<ClassDesc, List<RestrictedUse>>> allRestrictedMethods = new HashMap<>();
        for (ScannedModule mod : modulesToScan) {
            Path jar = mod.path();
            // jar files only for now
            if (!(Files.exists(jar) && Files.isRegularFile(jar) && jar.toString().endsWith(".jar"))) {
                log.error("File does not exist, or does not appear to be a regular jar file: " + jar);
                continue;
            }

            Map<ClassDesc, List<RestrictedUse>> restrictedMethods = finder.findRestrictedMethodReferences(jar);
            if (!restrictedMethods.isEmpty()) {
                allRestrictedMethods.put(mod, restrictedMethods);
            }
        }

        switch (action) {
            case PRINT -> printNativeAccess(allRestrictedMethods);
            case DUMP_ALL -> dumpAll(allRestrictedMethods);
        }
    }

    private void printNativeAccess(Map<ScannedModule, Map<ClassDesc, List<RestrictedUse>>> allRestrictedMethods) {
        StringJoiner sj = new StringJoiner(",");
        for (ScannedModule mod : allRestrictedMethods.keySet()) {
            sj.add(mod.moduleName());
        }
        log.println(sj.toString());
    }

    private void dumpAll(Map<ScannedModule, Map<ClassDesc, List<RestrictedUse>>> allRestrictedMethods) {
        allRestrictedMethods.forEach((module, perClass) -> {
            log.println(module.moduleName() + ":");
            if (perClass.isEmpty()) {
                log.println("  <no restricted methods>");
            } else {
                perClass.forEach((classDesc, restrictedUses) -> {
                    log.println("  " + classDesc.packageName() + "." + classDesc.displayName() + ":");
                    restrictedUses.forEach(use -> {
                        switch (use) {
                            case RestrictedUse.NativeMethodDecl(MethodRef nmd) ->
                                    log.println("    " + nmd + " is a native method declaration");
                            case RestrictedUse.RestrictedMethodRefs(MethodRef referent, Set<MethodRef> referees) -> {
                                log.println("    " + referent + " references restricted methods:");
                                referees.forEach(referee -> log.println("      " + referee));
                            }
                        }
                    });
                });
            }
        });
    }

    private record ScannedModule(Path path, String moduleName) {}

    public static void run(Log log, String[] args) throws MalformedURLException {
        OptionParser parser = new OptionParser(false);
        OptionSpec<Void> helpOpt = parser.acceptsAll(List.of("?", "h", "help"), "help").forHelp();
        OptionSpec<String> classPathOpt = parser.accepts(
                "class-path",
                "The class path as used at runtime")
                .withRequiredArg();
        OptionSpec<String> modulePathOpt = parser.accepts(
                "module-path",
                "The module path as used at runtime")
                .withRequiredArg();
        OptionSpec<String> releaseOpt = parser.accepts(
                "release",
                "The runtime version that will run the application")
                .withRequiredArg();
        OptionSpec<String> addModulesOpt = parser.accepts(
                "add-modules",
                "List of root modules to scan")
                .withRequiredArg();
        OptionSpecBuilder printNativeAccessOpt = parser.accepts(
                "print-native-access",
                "print a comma separated list of modules that can be passed directly to --enable-native-access");
        OptionSpecBuilder dumpAllOpt = parser.accepts(
                "dump-all",
                "dump all uses of restricted elements");
        parser.mutuallyExclusive(printNativeAccessOpt, dumpAllOpt);

        OptionSet optionSet;
        try {
            optionSet = parser.parse(args);
        } catch (OptionException oe) {
            throw new IllegalArgumentException("parsing options failed", oe);
        }

        if (optionSet.has(helpOpt)) {
            try {
                parser.printHelpOn(log.out());
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        List<Path> classPathJars = new ArrayList<>();
        if (optionSet.has(classPathOpt)) {
            String[] parts = optionSet.valueOf(classPathOpt).split(File.pathSeparator);
            for (String part : parts) {
                classPathJars.add(Path.of(part));
            }
        }

        List<Path> modulePaths = new ArrayList<>();
        if (optionSet.has(modulePathOpt)) {
            String[] parts = optionSet.valueOf(modulePathOpt).split(File.pathSeparator);
            for (String part : parts) {
                modulePaths.add(Path.of(part));
            }
        }

        Runtime.Version version = Runtime.version();
        if (optionSet.has(releaseOpt)) {
            String release = optionSet.valueOf(releaseOpt);
            try {
                version = Runtime.Version.parse(release);
            } catch (IllegalArgumentException e) {
                log.error("Invalid release: " + release + ", " + e.getMessage());
            }
        }

        Action action;
        if (optionSet.has(printNativeAccessOpt)) {
            action = Action.PRINT;
        } else if (optionSet.has(dumpAllOpt)) {
            action = Action.DUMP_ALL;
        } else {
            log.error("At least one of '--print-native-access', or '--dump-all' must be specified");
            return;
        }

        List<String> rootModules = List.of();
        if (optionSet.has(addModulesOpt)) {
            rootModules = List.of(optionSet.valueOf(addModulesOpt).split(","));
        }

        new JScanRestricted(log, classPathJars, modulePaths, rootModules, version, action).run();
    }

    private enum Action {
        DUMP_ALL,
        PRINT
    }
}

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
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.module.ResolvedModule;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

class JScanRestricted {

    private final Log log;
    private final List<Path> classPaths;
    private final List<Path> modulePaths;
    private final List<String> cmdRootModules;
    private final Runtime.Version version;
    private final Action action;

    private JScanRestricted(Log log, List<Path> classPaths, List<Path> modulePaths, List<String> cmdRootModules, Runtime.Version version, Action action) {
        this.log = log;
        this.classPaths = classPaths;
        this.modulePaths = modulePaths;
        this.version = version;
        this.action = action;
        this.cmdRootModules = cmdRootModules;
    }

    public void run() throws JScanFatalError {
        List<ScannedModule> modulesToScan = new ArrayList<>();
        findAllClassPathJars().forEach(modulesToScan::add);

        ModuleFinder moduleFinder = ModuleFinder.of(modulePaths.toArray(Path[]::new));
        List<String> rootModules = cmdRootModules;
        if (rootModules.contains("ALL-MODULE-PATH")) {
            rootModules = allModuleNames(moduleFinder);
        }
        Configuration config = Configuration.resolveAndBind(moduleFinder, List.of(systemConfiguration()), ModuleFinder.of(), rootModules);
        for (ResolvedModule m : config.modules()) {
            URI location = m.reference().location().orElseThrow();
            Path path = Path.of(location.getPath());
            checkRegularJar(path);
            modulesToScan.add(new ScannedModule(path, m.name()));
        }

        RestrictedMethodFinder finder = RestrictedMethodFinder.create(version);
        Map<ScannedModule, Map<ClassDesc, List<RestrictedUse>>> allRestrictedMethods = new HashMap<>();
        for (ScannedModule mod : modulesToScan) {
            Path jar = mod.path();
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

    // recursively look for all class path jars, starting at the root jars
    // in this.classPaths, and recursively following all Class-Path manifest
    // attributes
    private Stream<ScannedModule> findAllClassPathJars() throws JScanFatalError {
        Stream.Builder<ScannedModule> builder = Stream.builder();
        Deque<Path> classPathJars = new ArrayDeque<>(classPaths);
        while (!classPathJars.isEmpty()) {
            Path jar = classPathJars.poll();
            checkRegularJar(jar);
            String[] classPathAttribute = classPathAttribute(jar);
            Path parentDir = jar.getParent();
            for (String classPathEntry : classPathAttribute) {
                Path otherJar = parentDir != null
                        ? parentDir.resolve(classPathEntry)
                        : Path.of(classPathEntry);
                if (Files.exists(otherJar)) {
                    // Class-Path attribute specifies that jars that
                    // are not found are simply ignored. Do the same here
                    classPathJars.offer(otherJar);
                }
            }
            builder.add(new ScannedModule(jar, "ALL-UNNAMED"));
        }
        return builder.build();
    }

    private String[] classPathAttribute(Path jar) {
        try (JarFile jf = new JarFile(jar.toFile(), false, ZipFile.OPEN_READ, version)) {
           Manifest manifest = jf.getManifest();
           if (manifest != null) {
               String attrib = manifest.getMainAttributes().getValue("Class-Path");
               if (attrib != null) {
                   return attrib.split("\\s+");
               }
           }
           return new String[0];
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Configuration systemConfiguration() {
        ModuleFinder systemFinder = ModuleFinder.ofSystem();
        Configuration system = Configuration.resolve(systemFinder, List.of(Configuration.empty()), ModuleFinder.of(),
                allModuleNames(systemFinder)); // resolve all of them
        return system;
    }

    private List<String> allModuleNames(ModuleFinder finder) {
        return finder.findAll().stream().map(mr -> mr.descriptor().name()).toList();
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

    public static void run(Log log, String[] args) throws JScanFatalError {
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
            throw new JScanFatalError("Parsing options failed: " + oe.getMessage(), oe);
        }

        if (optionSet.has(helpOpt)) {
            try {
                parser.printHelpOn(log.out());
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        List<Path> classPathJars = parsePath(optionSet, classPathOpt);
        List<Path> modulePaths = parsePath(optionSet, modulePathOpt);

        Runtime.Version version = Runtime.version();
        if (optionSet.has(releaseOpt)) {
            String release = optionSet.valueOf(releaseOpt);
            try {
                version = Runtime.Version.parse(release);
            } catch (IllegalArgumentException e) {
                throw new JScanFatalError("Invalid release: " + release + ", " + e.getMessage());
            }
        }

        Action action;
        if (optionSet.has(printNativeAccessOpt)) {
            action = Action.PRINT;
        } else if (optionSet.has(dumpAllOpt)) {
            action = Action.DUMP_ALL;
        } else {
            throw new JScanFatalError("At least one of '--print-native-access', or '--dump-all' must be specified");
        }

        List<String> rootModules = List.of();
        if (optionSet.has(addModulesOpt)) {
            rootModules = List.of(optionSet.valueOf(addModulesOpt).split(","));
        }

        new JScanRestricted(log, classPathJars, modulePaths, rootModules, version, action).run();
    }

    private static List<Path> parsePath(OptionSet optionSet, OptionSpec<String> opt) throws JScanFatalError {
        List<Path> paths = new ArrayList<>();
        if (optionSet.has(opt)) {
            String[] parts = optionSet.valueOf(opt).split(File.pathSeparator);
            for (String part : parts) {
                Path path = Path.of(part);
                paths.add(path);
            }
        }
        return paths;
    }

    private static void checkRegularJar(Path path) throws JScanFatalError {
        if (!(Files.exists(path) && Files.isRegularFile(path) && path.toString().endsWith(".jar"))) {
            throw new JScanFatalError("File does not exist, or does not appear to be a regular jar file: " + path);
        }
    }

    private enum Action {
        DUMP_ALL,
        PRINT
    }
}

package com.sun.tools.jscan;

import jdk.internal.javac.Restricted;
import jdk.internal.joptsimple.OptionException;
import jdk.internal.joptsimple.OptionParser;
import jdk.internal.joptsimple.OptionSet;

import java.io.File;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.constantpool.InterfaceMethodRefEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.MethodRefEntry;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

public class JScanRestricted {

    private final Log log;
    private final List<Path> classPaths;
    private final List<Path> modulePaths;
    private final Runtime.Version version;
    private final Action action;

    private JScanRestricted(Log log, List<Path> classPaths, List<Path> modulePaths, Runtime.Version version, Action action) {
        this.log = log;
        this.classPaths = classPaths;
        this.modulePaths = modulePaths;
        this.version = version;
        this.action = action;
    }

    public void run() throws MalformedURLException {
        // loader to check for presence of @Restricted
        // only needs to find system classes
        ClassLoader loader = ClassLoader.getSystemClassLoader();

        List<ScannedModule> modulesToScan = new ArrayList<>();
        for (Path classPath : classPaths) {
            modulesToScan.add(new ScannedModule(classPath, "ALL-UNNAMED"));
        }
        for (ModuleReference ref : ModuleFinder.of(modulePaths.toArray(Path[]::new)).findAll()) {
            URI location = ref.location().orElseThrow();
            Path path = Path.of(location.getPath());
            modulesToScan.add(new ScannedModule(path, ref.descriptor().name()));
        }

        Map<ScannedModule, Map<ClassDesc, List<RestrictedUse>>> allRestrictedMethods = new HashMap<>();
        for (ScannedModule mod : modulesToScan) {
            Path jar = mod.path();
            // jar files only for now
            if (!(Files.exists(jar) && Files.isRegularFile(jar) && jar.toString().endsWith(".jar"))) {
                log.error("Jar file does not exist, or does not appear to be a regular jar file: " + jar);
                continue;
            }

            Map<ClassDesc, List<RestrictedUse>> restrictedMethods = findRestrictedMethodReferences(jar, loader);
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
                            case RestrictedUse.RestrictedMethodRef(MethodRef referent, Set<MethodRef> referees) -> {
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

    private Map<ClassDesc, List<RestrictedUse>> findRestrictedMethodReferences(Path jar, ClassLoader loader) {
        Map<ClassDesc, List<RestrictedUse>> restrictedMethods = new HashMap<>();
        forEachClassFile(jar, model -> {
            List<RestrictedUse> perClass = new ArrayList<>();
            model.methods().forEach(method -> {
                if (method.flags().has(AccessFlag.NATIVE)) {
                    perClass.add(new RestrictedUse.NativeMethodDecl(MethodRef.ofModel(method)));
                } else {
                    Set<MethodRef> perMethod = new HashSet<>();
                    method.code()
                            .ifPresent(code -> {
                                code.forEach(e -> {
                                    switch (e) {
                                        case InvokeInstruction invoke -> {
                                            Method referent = loadMethod(invoke.method(), loader);
                                            if (referent != null && isRestrictedMethod(referent)) {
                                                perMethod.add(MethodRef.ofMethod(referent));
                                            }
                                        }
                                        default -> {
                                        }
                                    }
                                });
                            });
                    if (!perMethod.isEmpty()) {
                        perClass.add(new RestrictedUse.RestrictedMethodRef(MethodRef.ofModel(method), Set.copyOf(perMethod)));
                    }
                }
            });
            if (!perClass.isEmpty()) {
                restrictedMethods.put(model.thisClass().asSymbol(), perClass);
            }
        });
        return restrictedMethods;
    }

    private sealed interface RestrictedUse {
        record RestrictedMethodRef(MethodRef referent, Set<MethodRef> referees) implements RestrictedUse {}
        record NativeMethodDecl(MethodRef decl) implements RestrictedUse {}
    }

    private boolean isRestrictedMethod(Method referent) {
        return referent.getAnnotation(Restricted.class) != null;
    }

    private Method loadMethod(MemberRefEntry method, ClassLoader loader) {
        ClassDesc owner;
        String methodName;
        MethodTypeDesc descriptor;
        switch (method) {
            case MethodRefEntry mre -> {
                owner = mre.owner().asSymbol();
                methodName = mre.name().stringValue();
                descriptor = mre.typeSymbol();
            }
            case InterfaceMethodRefEntry mre -> {
                owner = mre.owner().asSymbol();
                methodName = mre.name().stringValue();
                descriptor = mre.typeSymbol();
            }
            default -> throw new IllegalStateException("Unexpected type: " + method);
        }

        // FIXME for now there are no restricted <init> or <clinit> methods
        // but this might change in the future.
        if (methodName.equals("<init>") || methodName.equals("<clinit>")) {
            return null; // for now
        }

        String ownerDescriptor = owner.descriptorString();
        // why is this so hard
        String ownerBinaryName = ownerDescriptor.substring(1, ownerDescriptor.length() - 1)
                .replace('/', '.');

        try {
            Class<?> cls = Class.forName(ownerBinaryName, false, loader);
            Class<?>[] params = descriptor.parameterList().stream().map(cd -> {
                try {
                    return cd.resolveConstantDesc(MethodHandles.publicLookup());
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            }).toArray(Class<?>[]::new);

            return cls.getDeclaredMethod(methodName, params);
        } catch (ClassNotFoundException e) {
            log.error("Can not load class: " + ownerBinaryName);
        } catch (NoSuchMethodException e) {
            log.error("Can not find method: " + methodName + descriptor.displayDescriptor() + " in class: " + ownerBinaryName);
        }
        return null;
    }

    private record MethodRef(String methodName, MethodTypeDesc mtd) {
        public static MethodRef ofModel(MethodModel model) {
            return new MethodRef(model.methodName().stringValue(), model.methodTypeSymbol());
        }

        public static MethodRef ofMethod(Method referent) {
            MethodType type = MethodType.methodType(referent.getReturnType(), referent.getParameterTypes());
            return new MethodRef(referent.getName(), type.describeConstable().get());
        }

        @Override
        public String toString() {
            return methodName + mtd.displayDescriptor();
        }
    }

    private void forEachClassFile(Path jarFile, Consumer<ClassModel> action) {
        try (JarFile jf = new JarFile(jarFile.toFile(), false, ZipFile.OPEN_READ, version)) {
            jf.versionedStream().forEach(je -> {
                if (je.getName().endsWith(".class")) {
                    try {
                        ClassModel model = ClassFile.of().parse(jf.getInputStream(je).readAllBytes());
                        action.accept(model);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void run(Log log, String[] args) throws MalformedURLException {
        OptionParser parser = new OptionParser(false);
        parser.acceptsAll(List.of("?", "h", "help"), "help").forHelp();
        parser.accepts("class-path", "The class path as used at runtime").withRequiredArg();
        parser.accepts("module-path", "The module path as used at runtime").withRequiredArg();
        parser.accepts("release", "The runtime version that will run the application").withRequiredArg();
        parser.mutuallyExclusive(
            parser.accepts("print-native-access",
                "print a command separated list of modules that can be passed directly to --enable-native-access"),
            parser.accepts("dump-all",
                "dump all uses of restricted elements")
        );
        parser.nonOptions("modules to scan");

        OptionSet optionSet;
        try {
            optionSet = parser.parse(args);
        } catch (OptionException oe) {
            throw new IllegalArgumentException("parsing options failed", oe);
        }

        if (optionSet.has("h")) {
            try {
                parser.printHelpOn(log.out());
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        List<Path> classPathJars = new ArrayList<>();
        if (optionSet.has("class-path")) {
            String[] parts = optionSet.valueOf("class-path").toString().split(File.pathSeparator);
            for (String part : parts) {
                classPathJars.add(Path.of(part));
            }
        }

        List<Path> modulePaths = new ArrayList<>();
        if (optionSet.has("module-path")) {
            String[] parts = optionSet.valueOf("module-path").toString().split(File.pathSeparator);
            for (String part : parts) {
                modulePaths.add(Path.of(part));
            }
        }

        Runtime.Version version = Runtime.version();
        if (optionSet.has("release")) {
            String release = optionSet.valueOf("release").toString();
            try {
                version = Runtime.Version.parse(release);
            } catch (IllegalArgumentException e) {
                log.error("Invalid release: " + release + ", " + e.getMessage());
            }
        }

        Action action = null;
        if (optionSet.has("print-native-access")) {
            action = Action.PRINT;
        } else if (optionSet.has("dump-all")) {
            action = Action.DUMP_ALL;
        } else {
            log.error("At least one of '--print-native-access', or '--dump-all' must be specified");
            return;
        }

        new JScanRestricted(log, classPathJars, modulePaths, version, action).run();
    }

    private enum Action {
        DUMP_ALL,
        PRINT
    }
}

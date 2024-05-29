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
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class JScanRestricted {

    private final Log log;
    private final List<Path> jarFiles;
    private final List<URL> classPathURLs;

    private JScanRestricted(Log log, List<Path> jarFiles, List<URL> classPathURLs) {
        this.log = log;
        this.jarFiles = jarFiles;
        this.classPathURLs = classPathURLs;
    }

    public void run() throws MalformedURLException {
        ClassLoader loader = new URLClassLoader(classPathURLs.toArray(URL[]::new));

        Map<Path, Map<ClassDesc, Map<MethodRef, List<MethodRef>>>> allRestrictedMethods = new HashMap<>();
        for (Path jar : jarFiles) {
            if (!Files.exists(jar)) {
                log.error("Jar file does not exist: " + jar);
                continue;
            }

            Map<ClassDesc, Map<MethodRef, List<MethodRef>>> restrictedMethods = findRestrictedMethodReferences(jar, loader);
            allRestrictedMethods.put(jar, restrictedMethods);
        }

        allRestrictedMethods.forEach((jarFile, perClass) -> {
            log.println(jarFile.toString() + ":");
            if (perClass.isEmpty()) {
                log.println("  <no restricted methods>");
            } else {
                perClass.forEach((classDesc, perMethod) -> {
                    log.println("  " + classDesc.packageName() + "." + classDesc.displayName() + ":");
                    perMethod.forEach((referrent, referrees) -> {
                        log.println("    " + referrent + ":");
                        referrees.forEach(methodRef -> {
                            log.println("      " + methodRef);
                        });
                    });
                });
            }
        });
    }

    private Map<ClassDesc, Map<MethodRef, List<MethodRef>>> findRestrictedMethodReferences(Path jar, ClassLoader loader) {
        Map<ClassDesc, Map<MethodRef, List<MethodRef>>> restrictedMethods = new HashMap<>();
        forEachClassFile(jar, model -> {
            Map<MethodRef, List<MethodRef>> perClass = new HashMap<>();
            model.methods().forEach(method -> {
                List<MethodRef> perMethod = new ArrayList<>();
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
                    perClass.put(MethodRef.ofModel(method), perMethod);
                }
            });
            if (!perClass.isEmpty()) {
                restrictedMethods.put(model.thisClass().asSymbol(), perClass);
            }
        });
        return restrictedMethods;
    }

    private boolean isRestrictedMethod(Method referent) {
        if (Modifier.isNative(referent.getModifiers())) {
            return true;
        }

        if (referent.getAnnotation(Restricted.class) != null) {
            return true;
        }

        return false;
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

    private static void forEachClassFile(Path jarFile, Consumer<ClassModel> action) {
        try (FileSystem fs = FileSystems.newFileSystem(jarFile)) {
            fs.getRootDirectories().forEach(root -> {
                try (Stream<Path> stream = Files.walk(root)) {
                    stream.filter(p -> p.toString().endsWith(".class"))
                            .forEach(classFile -> {
                                try {
                                    action.accept(ClassFile.of().parse(classFile));
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            });
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void run(Log log, String[] args) throws MalformedURLException {
        OptionParser parser = new OptionParser(false);
        parser.acceptsAll(List.of("?", "h", "help"), "help").forHelp();
        parser.accepts("class-path").withRequiredArg();
        parser.nonOptions("jar files");

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

        List<URL> classPathURLs = new ArrayList<>();
        if (optionSet.has("class-path")) {
            String[] parts = optionSet.valueOf("class-path").toString().split(File.pathSeparator);
            for (String part : parts) {
                classPathURLs.add(URI.create(part).toURL());
            }
        }

        List<Path> jarFiles = new ArrayList<>();
        for (Object o : optionSet.nonOptionArguments()) {
            Path jarPath = Path.of(o.toString());
            jarFiles.add(jarPath);
            classPathURLs.add(jarPath.toUri().toURL());
        }

        if (jarFiles.isEmpty()) {
            log.error("Need at least one jar file to scan");
        }

        new JScanRestricted(log, jarFiles, classPathURLs).run();
    }
}

/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.foreign.abi;

import jdk.internal.foreign.SystemLookup;
import jdk.internal.foreign.abi.aarch64.linux.LinuxAArch64Linker;
import jdk.internal.foreign.abi.aarch64.macos.MacOsAArch64Linker;
import jdk.internal.foreign.abi.aarch64.windows.WindowsAArch64Linker;
import jdk.internal.foreign.abi.fallback.FallbackLinker;
import jdk.internal.foreign.abi.riscv64.linux.LinuxRISCV64Linker;
import jdk.internal.foreign.abi.x64.sysv.SysVx64Linker;
import jdk.internal.foreign.abi.x64.windows.Windowsx64Linker;
import jdk.internal.foreign.layout.AbstractLayout;
import jdk.internal.foreign.layout.ValueLayouts;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.PaddingLayout;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.Objects;

public abstract sealed class AbstractLinker implements Linker permits LinuxAArch64Linker, MacOsAArch64Linker,
                                                                      SysVx64Linker, WindowsAArch64Linker,
                                                                      Windowsx64Linker, LinuxRISCV64Linker,
                                                                      FallbackLinker {

    public interface UpcallStubFactory {
        MemorySegment makeStub(MethodHandle target, Arena arena);
    }

    private final SoftReferenceCache<LinkRequest, MethodHandle> DOWNCALL_CACHE = new SoftReferenceCache<>();
    private final SoftReferenceCache<LinkRequest, UpcallStubFactory> UPCALL_CACHE = new SoftReferenceCache<>();

    @Override
    public MethodHandle downcallHandle(FunctionDescriptor function, Option... options) {
        Objects.requireNonNull(function);
        Objects.requireNonNull(options);
        checkHasNaturalAlignment(function);
        LinkerOptions optionSet = LinkerOptions.forDowncall(function, options);

        return DOWNCALL_CACHE.get(LinkRequest.of(function, optionSet), linkRequest ->  {
            FunctionDescriptor fd = linkRequest.descriptor();
            MethodType type = fd.toMethodType();
            MethodHandle handle = arrangeDowncall(type, fd, linkRequest.options());
            handle = SharedUtils.maybeInsertAllocator(fd, handle);
            return handle;
        });
    }
    protected abstract MethodHandle arrangeDowncall(MethodType inferredMethodType, FunctionDescriptor function, LinkerOptions options);

    @Override
    public MemorySegment upcallStub(MethodHandle target, FunctionDescriptor function, Arena arena, Linker.Option... options) {
        Objects.requireNonNull(arena);
        Objects.requireNonNull(target);
        Objects.requireNonNull(function);
        checkHasNaturalAlignment(function);
        SharedUtils.checkExceptions(target);
        LinkerOptions optionSet = LinkerOptions.forUpcall(function, options);

        MethodType type = function.toMethodType();
        if (!type.equals(target.type())) {
            throw new IllegalArgumentException("Wrong method handle type: " + target.type());
        }

        UpcallStubFactory factory = UPCALL_CACHE.get(LinkRequest.of(function, optionSet), linkRequest ->
            arrangeUpcall(type, linkRequest.descriptor(), linkRequest.options()));
        return factory.makeStub(target, arena);
    }

    protected abstract UpcallStubFactory arrangeUpcall(MethodType targetType, FunctionDescriptor function, LinkerOptions options);

    @Override
    public SystemLookup defaultLookup() {
        return SystemLookup.getInstance();
    }

    // Current limitation of the implementation:
    // We don't support packed structs on some platforms,
    // so reject them here explicitly
    private static void checkHasNaturalAlignment(FunctionDescriptor descriptor) {
        descriptor.returnLayout().ifPresent(AbstractLinker::checkHasNaturalAlignmentRecursive);
        descriptor.argumentLayouts().forEach(AbstractLinker::checkHasNaturalAlignmentRecursive);
    }

    private static void checkHasNaturalAlignmentRecursive(MemoryLayout layout) {
        checkHasNaturalAlignment(layout);
        if (layout instanceof GroupLayout gl) {
            for (MemoryLayout member : gl.memberLayouts()) {
                checkHasNaturalAlignmentRecursive(member);
            }
        } else if (layout instanceof SequenceLayout sl) {
            checkHasNaturalAlignmentRecursive(sl.elementLayout());
        }
    }

    private static void checkHasNaturalAlignment(MemoryLayout layout) {
        if (!((AbstractLayout<?>) layout).hasNaturalAlignment()) {
            throw new IllegalArgumentException("Layout bit alignment must be natural alignment: " + layout);
        }
    }

    // class with custom equals/hash code that ignores layout names
    private static class LinkRequest {
        private final int hash;
        private final FunctionDescriptor descriptor;
        private final LinkerOptions options;

        private LinkRequest(FunctionDescriptor descriptor, LinkerOptions options, int hash) {
            this.descriptor = descriptor;
            this.options = options;
            this.hash = hash;
        }

        public static LinkRequest of(FunctionDescriptor descriptor, LinkerOptions options) {
            int hash = mixHash(hashCodeForLinker(descriptor), options.hashCode());
            return new LinkRequest(descriptor, options, hash);
        }

        public FunctionDescriptor descriptor() {
            return descriptor;
        }

        public LinkerOptions options() {
            return options;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LinkRequest that = (LinkRequest) o;
            return equalForLinker(descriptor, that.descriptor) && Objects.equals(options, that.options);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        private static boolean equalForLinker(FunctionDescriptor lhs, FunctionDescriptor rhs) {
            if (lhs.returnLayout().isPresent() != rhs.returnLayout().isPresent()) {
                return false;
            }

            if (lhs.argumentLayouts().size() != rhs.argumentLayouts().size()) {
                return false;
            }

            if (lhs.returnLayout().isPresent()
                    && !equalForLinker(lhs.returnLayout().get(), rhs.returnLayout().get())) {
                return false;
            }

            for (int i = 0; i < lhs.argumentLayouts().size(); i++) {
                if (!equalForLinker(lhs.argumentLayouts().get(i), rhs.argumentLayouts().get(i))) {
                    return false;
                }
            }

            return true;
        }

        private static boolean equalForLinker(MemoryLayout lhs, MemoryLayout rhs) {
            if (lhs.getClass() != rhs.getClass()) {
                return false;
            }

            if (lhs.byteSize() != rhs.byteSize() || lhs.byteAlignment() != rhs.byteAlignment()) {
                return false;
            }

            return switch (lhs) {
                case AddressLayout lhsAl -> addressLayoutEqualForLinker(lhsAl, (AddressLayout) rhs);
                case GroupLayout lhsGl -> groupLayoutsEqualForLinker(lhsGl, (GroupLayout) rhs);
                case SequenceLayout lhsSl -> sequenceLayoutEqualForLinker(lhsSl, (SequenceLayout) rhs);
                case PaddingLayout pl -> true;
                case ValueLayout vl -> true; // carrier and order don't matter
            };
        }

        private static boolean addressLayoutEqualForLinker(AddressLayout lhs, AddressLayout rhs) {
            if (lhs.targetLayout().isPresent() != rhs.targetLayout().isPresent()) {
                return false;
            }

            return lhs.targetLayout().isEmpty()
                    || equalForLinker(lhs.targetLayout().get(), rhs.targetLayout().get());
        }

        private static boolean groupLayoutsEqualForLinker(GroupLayout lhs, GroupLayout rhs) {
            if (lhs.memberLayouts().size() != rhs.memberLayouts().size()) {
                return false;
            }

            for (int i = 0; i < lhs.memberLayouts().size(); i++) {
                if (!equalForLinker(lhs.memberLayouts().get(i), rhs.memberLayouts().get(i))) {
                    return false;
                }
            }

            return true;
        }

        private static boolean sequenceLayoutEqualForLinker(SequenceLayout lhs, SequenceLayout rhs) {
            if (lhs.elementCount() != rhs.elementCount()) {
                return false;
            }

            return equalForLinker(lhs.elementLayout(), rhs.elementLayout());
        }

        private static int hashCodeForLinker(FunctionDescriptor descriptor) {
            int hash = descriptor.returnLayout().map(LinkRequest::hashCodeForLinker).orElse(0);

            for (MemoryLayout argLayout : descriptor.argumentLayouts()) {
                hash = mixHash(hash, hashCodeForLinker(argLayout));
            }

            return hash;
        }

        private static int hashCodeForLinker(MemoryLayout layout) {
            int hash = layout.getClass().hashCode();
            hash = mixHash(hash, Long.hashCode(layout.byteSize()));
            hash = mixHash(hash, Long.hashCode(layout.byteAlignment()));

            hash = mixHash(hash, switch (layout) {
                case AddressLayout al -> addressLayoutHashForLinker(al);
                case GroupLayout gl -> groupLayoutHashForLinker(gl);
                case SequenceLayout sl -> sequenceLayoutHashForLinker(sl);
                case PaddingLayout pl -> hash;
                case ValueLayout vl -> hash; // carrier and order don't matter
            });

            return hash;
        }

        private static int addressLayoutHashForLinker(AddressLayout al) {
            return al.targetLayout().map(LinkRequest::hashCodeForLinker).orElse(0);
        }

        private static int groupLayoutHashForLinker(GroupLayout gl) {
            int hash = 0;
            for (MemoryLayout member : gl.memberLayouts()) {
                hash = mixHash(hash, hashCodeForLinker(member));
            }
            return hash;
        }

        private static int sequenceLayoutHashForLinker(SequenceLayout sl) {
            return hashCodeForLinker(sl.elementLayout());
        }

        private static int mixHash(int hash, int addedHash) {
            return 31 * hash + addedHash;
        }
    }
}

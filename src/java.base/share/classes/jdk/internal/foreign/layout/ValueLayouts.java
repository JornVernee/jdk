/*
 *  Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.  Oracle designates this
 *  particular file as subject to the "Classpath" exception as provided
 *  by Oracle in the LICENSE file that accompanied this code.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */
package jdk.internal.foreign.layout;

import jdk.internal.foreign.Utils;
import jdk.internal.foreign.abi.x64.windows.TypeClass;
import jdk.internal.misc.Unsafe;
import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;
import sun.invoke.util.Wrapper;

import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.AddressLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A value layout. A value layout is used to model the memory layout associated with values of basic data types, such as <em>integral</em> types
 * (either signed or unsigned) and <em>floating-point</em> types. Each value layout has a size, an alignment (in bits),
 * a {@linkplain ByteOrder byte order}, and a <em>carrier</em>, that is, the Java type that should be used when
 * {@linkplain MemorySegment#get(ValueLayout.OfInt, long) accessing} a memory region using the value layout.
 * <p>
 * This class defines useful value layout constants for Java primitive types and addresses.
 * The layout constants in this class make implicit alignment and byte-ordering assumption: all layout
 * constants in this class are byte-aligned, and their byte order is set to the {@linkplain ByteOrder#nativeOrder() platform default},
 * thus making it easy to work with other APIs, such as arrays and {@link java.nio.ByteBuffer}.
 *
 * @implSpec This class and its subclasses are immutable, thread-safe and <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
 */
public final class ValueLayouts {

    // Suppresses default constructor, ensuring non-instantiability.
    private ValueLayouts() {}

    static class AddrSizeHolder {
        static final int ADDRESS_SIZE_BITS = Unsafe.ADDRESS_SIZE * 8;
    }

    public abstract sealed static class AbstractValueLayout<V extends AbstractValueLayout<V> & ValueLayout> extends AbstractLayout<V> implements ValueLayout {
        private final Class<?> carrier;
        private final ByteOrder order;
        @Stable
        private VarHandle handle;

        AbstractValueLayout(Class<?> carrier, ByteOrder order, long bitSize, long bitAlignment, Optional<String> name,
                            Optional<Linker.Classifier> classifier) {
            super(bitSize, bitAlignment, name, classifier);
            this.carrier = carrier;
            this.order = order;
            assert isValidCarrier(carrier, bitSize);
        }

        /**
         * {@return the value's byte order}
         */
        public final ByteOrder order() {
            return order;
        }

        /**
         * Returns a value layout with the same carrier, alignment constraints and name as this value layout,
         * but with the specified byte order.
         *
         * @param order the desired byte order.
         * @return a value layout with the given byte order.
         */
        public final V withOrder(ByteOrder order) {
            Objects.requireNonNull(order);
            return dup(order, bitAlignment(), name(), classifier());
        }

        @Override
        public String toString() {
            char descriptor = carrier.descriptorString().charAt(0);
            if (order == ByteOrder.LITTLE_ENDIAN) {
                descriptor = Character.toLowerCase(descriptor);
            }
            return decorateLayoutString(String.format("%s%d", descriptor, bitSize()));
        }

        @Override
        public boolean equals(Object other) {
            return this == other ||
                    other instanceof AbstractValueLayout<?> otherValue &&
                            super.equals(other) &&
                            carrier.equals(otherValue.carrier) &&
                            order.equals(otherValue.order);
        }

        public final VarHandle arrayElementVarHandle(int... shape) {
            Objects.requireNonNull(shape);
            MemoryLayout layout = self();
            List<MemoryLayout.PathElement> path = new ArrayList<>();
            for (int i = shape.length; i > 0; i--) {
                int size = shape[i - 1];
                if (size < 0) throw new IllegalArgumentException("Invalid shape size: " + size);
                layout = MemoryLayout.sequenceLayout(size, layout);
                path.add(MemoryLayout.PathElement.sequenceElement());
            }
            layout = MemoryLayout.sequenceLayout(layout);
            path.add(MemoryLayout.PathElement.sequenceElement());
            return layout.varHandle(path.toArray(new MemoryLayout.PathElement[0]));
        }

        /**
         * {@return the carrier associated with this value layout}
         */
        public final Class<?> carrier() {
            return carrier;
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), order, carrier);
        }

        @Override
        final V dup(long bitAlignment, Optional<String> name, Optional<Linker.Classifier> classifier) {
            return dup(order(), bitAlignment, name, classifier);
        }

        abstract V dup(ByteOrder order, long bitAlignment, Optional<String> name, Optional<Linker.Classifier> classifier);

        @Override
        public ValueLayout withCarrier(Class<?> carrier) {
            Objects.requireNonNull(carrier);
            if (!isValidCarrier(carrier, bitSize())) {
                throw new IllegalArgumentException("Not a valid carrier for this layout: " + carrier + ", " + this);
            }
            return valueLayout(carrier, order()).dup(order(), bitAlignment(), name(), classifier());
        }

        static boolean isValidCarrier(Class<?> carrier, long bitSize) {
            return isValidCarrier(carrier)
                   && (carrier != MemorySegment.class
                    // MemorySegment bitSize must always equal ADDRESS_SIZE_BITS
                    || bitSize == AddrSizeHolder.ADDRESS_SIZE_BITS)
                   && (!carrier.isPrimitive() ||
                    // Primitive class bitSize must always correspond
                    bitSize == (carrier == boolean.class ? 8 : Wrapper.forPrimitiveType(carrier).bitWidth()));
        }

        static boolean isValidCarrier(Class<?> carrier) {
            // void.class is not valid
            return carrier == boolean.class
                    || carrier == byte.class
                    || carrier == short.class
                    || carrier == char.class
                    || carrier == int.class
                    || carrier == long.class
                    || carrier == float.class
                    || carrier == double.class
                    || carrier == MemorySegment.class;
        }

        @ForceInline
        public final VarHandle accessHandle() {
            if (handle == null) {
                // this store to stable field is safe, because return value of 'makeMemoryAccessVarHandle' has stable identity
                handle = Utils.makeSegmentViewVarHandle(self());
            }
            return handle;
        }

        @SuppressWarnings("unchecked")
        final V self() {
            return (V) this;
        }
    }

    public static final class OfBooleanImpl extends AbstractValueLayout<OfBooleanImpl> implements ValueLayout.OfBoolean {

        private OfBooleanImpl(ByteOrder order, long bitAlignment, Optional<String> name, Optional<Linker.Classifier> classifier) {
            super(boolean.class, order, Byte.SIZE, bitAlignment, name, classifier);
        }

        @Override
        OfBooleanImpl dup(ByteOrder order, long bitAlignment, Optional<String> name, Optional<Linker.Classifier> classifier) {
            return new OfBooleanImpl(order, bitAlignment, name, classifier);
        }

        public static OfBooleanImpl of(ByteOrder order) {
            return new OfBooleanImpl(order, Byte.SIZE, Optional.empty(), Optional.empty());
        }
    }

    public static final class OfByteImpl extends AbstractValueLayout<OfByteImpl> implements ValueLayout.OfByte {

        private OfByteImpl(ByteOrder order, long bitAlignment, Optional<String> name, Optional<Linker.Classifier> classifier) {
            super(byte.class, order, Byte.SIZE, bitAlignment, name, classifier);
        }

        @Override
        OfByteImpl dup(ByteOrder order, long bitAlignment, Optional<String> name, Optional<Linker.Classifier> classifier) {
            return new OfByteImpl(order, bitAlignment, name, classifier);
        }

        public static OfByteImpl of(ByteOrder order) {
            return new OfByteImpl(order, Byte.SIZE, Optional.empty(), Optional.empty());
        }
    }

    public static final class OfCharImpl extends AbstractValueLayout<OfCharImpl> implements ValueLayout.OfChar {

        private OfCharImpl(ByteOrder order, long bitAlignment, Optional<String> name, Optional<Linker.Classifier> classifier) {
            super(char.class, order, Character.SIZE, bitAlignment, name, classifier);
        }

        @Override
        OfCharImpl dup(ByteOrder order, long bitAlignment, Optional<String> name, Optional<Linker.Classifier> classifier) {
            return new OfCharImpl(order, bitAlignment, name, classifier);
        }

        public static OfCharImpl of(ByteOrder order) {
            return new OfCharImpl(order, Character.SIZE, Optional.empty(), Optional.empty());
        }
    }

    public static final class OfShortImpl extends AbstractValueLayout<OfShortImpl> implements ValueLayout.OfShort {

        private OfShortImpl(ByteOrder order, long bitAlignment, Optional<String> name, Optional<Linker.Classifier> classifier) {
            super(short.class, order, Short.SIZE, bitAlignment, name, classifier);
        }

        @Override
        OfShortImpl dup(ByteOrder order, long bitAlignment, Optional<String> name, Optional<Linker.Classifier> classifier) {
            return new OfShortImpl(order, bitAlignment, name, classifier);
        }

        public static OfShortImpl of(ByteOrder order) {
            return new OfShortImpl(order, Short.SIZE, Optional.empty(), Optional.empty());
        }
    }

    public static final class OfIntImpl extends AbstractValueLayout<OfIntImpl> implements ValueLayout.OfInt {

        private OfIntImpl(ByteOrder order, long bitAlignment, Optional<String> name, Optional<Linker.Classifier> classifier) {
            super(int.class, order, Integer.SIZE, bitAlignment, name, classifier);
        }

        @Override
        OfIntImpl dup(ByteOrder order, long bitAlignment, Optional<String> name, Optional<Linker.Classifier> classifier) {
            return new OfIntImpl(order, bitAlignment, name, classifier);
        }

        public static OfIntImpl of(ByteOrder order) {
            return new OfIntImpl(order, Integer.SIZE, Optional.empty(), Optional.empty());
        }
    }

    public static final class OfFloatImpl extends AbstractValueLayout<OfFloatImpl> implements ValueLayout.OfFloat {

        private OfFloatImpl(ByteOrder order, long bitAlignment, Optional<String> name, Optional<Linker.Classifier> classifier) {
            super(float.class, order, Float.SIZE, bitAlignment, name, classifier);
        }

        @Override
        OfFloatImpl dup(ByteOrder order, long bitAlignment, Optional<String> name, Optional<Linker.Classifier> classifier) {
            return new OfFloatImpl(order, bitAlignment, name, classifier);
        }

        public static OfFloatImpl of(ByteOrder order) {
            return new OfFloatImpl(order, Float.SIZE, Optional.empty(), Optional.empty());
        }
    }

    public static final class OfLongImpl extends AbstractValueLayout<OfLongImpl> implements ValueLayout.OfLong {

        private OfLongImpl(ByteOrder order, long bitAlignment, Optional<String> name, Optional<Linker.Classifier> classifier) {
            super(long.class, order, Long.SIZE, bitAlignment, name, classifier);
        }

        @Override
        OfLongImpl dup(ByteOrder order, long bitAlignment, Optional<String> name, Optional<Linker.Classifier> classifier) {
            return new OfLongImpl(order, bitAlignment, name, classifier);
        }

        public static OfLongImpl of(ByteOrder order) {
            return new OfLongImpl(order, Long.SIZE, Optional.empty(), Optional.empty());
        }
    }

    public static final class OfDoubleImpl extends AbstractValueLayout<OfDoubleImpl> implements ValueLayout.OfDouble {

        private OfDoubleImpl(ByteOrder order, long bitAlignment, Optional<String> name,
                             Optional<Linker.Classifier> classifier) {
            super(double.class, order, Double.SIZE, bitAlignment, name, classifier);
        }

        @Override
        OfDoubleImpl dup(ByteOrder order, long bitAlignment, Optional<String> name, Optional<Linker.Classifier> classifier) {
            return new OfDoubleImpl(order, bitAlignment, name, classifier);
        }

        public static OfDoubleImpl of(ByteOrder order) {
            return new OfDoubleImpl(order, Double.SIZE, Optional.empty(), Optional.empty());
        }

    }

    public static final class OfAddressImpl extends AbstractValueLayout<OfAddressImpl> implements AddressLayout {

        private final MemoryLayout targetLayout;

        private OfAddressImpl(ByteOrder order, long bitSize, long bitAlignment, MemoryLayout targetLayout,
                              Optional<String> name, Optional<Linker.Classifier> classifier) {
            super(MemorySegment.class, order, bitSize, bitAlignment, name, classifier);
            this.targetLayout = targetLayout;
        }

        @Override
        OfAddressImpl dup(ByteOrder order, long bitAlignment, Optional<String> name,
                          Optional<Linker.Classifier> classifier) {
            return new OfAddressImpl(order, bitSize(), bitAlignment,targetLayout, name, classifier);
        }

        @Override
        public boolean equals(Object other) {
            return super.equals(other) &&
                    Objects.equals(((OfAddressImpl)other).targetLayout, this.targetLayout);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), targetLayout);
        }

        @Override
        @CallerSensitive
        public AddressLayout withTargetLayout(MemoryLayout layout) {
            Reflection.ensureNativeAccess(Reflection.getCallerClass(), AddressLayout.class, "withTargetLayout");
            Objects.requireNonNull(layout);
            return new OfAddressImpl(order(), bitSize(), bitAlignment(), layout, name(), classifier());
        }

        @Override
        public AddressLayout withoutTargetLayout() {
            return new OfAddressImpl(order(), bitSize(), bitAlignment(), null, name(), classifier());
        }

        @Override
        public Optional<MemoryLayout> targetLayout() {
            return Optional.ofNullable(targetLayout);
        }

        public static OfAddressImpl of(ByteOrder order) {
            return new OfAddressImpl(order, AddrSizeHolder.ADDRESS_SIZE_BITS, AddrSizeHolder.ADDRESS_SIZE_BITS, null, Optional.empty(), Optional.empty());
        }

        @Override
        public String toString() {
            char descriptor = 'A';
            if (order() == ByteOrder.LITTLE_ENDIAN) {
                descriptor = Character.toLowerCase(descriptor);
            }
            String str = decorateLayoutString(String.format("%s%d", descriptor, bitSize()));
            if (targetLayout != null) {
                str += ":" + targetLayout;
            }
            return str;
        }
    }

    /**
     * Creates a value layout of given Java carrier and byte order. The type of resulting value layout is determined
     * by the carrier provided:
     * <ul>
     *     <li>{@link ValueLayout.OfBoolean}, for {@code boolean.class}</li>
     *     <li>{@link ValueLayout.OfByte}, for {@code byte.class}</li>
     *     <li>{@link ValueLayout.OfShort}, for {@code short.class}</li>
     *     <li>{@link ValueLayout.OfChar}, for {@code char.class}</li>
     *     <li>{@link ValueLayout.OfInt}, for {@code int.class}</li>
     *     <li>{@link ValueLayout.OfFloat}, for {@code float.class}</li>
     *     <li>{@link ValueLayout.OfLong}, for {@code long.class}</li>
     *     <li>{@link ValueLayout.OfDouble}, for {@code double.class}</li>
     *     <li>{@link AddressLayout}, for {@code MemorySegment.class}</li>
     * </ul>
     * @param carrier the value layout carrier.
     * @param order the value layout's byte order.
     * @return a value layout with the given Java carrier and byte-order.
     * @throws IllegalArgumentException if the carrier type is not supported.
     */
    public static AbstractValueLayout<?> valueLayout(Class<?> carrier, ByteOrder order) {
        Objects.requireNonNull(carrier);
        Objects.requireNonNull(order);
        if (carrier == boolean.class) {
            return ValueLayouts.OfBooleanImpl.of(order);
        } else if (carrier == char.class) {
            return ValueLayouts.OfCharImpl.of(order);
        } else if (carrier == byte.class) {
            return ValueLayouts.OfByteImpl.of(order);
        } else if (carrier == short.class) {
            return ValueLayouts.OfShortImpl.of(order);
        } else if (carrier == int.class) {
            return ValueLayouts.OfIntImpl.of(order);
        } else if (carrier == float.class) {
            return ValueLayouts.OfFloatImpl.of(order);
        } else if (carrier == long.class) {
            return ValueLayouts.OfLongImpl.of(order);
        } else if (carrier == double.class) {
            return ValueLayouts.OfDoubleImpl.of(order);
        } else if (carrier == MemorySegment.class) {
            return ValueLayouts.OfAddressImpl.of(order);
        } else {
            throw new IllegalArgumentException("Unsupported carrier: " + carrier.getName());
        }
    }
}

/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 *
 */

/*
 * @test
 * @enablePreview
 * @library ..
 * @modules java.base/jdk.internal.foreign.layout
 * @run testng TestLayoutEquality
 */

import jdk.internal.foreign.layout.ValueLayouts;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;

import static java.lang.foreign.Linker.C_INT32_T;
import static java.lang.foreign.Linker.C_INT64_T;
import static org.testng.Assert.assertEquals;

public class TestLayoutEquality extends NativeTestHelper {

    @Test(dataProvider = "layoutConstants")
    public void testReconstructedEquality(ValueLayout layout) {
        ValueLayout newLayout = ValueLayouts.valueLayout(layout.carrier(), layout.order());
        newLayout = newLayout.withBitAlignment(layout.bitAlignment());
        if (layout instanceof AddressLayout addressLayout && addressLayout.targetLayout().isPresent()) {
            newLayout = ((AddressLayout) newLayout).withTargetLayout(addressLayout.targetLayout().get());
        }
        if (layout.name().isPresent()) {
            newLayout = newLayout.withName(layout.name().get());
        }
        if (layout.linkerType().isPresent()) {
            newLayout = newLayout.withLinkerType(layout.linkerType().get());
        }

        // properties should be equal
        assertEquals(newLayout.bitSize(), layout.bitSize());
        assertEquals(newLayout.bitAlignment(), layout.bitAlignment());
        assertEquals(newLayout.name(), layout.name());

        // layouts should be equals
        assertEquals(newLayout, layout);
    }

    @DataProvider
    public static Object[][] layoutConstants() throws ReflectiveOperationException {
        List<ValueLayout> testValues = new ArrayList<>();

        testValues.add(ValueLayout.JAVA_BYTE);
        testValues.add(ValueLayout.JAVA_CHAR);
        testValues.add(ValueLayout.JAVA_BOOLEAN);
        testValues.add(ValueLayout.JAVA_SHORT);
        testValues.add(ValueLayout.JAVA_INT);
        testValues.add(ValueLayout.JAVA_FLOAT);
        testValues.add(ValueLayout.JAVA_LONG);
        testValues.add(ValueLayout.JAVA_DOUBLE);
        testValues.add(C_BOOL);
        testValues.add(C_CHAR);
        testValues.add(C_SHORT);
        testValues.add(C_INT32_T);
        testValues.add(C_FLOAT);
        testValues.add(C_INT64_T);
        testValues.add(C_DOUBLE);
        testValues.add(C_POINTER);

        testValues.add(ValueLayout.JAVA_INT);
        testValues.add(ValueLayout.JAVA_FLOAT);
        testValues.add(ValueLayout.JAVA_LONG);
        testValues.add(ValueLayout.JAVA_DOUBLE);

        return testValues.stream().map(e -> new Object[]{e}).toArray(Object[][]::new);
    }
}

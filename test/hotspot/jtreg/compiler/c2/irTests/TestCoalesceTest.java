/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 */
package compiler.c2.irTests;

import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8311969
 * @summary Test that mach analysis coalesces redundant test instructions
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestCoalesceTest
 */
public class TestCoalesceTest {

    int iFld1 = 0;
    int iFld2 = 0;
    long lFld1 = 0;
    long lFld2 = 0;

    public static void main(String[] args) {
        TestFramework.run();
    }

    // OR
    @Test
    @IR(failOn = IRNode.X86_TESTI_REG)
    public boolean testICoalesceOrRegEqual() {
        return (iFld1 | iFld2) == 0;
    }

    @Test
    @IR(failOn = IRNode.X86_TESTI_REG)
    public boolean testICoalesceOrRegGreater() {
        return (iFld1 | iFld2) > 0;
    }

    @Test
    @IR(failOn = IRNode.X86_TESTI_REG)
    public boolean testICoalesceOrRegGreaterEqual() {
        return (iFld1 | iFld2) >= 0;
    }

    @Test
    @IR(failOn = IRNode.X86_TESTI_REG)
    public boolean testICoalesceOrRegLess() {
        return (iFld1 | iFld2) < 0;
    }

    @Test
    @IR(failOn = IRNode.X86_TESTI_REG)
    public boolean testICoalesceOrRegLessEqual() {
        return (iFld1 | iFld2) <= 0;
    }

    // AND
    @Test
    @IR(failOn = IRNode.X86_TESTI_REG)
    public boolean testICoalesceAndRegEqual() {
        return (iFld1 & iFld2) == 0;
    }

    @Test
    @IR(failOn = IRNode.X86_TESTI_REG)
    public boolean testICoalesceAndRegGreater() {
        return (iFld1 & iFld2) > 0;
    }

    @Test
    @IR(failOn = IRNode.X86_TESTI_REG)
    public boolean testICoalesceAndRegGreaterEqual() {
        return (iFld1 & iFld2) >= 0;
    }

    @Test
    @IR(failOn = IRNode.X86_TESTI_REG)
    public boolean testICoalesceAndRegLess() {
        return (iFld1 & iFld2) < 0;
    }

    @Test
    @IR(failOn = IRNode.X86_TESTI_REG)
    public boolean testICoalesceAndRegLessEqual() {
        return (iFld1 & iFld2) <= 0;
    }

    // XOR
    @Test
    @IR(failOn = IRNode.X86_TESTI_REG)
    public boolean testICoalesceXorRegEqual() {
        return (iFld1 ^ iFld2) == 0;
    }

    @Test
    @IR(failOn = IRNode.X86_TESTI_REG)
    public boolean testICoalesceXorRegGreater() {
        return (iFld1 ^ iFld2) > 0;
    }

    @Test
    @IR(failOn = IRNode.X86_TESTI_REG)
    public boolean testICoalesceXorRegGreaterEqual() {
        return (iFld1 ^ iFld2) >= 0;
    }

    @Test
    @IR(failOn = IRNode.X86_TESTI_REG)
    public boolean testICoalesceXorRegLess() {
        return (iFld1 ^ iFld2) < 0;
    }

    @Test
    @IR(failOn = IRNode.X86_TESTI_REG)
    public boolean testICoalesceXorRegLessEqual() {
        return (iFld1 ^ iFld2) <= 0;
    }

    // Same for long
    // OR
    @Test
    @IR(failOn = IRNode.X86_TESTL_REG)
    public boolean testLCoalesceOrRegEqual() {
        return (lFld1 | lFld2) == 0;
    }

    @Test
    @IR(failOn = IRNode.X86_TESTL_REG)
    public boolean testLCoalesceOrRegGreater() {
        return (lFld1 | lFld2) > 0;
    }

    @Test
    @IR(failOn = IRNode.X86_TESTL_REG)
    public boolean testLCoalesceOrRegGreaterEqual() {
        return (lFld1 | lFld2) >= 0;
    }

    @Test
    @IR(failOn = IRNode.X86_TESTL_REG)
    public boolean testLCoalesceOrRegLess() {
        return (lFld1 | lFld2) < 0;
    }

    @Test
    @IR(failOn = IRNode.X86_TESTL_REG)
    public boolean testLCoalesceOrRegLessEqual() {
        return (lFld1 | lFld2) <= 0;
    }

    // AND
    @Test
    @IR(failOn = IRNode.X86_TESTL_REG)
    public boolean testLCoalesceAndRegEqual() {
        return (lFld1 & lFld2) == 0;
    }

    @Test
    @IR(failOn = IRNode.X86_TESTL_REG)
    public boolean testLCoalesceAndRegGreater() {
        return (lFld1 & lFld2) > 0;
    }

    @Test
    @IR(failOn = IRNode.X86_TESTL_REG)
    public boolean testLCoalesceAndRegGreaterEqual() {
        return (lFld1 & lFld2) >= 0;
    }

    @Test
    @IR(failOn = IRNode.X86_TESTL_REG)
    public boolean testLCoalesceAndRegLess() {
        return (lFld1 & lFld2) < 0;
    }

    @Test
    @IR(failOn = IRNode.X86_TESTL_REG)
    public boolean testLCoalesceAndRegLessEqual() {
        return (lFld1 & lFld2) <= 0;
    }

    // XOR
    @Test
    @IR(failOn = IRNode.X86_TESTL_REG)
    public boolean testLCoalesceXorRegEqual() {
        return (lFld1 ^ lFld2) == 0;
    }

    @Test
    @IR(failOn = IRNode.X86_TESTL_REG)
    public boolean testLCoalesceXorRegGreater() {
        return (lFld1 ^ lFld2) > 0;
    }

    @Test
    @IR(failOn = IRNode.X86_TESTL_REG)
    public boolean testLCoalesceXorRegGreaterEqual() {
        return (lFld1 ^ lFld2) >= 0;
    }

    @Test
    @IR(failOn = IRNode.X86_TESTL_REG)
    public boolean testLCoalesceXorRegLess() {
        return (lFld1 ^ lFld2) < 0;
    }

    @Test
    @IR(failOn = IRNode.X86_TESTL_REG)
    public boolean testLCoalesceXorRegLessEqual() {
        return (lFld1 ^ lFld2) <= 0;
    }
}

/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package sun.font;

import java.awt.geom.Point2D;
import sun.font.GlyphLayout.GVData;
import sun.java2d.Disposer;
import sun.java2d.DisposerRecord;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import static java.lang.foreign.MemorySegment.NULL;
import java.lang.foreign.SymbolLookup;
import static java.lang.foreign.ValueLayout.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import sun.font.fontmanager.*;
import static sun.font.fontmanager.hb_jdk_p_h.*;

import java.util.Optional;
import java.util.WeakHashMap;

public class HBShaper {

    private static final Linker LINKER;
    private static final SymbolLookup SYM_LOOKUP;
    private static final MethodHandle malloc_handle;

    /* hb_jdk_font_funcs_struct is a pointer to a harfbuzz font_funcs
     * object which references the 5 following upcall stubs.
     * The singleton shared font_funcs ptr is passed down in each
     * call to shape() and installed on the hb_font.
     */
    private static final MemorySegment hb_jdk_font_funcs_struct;
    private static final MemorySegment store_layout_results_stub;

   static {
        LINKER = Linker.nativeLinker();
        SYM_LOOKUP = SymbolLookup.loaderLookup().or(LINKER.defaultLookup());
        FunctionDescriptor mallocDescriptor =
            FunctionDescriptor.of(ADDRESS, JAVA_LONG);
        Optional<MemorySegment> malloc_symbol = SYM_LOOKUP.find("malloc");
        @SuppressWarnings("restricted")
        MethodHandle tmp1 = LINKER.downcallHandle(malloc_symbol.get(), mallocDescriptor);
        malloc_handle = tmp1;

        Arena garena = Arena.global(); // creating stubs that exist until VM exit.
        MemorySegment get_var_glyph_stub = hb_font_get_variation_glyph_func_t.allocate(HBShaper::get_variation_glyph, garena);
        MemorySegment get_nominal_glyph_stub = hb_font_get_nominal_glyph_func_t.allocate(HBShaper::get_nominal_glyph, garena);
        MemorySegment get_h_advance_stub = hb_font_get_glyph_h_advance_func_t.allocate(HBShaper::get_glyph_h_advance, garena);
        MemorySegment get_v_advance_stub = hb_font_get_glyph_v_advance_func_t.allocate(HBShaper::get_glyph_v_advance, garena);
        MemorySegment get_contour_pt_stub = hb_font_get_glyph_contour_point_func_t.allocate(HBShaper::get_glyph_contour_point, garena);

        hb_jdk_font_funcs_struct = HBCreateFontFuncs(
                get_nominal_glyph_stub,
                get_var_glyph_stub,
                get_h_advance_stub,
                get_v_advance_stub,
                get_contour_pt_stub);

       store_layout_results_stub = store_layoutdata_func_t.allocate(HBShaper::store_layout_results, garena);
    }

    private static int get_nominal_glyph(
        MemorySegment font_ptr,   /* Not used */
        MemorySegment font_data,  /* Not used */
        int unicode,
        MemorySegment glyph,      /* pointer to location to store glyphID */
        MemorySegment user_data   /* Not used */
    ) {

        Font2D font2D = scopedVars.get().font();
        int glyphID = font2D.charToGlyph(unicode);
        @SuppressWarnings("restricted")
        MemorySegment glyphIDPtr = glyph.reinterpret(4);
        glyphIDPtr.setAtIndex(JAVA_INT, 0, glyphID);
        return (glyphID != 0) ? 1 : 0;
    }

    private static int get_variation_glyph(
        MemorySegment font_ptr,   /* Not used */
        MemorySegment font_data,  /* Not used */
        int unicode,
        int variation_selector,
        MemorySegment glyph,      /* pointer to location to store glyphID */
        MemorySegment user_data   /* Not used */
    ) {
        Font2D font2D = scopedVars.get().font();
        int glyphID = font2D.charToVariationGlyph(unicode, variation_selector);
        @SuppressWarnings("restricted")
        MemorySegment glyphIDPtr = glyph.reinterpret(4);
        glyphIDPtr.setAtIndex(JAVA_INT, 0, glyphID);
        return (glyphID != 0) ? 1 : 0;
    }

    private static final float HBFloatToFixedScale = ((float)(1 << 16));
    private static final int HBFloatToFixed(float f) {
        return ((int)((f) * HBFloatToFixedScale));
    }

    private static int get_glyph_h_advance(
        MemorySegment font_ptr,   /* Not used */
        MemorySegment font_data,  /* Not used */
        int glyph,
        MemorySegment user_data  /* Not used */
    ) {
        FontStrike strike = scopedVars.get().fontStrike();
        Point2D.Float pt = strike.getGlyphMetrics(glyph);
        return (pt != null) ? HBFloatToFixed(pt.x) : 0;
    }

    private static int get_glyph_v_advance(
        MemorySegment font_ptr,   /* Not used */
        MemorySegment font_data,  /* Not used */
        int glyph,
        MemorySegment user_data  /* Not used */
    ) {

        FontStrike strike = scopedVars.get().fontStrike();
        Point2D.Float pt = strike.getGlyphMetrics(glyph);
        return (pt != null) ? HBFloatToFixed(pt.y) : 0;
    }

    /*
     * This class exists to make the code that uses it less verbose
     */
    private static class IntPtr {
        MemorySegment seg;
        IntPtr(MemorySegment seg) {
        }

        void set(int i) {
            seg.setAtIndex(JAVA_INT, 0, i);
        }
    }

    private static int get_glyph_contour_point(
        MemorySegment font_ptr,   /* Not used */
        MemorySegment font_data,  /* Not used */
        int glyph,
        int point_index,
        MemorySegment x_ptr,     /* ptr to return x */
        MemorySegment y_ptr,     /* ptr to return y */
        MemorySegment user_data  /* Not used */
    ) {
        IntPtr x = new IntPtr(x_ptr);
        IntPtr y = new IntPtr(y_ptr);

        if ((glyph & 0xfffe) == 0xfffe) {
            x.set(0);
            y.set(0);
            return 1;
        }

        FontStrike strike = scopedVars.get().fontStrike();
        Point2D.Float pt = ((PhysicalStrike)strike).getGlyphPoint(glyph, point_index);
        x.set(HBFloatToFixed(pt.x));
        y.set(HBFloatToFixed(pt.y));

       return 1;
    }

    record ScopedVars (
        Font2D font,
        FontStrike fontStrike,
        GVData gvData,
        Point2D.Float point) {}

    static final ScopedValue<ScopedVars> scopedVars = ScopedValue.newInstance();

    static void shape(
        Font2D font2D,
        FontStrike fontStrike,
        float ptSize,
        float[] mat,
        MemorySegment hbface,
        char[] text,
        GVData gvData,
        int script,
        int offset,
        int limit,
        int baseIndex,
        Point2D.Float startPt,
        int flags,
        int slot) {

        /*
         * ScopedValue is needed so that call backs into Java during
         * shaping can locate the correct instances of these to query or update.
         * The alternative of creating bound method handles is far too slow.
         */
        ScopedVars vars = new ScopedVars(font2D, fontStrike, gvData, startPt);
        ScopedValue.where(scopedVars, vars)
                   .run(() -> {

            try (Arena arena = Arena.ofConfined()) {

                float startX = (float)startPt.getX();
                float startY = (float)startPt.getY();

                MemorySegment matrix = arena.allocateFrom(JAVA_FLOAT, mat);
                MemorySegment chars = arena.allocateFrom(JAVA_CHAR, text);

                jdk_hb_shape(
                     ptSize, matrix, hbface, chars, text.length,
                     script, offset, limit,
                     baseIndex, startX, startY, flags, slot,
                     hb_jdk_font_funcs_struct,
                     store_layout_results_stub);
            } catch (Throwable t) {
            }
        });
    }

    private static int getFontTableData(Font2D font2D,
                                int tag,
                                MemorySegment data_ptr_out) {

        /*
         * On return, the data_out_ptr will point to memory allocated by native malloc,
         * so it will be freed by the caller using native free - when it is
         * done with it.
         */
        @SuppressWarnings("restricted")
        MemorySegment data_ptr = data_ptr_out.reinterpret(ADDRESS.byteSize());
        if (tag == 0) {
            data_ptr.setAtIndex(ADDRESS, 0, NULL);
            return 0;
        }
        byte[] data = font2D.getTableBytes(tag);
        if (data == null) {
            data_ptr.setAtIndex(ADDRESS, 0, NULL);
            return 0;
        }
        int len = data.length;
        MemorySegment zero_len = NULL;
        try {
            zero_len = (MemorySegment)malloc_handle.invokeExact((long)len);
        } catch (Throwable t) {
        }
        if (zero_len.equals(NULL)) {
            data_ptr.setAtIndex(ADDRESS, 0, NULL);
            return 0;
        }
        @SuppressWarnings("restricted")
        MemorySegment mem = zero_len.reinterpret(len);
        MemorySegment.copy(data, 0, mem, JAVA_BYTE, 0, len);
        data_ptr.setAtIndex(ADDRESS, 0, mem);
        return len;
    }

    /* WeakHashMap is used so that we do not retain temporary fonts
     *
     * The value is a class that implements the 2D Disposer, so
     * that the native resources for temp. fonts can be freed.
     *
     * Installed fonts should never be cleared from the map as
     * they are permanently referenced.
     */
    private static final WeakHashMap<Font2D, FaceRef>
       faceMap = new WeakHashMap<>();

    static MemorySegment getFace(Font2D font2D) {
        FaceRef ref;
        synchronized (faceMap) {
            ref = faceMap.computeIfAbsent(font2D, FaceRef::new);
        }
        return ref.getFace();
    }

    private static class FaceRef implements DisposerRecord {
        private Font2D font2D;
        private MemorySegment face;
        // get_table_data_fn uses an Arena managed by GC,
        // so we need to keep a reference to it here until
        // this FaceRef is collected.
        private MemorySegment get_table_data_fn;

        private FaceRef(Font2D font) {
            this.font2D = font;
        }

        private synchronized MemorySegment getFace() {
            if (face == null) {
                final Font2D capturedFont2D = font2D; // create copy, since we're about to set font2D field to null
                get_table_data_fn = GetTableDataFn.allocate(
                    (int tag, MemorySegment data_ptr_out) -> getFontTableData(capturedFont2D, tag, data_ptr_out), Arena.ofAuto());
                face = HBCreateFace(get_table_data_fn);
                Disposer.addObjectRecord(font2D, this);
                font2D = null;
            }
            return face;
        }

        @Override
        public void dispose() {
            HBDisposeFace(face);
        }
    }


    /* Upcall to receive results of layout */
    private static int store_layout_results(
        int slot,
        int baseIndex,
        int offset,
        float startX,
        float startY,
        float devScale,
        int charCount,
        int glyphCount,
        MemorySegment /* hb_glyph_info_t* */ glyphInfo,
        MemorySegment /* hb_glyph_position_t* */ glyphPos
        ) {

        GVData gvdata = scopedVars.get().gvData();
        Point2D.Float startPt = scopedVars.get().point();
        float x=0, y=0;
        float advX, advY;
        float scale = 1.0f / HBFloatToFixedScale / devScale;

        int initialCount = gvdata._count;

        int maxGlyphs = (charCount > glyphCount) ? charCount : glyphCount;
        int maxStore = maxGlyphs + initialCount;
        boolean needToGrow = (maxStore > gvdata._glyphs.length) ||
                             ((maxStore * 2 + 2) > gvdata._positions.length);
        if (needToGrow) {
            gvdata.grow(maxStore-initialCount);
        }

        int glyphPosLen = glyphCount * 2 + 2;
        long posSize = glyphPosLen * hb_glyph_position_t.sizeof();
        @SuppressWarnings("restricted")
        MemorySegment glyphPosArr = glyphPos.reinterpret(posSize);

        long glyphInfoSize = glyphCount * hb_glyph_info_t.sizeof();
        @SuppressWarnings("restricted")
        MemorySegment glyphInfoArr = glyphInfo.reinterpret(glyphInfoSize);

         for (int i = 0; i < glyphCount; i++) {
             MemorySegment glyphInfoSeg = hb_glyph_info_t.asSlice(glyphInfoArr, i);
             MemorySegment glyphPosSeg = hb_glyph_position_t.asSlice(glyphPosArr, i);
             int storei = i + initialCount;
             int cluster = hb_glyph_info_t.cluster(glyphInfoSeg) - offset;
             gvdata._indices[storei] = baseIndex + cluster;
             int codePoint = hb_glyph_info_t.codepoint(glyphInfoSeg);
             gvdata._glyphs[storei] = (slot | codePoint);
             int x_offset = hb_glyph_position_t.x_offset(glyphPosSeg);
             int y_offset = hb_glyph_position_t.y_offset(glyphPosSeg);
             gvdata._positions[(storei*2)]   = startX + x + (x_offset * scale);
             gvdata._positions[(storei*2)+1] = startY + y + (y_offset * scale);
             int x_advance = hb_glyph_position_t.x_advance(glyphPosSeg);
             int y_advance = hb_glyph_position_t.y_advance(glyphPosSeg);
             x += x_advance * scale;
             y += y_advance * scale;
        }
        int storeadv = initialCount + glyphCount;
        gvdata._count = storeadv;
        // The final slot in the positions array is important
        // because when the GlyphVector is created from this
        // data it determines the overall advance of the glyphvector
        // and this is used in positioning the next glyphvector
        // during rendering where text is broken into runs.
        // We also need to report it back into "pt", so layout can
        // pass it back down for any next run.
        advX = startX + x;
        advY = startY + y;
        gvdata._positions[(storeadv*2)] = advX;
        gvdata._positions[(storeadv*2)+1] = advY;
        startPt.x = advX;
        startPt.y = advY;
        startPt.x = advX;

        return 0;
  }
}

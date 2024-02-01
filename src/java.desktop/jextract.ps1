jextract `
  --output .\share\classes `
  -t sun.font.fontmanager `
  -I .\share\native\libharfbuzz\ `
  --include-struct hb_glyph_position_t `
  --include-struct hb_glyph_info_t `
  --include-union _hb_var_int_t `
  --include-typedef store_layoutdata_func_t `
  --include-function jdk_hb_shape `
  --include-function HBCreateFontFuncs `
  --include-function HBCreateFace `
  --include-function HBDisposeFace `
  --include-function malloc `
  --include-typedef hb_font_get_nominal_glyph_func_t `
  --include-typedef hb_font_get_variation_glyph_func_t `
  --include-typedef hb_font_get_glyph_h_advance_func_t `
  --include-typedef hb_font_get_glyph_v_advance_func_t `
  --include-typedef hb_font_get_glyph_contour_point_func_t `
  --include-typedef GetTableDataFn `
  .\share\native\libfontmanager\hb-jdk-p.h

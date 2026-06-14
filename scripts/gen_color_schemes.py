#!/usr/bin/env python3
"""Generate Compose Material3 ColorScheme palettes from Doki's colors_themed.xml (light + night).
One-off codegen for settings Phase 1A (color schemes). Output: commonMain ColorSchemes.kt."""
import re, sys, pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
LIGHT = ROOT / "app/src/main/res/values/colors_themed.xml"
DARK = ROOT / "app/src/main/res/values-night/colors_themed.xml"
OUT = ROOT / "composeApp/src/commonMain/kotlin/org/nekosukuriputo/nekuva/core/ui/theme/ColorSchemes.kt"

# enum name -> Doki color prefix (static themes only)
THEMES = [
    ("DEFAULT", "totoro"), ("MIKU", "miku"), ("RENA", "asuka"), ("FROG", "mion"),
    ("BLUEBERRY", "rikka"), ("SAKURA", "sakura"), ("MAMIMI", "mamimi"),
    ("KANADE", "kanade"), ("ITSUKA", "itsuka"),
]
# Compose lightColorScheme/darkColorScheme params present in Doki (surfaceTint omitted -> defaults to primary)
ROLES = [
    "primary","onPrimary","primaryContainer","onPrimaryContainer",
    "secondary","onSecondary","secondaryContainer","onSecondaryContainer",
    "tertiary","onTertiary","tertiaryContainer","onTertiaryContainer",
    "error","onError","errorContainer","onErrorContainer",
    "background","onBackground","surface","onSurface",
    "surfaceVariant","onSurfaceVariant","outline","outlineVariant","scrim",
    "inverseSurface","inverseOnSurface","inversePrimary",
    "surfaceDim","surfaceBright","surfaceContainerLowest","surfaceContainerLow",
    "surfaceContainer","surfaceContainerHigh","surfaceContainerHighest",
]

def parse(path):
    text = path.read_text(encoding="utf-8")
    out = {}
    for m in re.finditer(r'<color name="([a-z]+)_([a-zA-Z]+)">#([0-9A-Fa-f]+)</color>', text):
        prefix, role, hexv = m.group(1), m.group(2), m.group(3)
        out[(prefix, role)] = hexv.upper()
    return out

def to_color(hexv):
    if len(hexv) == 6:
        return f"Color(0xFF{hexv})"
    if len(hexv) == 8:
        return f"Color(0x{hexv})"
    raise ValueError(hexv)

light = parse(LIGHT)
dark = parse(DARK)

def scheme_block(prefix, table, fn):
    lines = [f"    {role} = {to_color(table[(prefix, role)])}," for role in ROLES if (prefix, role) in table]
    return f"{fn}(\n" + "\n".join(lines) + "\n)"

buf = []
buf.append("package org.nekosukuriputo.nekuva.core.ui.theme")
buf.append("")
buf.append("// AUTO-GENERATED from Doki colors_themed.xml (scripts/gen_color_schemes.py). Do not edit by hand.")
buf.append("// Static Material3 palettes for the named color themes (settings Phase 1A).")
buf.append("import androidx.compose.material3.ColorScheme")
buf.append("import androidx.compose.material3.darkColorScheme")
buf.append("import androidx.compose.material3.lightColorScheme")
buf.append("import androidx.compose.ui.graphics.Color")
buf.append("")
for name, prefix in THEMES:
    buf.append(f"private val {prefix.capitalize()}Light = " + scheme_block(prefix, light, "lightColorScheme"))
    buf.append("")
    buf.append(f"private val {prefix.capitalize()}Dark = " + scheme_block(prefix, dark, "darkColorScheme"))
    buf.append("")

# selector
buf.append("/** Resolve a Material3 [ColorScheme] for a [org.nekosukuriputo.nekuva.core.prefs.ColorScheme] enum name. */")
buf.append("fun appColorScheme(schemeName: String, dark: Boolean): ColorScheme = when (schemeName) {")
for name, prefix in THEMES:
    if name == "DEFAULT":
        continue
    buf.append(f'    "{name}" -> if (dark) {prefix.capitalize()}Dark else {prefix.capitalize()}Light')
buf.append(f"    else -> if (dark) TotoroDark else TotoroLight // DEFAULT/MONET/EXPRESSIVE fallback")
buf.append("}")
buf.append("")
# amoled override
buf.append("/** Pure-black AMOLED variant of a dark [ColorScheme] (keeps the theme accent). */")
buf.append("fun ColorScheme.toAmoled(): ColorScheme = copy(")
buf.append("    background = Color.Black,")
buf.append("    surface = Color.Black,")
buf.append("    surfaceDim = Color.Black,")
buf.append("    surfaceContainerLowest = Color.Black,")
buf.append("    surfaceContainerLow = Color(0xFF0A0A0A),")
buf.append("    surfaceContainer = Color(0xFF101010),")
buf.append("    surfaceContainerHigh = Color(0xFF161616),")
buf.append("    surfaceContainerHighest = Color(0xFF1C1C1C),")
buf.append(")")
buf.append("")

OUT.write_text("\n".join(buf), encoding="utf-8")
print(f"Wrote {OUT} ({len(THEMES)} themes)")

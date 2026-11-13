"""Draws the R3AKT UAS logo from the cockpit HUD's palette and font.

Run from the repository root: python3 docs/brand/make_logo.py
Writes docs/brand/logo-mark.svg, docs/brand/logo.svg and the launcher icon's
foreground (apps/android/app/src/main/res/drawable/ic_launcher_foreground.xml).
Text is converted to paths (Rajdhani Bold, the HUD label font), so the SVGs
need no installed font.
"""
import math
from pathlib import Path

from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont

OUT = Path(__file__).parent
RES = Path("apps/android/app/src/main/res")
FONT = RES / "font/rajdhani_bold.ttf"

# Hud.* in HudButton.kt
INK, GREEN, WHITE, RED, BLUE = "#0A0F0B", "#3DFF7A", "#FFFFFF", "#FF4A4A", "#4AA8FF"


def circle(cx: float, cy: float, r: float) -> str:
    return f"M{cx - r:.2f},{cy:.2f} a{r:.2f},{r:.2f} 0 1,0 {2 * r:.2f},0 a{r:.2f},{r:.2f} 0 1,0 {-2 * r:.2f},0 Z"


def rect(x: float, y: float, w: float, h: float) -> str:
    return f"M{x:.2f},{y:.2f} h{w:.2f} v{h:.2f} h{-w:.2f} Z"


def mark(cx: float, cy: float, r: float) -> list[dict]:
    """The radar disc as shapes: aircraft at the centre, rings, ticks, reticle brackets, two contacts.

    Each shape is {d, fill} or {d, stroke, width[, alpha, cap]}; only paths, so SVG and
    Android vector drawables draw the same thing.
    """
    out = [dict(d=circle(cx, cy, r), stroke=GREEN, width=r * 0.045)]
    for k in (0.42, 0.71):
        out.append(dict(d=circle(cx, cy, r * k), stroke=GREEN, width=r * 0.022, alpha=0.4))
    ticks = ""
    for deg in range(30, 360, 30):
        a = math.radians(deg)
        ticks += (f"M{cx + math.sin(a) * r * 0.88:.2f},{cy - math.cos(a) * r * 0.88:.2f} "
                  f"L{cx + math.sin(a) * r:.2f},{cy - math.cos(a) * r:.2f} ")
    out.append(dict(d=ticks, stroke=GREEN, width=r * 0.03))
    # Heading caret at north, pointing into the disc.
    s = r * 0.13
    out.append(dict(d=f"M{cx - s:.2f},{cy - r - s * 0.9:.2f} L{cx + s:.2f},{cy - r - s * 0.9:.2f} L{cx:.2f},{cy - r + s * 0.6:.2f} Z", fill=GREEN))
    # Reticle corner brackets.
    b, L = r * 0.36, r * 0.14
    brackets = ""
    for sx in (-1, 1):
        for sy in (-1, 1):
            x, y = cx + sx * b, cy + sy * b
            brackets += f"M{x:.2f},{y - sy * L:.2f} L{x:.2f},{y:.2f} L{x - sx * L:.2f},{y:.2f} "
    out.append(dict(d=brackets, stroke=WHITE, width=r * 0.04, cap="square"))
    # Quadcopter (the FLIGHT button icon): X frame and four rotors.
    q, rr, sw = r * 0.17, r * 0.075, r * 0.04
    out.append(dict(d=f"M{cx - q:.2f},{cy - q:.2f} L{cx + q:.2f},{cy + q:.2f} M{cx + q:.2f},{cy - q:.2f} L{cx - q:.2f},{cy + q:.2f}",
                    stroke=WHITE, width=sw, cap="round"))
    for sx in (-1, 1):
        for sy in (-1, 1):
            out.append(dict(d=circle(cx + sx * q, cy + sy * q, rr), fill=INK))
            out.append(dict(d=circle(cx + sx * q, cy + sy * q, rr), stroke=WHITE, width=sw))
    out.append(dict(d=rect(cx - r * 0.06, cy - r * 0.06, r * 0.12, r * 0.12), fill=GREEN))
    # Contacts in their canonical affiliation colours: hostile diamond, friendly rectangle.
    hx, hy, d = cx + r * 0.42, cy + r * 0.47, r * 0.075
    out.append(dict(d=f"M{hx:.2f},{hy - d:.2f} L{hx + d:.2f},{hy:.2f} L{hx:.2f},{hy + d:.2f} L{hx - d:.2f},{hy:.2f} Z", fill=RED))
    fx, fy = cx - r * 0.6, cy - r * 0.3
    out.append(dict(d=rect(fx - r * 0.08, fy - r * 0.05, r * 0.16, r * 0.1), fill=BLUE))
    return out


def svg_shapes(shapes: list[dict]) -> str:
    def one(s: dict) -> str:
        if "fill" in s:
            return f'<path d="{s["d"]}" fill="{s["fill"]}"/>'
        extra = f' stroke-opacity="{s["alpha"]}"' if "alpha" in s else ""
        extra += f' stroke-linecap="{s["cap"]}"' if "cap" in s else ""
        return f'<path d="{s["d"]}" fill="none" stroke="{s["stroke"]}" stroke-width="{s["width"]:.2f}"{extra}/>'
    return "\n  ".join(one(s) for s in shapes)


def vector_shapes(shapes: list[dict]) -> str:
    def one(s: dict) -> str:
        if "fill" in s:
            return f'    <path android:pathData="{s["d"]}" android:fillColor="{s["fill"]}" />'
        extra = f' android:strokeAlpha="{s["alpha"]}"' if "alpha" in s else ""
        extra += f' android:strokeLineCap="{s["cap"]}"' if "cap" in s else ""
        return (f'    <path android:pathData="{s["d"]}" android:strokeColor="{s["stroke"]}" '
                f'android:strokeWidth="{s["width"]:.2f}"{extra} />')
    return "\n".join(one(s) for s in shapes)


def text(font: TTFont, s: str, x: float, baseline: float, size: float, fill: str, spacing: float = 0.0) -> tuple[str, float]:
    """`s` as one path in Rajdhani Bold; returns (svg, advance width)."""
    glyphs, cmap = font.getGlyphSet(), font.getBestCmap()
    scale = size / font["head"].unitsPerEm
    pen = SVGPathPen(glyphs)
    pos = x
    for ch in s:
        g = cmap[ord(ch)]
        glyphs[g].draw(TransformPen(pen, (scale, 0, 0, -scale, pos, baseline)))
        pos += glyphs[g].width * scale + spacing * size
    return f'<path d="{pen.getCommands()}" fill="{fill}"/>', pos - x - spacing * size


def main() -> None:
    font = TTFont(FONT)
    (OUT / "logo-mark.svg").write_text(f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
  <title>R3AKT UAS</title>
  <rect width="512" height="512" rx="96" fill="{INK}"/>
  {svg_shapes(mark(256, 268, 188))}
</svg>
''')

    h = 200
    word, ww = text(font, "R3AKT", 230, 118, 104, WHITE, 0.04)
    _, uw = text(font, "UAS", 0, 0, 44, GREEN, 0.3)
    # UAS sits in a status-cell box like the cockpit's IN FLIGHT badge.
    bx, by, bw, bh = 236, 136, uw + 36, 50
    uas, _ = text(font, "UAS", bx + 18, by + 39, 44, GREEN, 0.3)
    width = int(max(230 + ww, bx + bw) + 32)
    (OUT / "logo.svg").write_text(f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width} {h}" width="{width}" height="{h}">
  <title>R3AKT UAS</title>
  <rect width="{width}" height="{h}" rx="24" fill="{INK}"/>
  {svg_shapes(mark(110, 104, 76))}
  {word}
  <rect x="{bx}" y="{by}" width="{bw:.1f}" height="{bh}" fill="none" stroke="{GREEN}" stroke-width="3"/>
  {uas}
  <rect x="{bx + bw + 14:.1f}" y="{by + bh / 2 - 4}" width="{max(0, width - 32 - bx - bw - 14):.1f}" height="8" fill="{GREEN}"/>
</svg>
''')

    # Adaptive icon foreground: 108 dp canvas; the disc and caret stay inside the 66 dp safe circle.
    drawable = RES / "drawable"
    drawable.mkdir(exist_ok=True)
    (drawable / "ic_launcher_foreground.xml").write_text(f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by docs/brand/make_logo.py; edit that script, not this file. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
{vector_shapes(mark(54, 55.6, 27.5))}
</vector>
''')


if __name__ == "__main__":
    main()

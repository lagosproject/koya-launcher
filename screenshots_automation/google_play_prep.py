"""
google_play_prep.py — Google Play Store image compliance tool for Koya Launcher
=============================================================================
Converts all raw phone screenshots (1080×2412 or 1080×2400) to Google Play-valid 9:16
images (1080×1920), generates beautiful showcase cards, and creates a premium
1024×500 Feature Graphic banner.

Output: google_play/{lang}/  ← upload these directly to Play Store
  showcase_home.png          ← Feature card: Home Screen
  showcase_drawer.png        ← Feature card: App Drawer
  showcase_settings.png      ← Feature card: Settings Screen

Output: google_play/feature_graphic.png  ← 1024×500 store banner

Run:  python3 google_play_prep.py
"""

import os, sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter

# ─── Constants ─────────────────────────────────────────────────────────────────

OUT_W = 1080
OUT_H = 1920

STATUS_BAR = 110   # pixels at top of phone screenshot to crop (status bar)
NAV_BAR    = 48    # pixels at bottom (nav gesture bar)

LANGS = ["en-US", "es-ES", "fr-FR"]
SRC_DIR = "screenshots"
OUT_DIR = "google_play"

# Brand colours (Koya: Charcoal, Washi Paper, Terracotta/Vermilion)
ACCENT  = (167, 104,  89)   # Terracotta/Vermilion #a76859
BG_DARK = ( 28,  25,  23)   # Warm Charcoal #1C1917
WHITE   = (250, 250, 249)   # Washi Paper #FAFAF9
MUTED   = (168, 162, 158)   # Stone Gray #A8A29E


# ─── Font helpers ──────────────────────────────────────────────────────────────

def _try_fonts(paths, size):
    for p in paths:
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()

def font_mono(size, bold=False):
    return _try_fonts([
        f"/usr/share/fonts/truetype/dejavu/DejaVuSansMono{'-Bold' if bold else ''}.ttf",
        f"/usr/share/fonts/truetype/liberation/LiberationMono-{'Bold' if bold else 'Regular'}.ttf",
    ], size)

def font_sans(size, bold=False):
    return _try_fonts([
        f"/usr/share/fonts/truetype/dejavu/DejaVuSans{'-Bold' if bold else ''}.ttf",
        f"/usr/share/fonts/truetype/liberation/LiberationSans-{'Bold' if bold else 'Regular'}.ttf",
    ], size)


# ─── Drawing helpers ───────────────────────────────────────────────────────────

def center_text(draw, text, y, font, fill, width=OUT_W):
    bbox = draw.textbbox((0, 0), text, font=font)
    x = (width - (bbox[2] - bbox[0])) // 2
    draw.text((x, y), text, font=font, fill=fill)

def wrap_text_centered(draw, text, y, font, fill, max_w, line_gap=10, width=OUT_W):
    """Word-wrap and center each line. Returns total height used."""
    words = text.split()
    lines, cur = [], ""
    for w in words:
        test = f"{cur} {w}".strip()
        if draw.textbbox((0,0), test, font=font)[2] <= max_w:
            cur = test
        else:
            if cur: lines.append(cur)
            cur = w
    if cur: lines.append(cur)

    lh = draw.textbbox((0,0), "Ag", font=font)[3] + line_gap
    for i, ln in enumerate(lines):
        center_text(draw, ln, y + i*lh, font, fill, width)
    return len(lines) * lh

def accent_rule(draw, y, length=140, color=None, width=OUT_W):
    color = color or (*ACCENT, 200)
    cx = width // 2
    draw.line([(cx - length//2, y), (cx + length//2, y)], fill=color, width=2)

def dark_gradient(w, h, tint_ratio=0.22):
    img = Image.new("RGB", (w, h), BG_DARK)
    d = ImageDraw.Draw(img)
    for i in range(h):
        t = i / h
        r = int(BG_DARK[0] + (ACCENT[0] - BG_DARK[0]) * t * tint_ratio)
        g = int(BG_DARK[1] + (ACCENT[1] - BG_DARK[1]) * t * tint_ratio * 0.6)
        b = int(BG_DARK[2] + (ACCENT[2] - BG_DARK[2]) * t * tint_ratio * 0.3)
        d.line([(0,i),(w,i)], fill=(r,g,b))
    return img

def rounded_rect_mask(size, radius):
    mask = Image.new("L", size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([(0,0),(size[0]-1,size[1]-1)], radius=radius, fill=255)
    return mask

def apply_shadow(img, blur=18, offset=(6,10), color=(0,0,0,160)):
    pad = blur * 2
    sw, sh = img.width + pad + abs(offset[0]), img.height + pad + abs(offset[1])
    shadow = Image.new("RGBA", (sw, sh), (0,0,0,0))
    stamp  = Image.new("RGBA", img.size, color)
    mask   = img.split()[3]
    ox, oy = pad//2 + max(0, offset[0]), pad//2 + max(0, offset[1])
    shadow.paste(stamp, (ox, oy), mask)
    shadow = shadow.filter(ImageFilter.GaussianBlur(blur))
    ix, iy = pad//2 + max(0, -offset[0]), pad//2 + max(0, -offset[1])
    shadow.paste(img, (ix, iy), img)
    return shadow, (ix - ox, iy - oy)


# ─── Feature Card Generation ───────────────────────────────────────────────────

def make_showcase_card(phone_path, headline, subtext, out_path):
    """
    Build a 1080×1920 Google Play showcase card.
    Top 38%  → dark gradient with title text
    Bottom 62% → phone screenshot (status bar cropped, rounded, shadowed)
    """
    W, H = OUT_W, OUT_H
    TEXT_H = int(H * 0.38)    # 729 px
    PHONE_H = H - TEXT_H       # 1191 px

    # ── Background
    canvas = dark_gradient(W, H).convert("RGBA")
    draw   = ImageDraw.Draw(canvas)

    # ── Text section
    f_label = font_mono(26, bold=False)
    f_head  = font_mono(62, bold=True)
    f_sub   = font_sans(33, bold=False)

    y = 76
    center_text(draw, "KOYA", y, f_label, (*ACCENT, 210))
    y += 46
    accent_rule(draw, y, color=(*ACCENT, 150))
    y += 26

    # Headline
    wrap_text_centered(draw, headline, y, f_head, (*WHITE, 255), max_w=W - 80)
    y += 80
    accent_rule(draw, y, color=(*ACCENT, 180), length=120)
    y += 22
    wrap_text_centered(draw, subtext, y, f_sub, (*MUTED, 220), max_w=W - 100)

    # ── Phone screenshot embed
    raw  = Image.open(phone_path).convert("RGBA")
    # Crop status bar
    cropped = raw.crop((0, STATUS_BAR, raw.width, raw.height - NAV_BAR))
    # Scale to fill PHONE_H
    scale   = PHONE_H / cropped.height
    pw      = int(cropped.width * scale)
    ph      = PHONE_H
    scaled  = cropped.resize((pw, ph), Image.LANCZOS)
    # Rounded corners
    mask    = rounded_rect_mask((pw, ph), radius=42)
    scaled.putalpha(mask)
    # Drop shadow
    shadowed, (six, siy) = apply_shadow(scaled, blur=18, offset=(6, 12))
    # Center horizontally, top at TEXT_H
    sx = (W - shadowed.width) // 2
    sy = TEXT_H - siy
    canvas.paste(shadowed, (sx, sy), shadowed)

    # Soft vertical fade between text and phone areas
    fade_h = 120
    fade   = Image.new("RGBA", (W, fade_h))
    fd     = ImageDraw.Draw(fade)
    for i in range(fade_h):
        a = int(255 * ((1 - i/fade_h) ** 1.6))
        fd.line([(0,i),(W,i)], fill=(*BG_DARK, a))
    canvas.alpha_composite(fade, (0, TEXT_H))

    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    canvas.convert("RGB").save(out_path, "PNG", optimize=True)
    size_kb = os.path.getsize(out_path) // 1024
    print(f"  ✅ {out_path}  ({OUT_W}×{OUT_H}, {size_kb} KB)")


# ─── Feature Graphic Generation (1024×500) ─────────────────────────────────────

def draw_koya_logo(draw, cx, cy, height, foreground_color, accent_color):
    """
    Draws the Koya logo in vector path logic.
    height specifies the vertical scale of the logo.
    """
    # The source height is 128.80893. Scale is height / 128.80893.
    scale = height / 128.80893
    
    # Let's map coordinates:
    # translate coordinates from source layout: translate(205.86948, -253.50184)
    tx = cx - (128.80893 / 2) * scale
    ty = cy - (128.80893 / 2) * scale
    
    def transform_pt(px, py):
        # Apply SVG translation
        tx_svg = px + 205.86948
        ty_svg = py - 253.50184
        # Apply scaling and final translation
        rx = tx + tx_svg * scale
        ry = ty + ty_svg * scale
        return rx, ry

    # 1. Draw main 'K' cabin structure
    # SVG Path commands:
    # m -194.3809,253.50184 v 128.80867 h 12.47624 v -36.73367 l 40.40787,-39.8539 40.44714,39.89266 v 36.69491 h 12.476762 v -41.9132 l -44.040202,-43.43559 44.063973,-43.45988 h -17.767393 l -75.58815,74.551 v -74.551 z
    # Let's rebuild coordinates sequentially:
    pts = []
    
    # Start: m -194.3809, 253.50184
    cx_s, cy_s = -194.3809, 253.50184
    pts.append(transform_pt(cx_s, cy_s))
    
    # v 128.80867
    cy_s += 128.80867
    pts.append(transform_pt(cx_s, cy_s))
    
    # h 12.47624
    cx_s += 12.47624
    pts.append(transform_pt(cx_s, cy_s))
    
    # v -36.73367
    cy_s -= 36.73367
    pts.append(transform_pt(cx_s, cy_s))
    
    # l 40.40787, -39.8539
    cx_s += 40.40787
    cy_s -= 39.8539
    pts.append(transform_pt(cx_s, cy_s))
    
    # l 40.44714, 39.89266
    cx_s += 40.44714
    cy_s += 39.89266
    pts.append(transform_pt(cx_s, cy_s))
    
    # v 36.69491
    cy_s += 36.69491
    pts.append(transform_pt(cx_s, cy_s))
    
    # h 12.476762
    cx_s += 12.476762
    pts.append(transform_pt(cx_s, cy_s))
    
    # v -41.9132
    cy_s -= 41.9132
    pts.append(transform_pt(cx_s, cy_s))
    
    # l -44.040202, -43.43559
    cx_s -= 44.040202
    cy_s -= 43.43559
    pts.append(transform_pt(cx_s, cy_s))
    
    # l 44.063973, -43.45988
    cx_s += 44.063973
    cy_s -= 43.45988
    pts.append(transform_pt(cx_s, cy_s))
    
    # h -17.767393
    cx_s -= 17.767393
    pts.append(transform_pt(cx_s, cy_s))
    
    # l -75.58815, 74.551
    cx_s -= 75.58815
    cy_s += 74.551
    pts.append(transform_pt(cx_s, cy_s))
    
    # v -74.551
    cy_s -= 74.551
    pts.append(transform_pt(cx_s, cy_s))
    
    draw.polygon(pts, fill=foreground_color)

    # 2. Draw rounded square widget representation
    # Rect bounds: x="-163.45758", y="338.29114", width="44.019638", height="44.019638", ry="7.2854557"
    rx, ry = transform_pt(-163.45758, 338.29114)
    rw, rh = 44.019638 * scale, 44.019638 * scale
    rrad = 7.2854557 * scale
    
    draw.rounded_rectangle([rx, ry, rx + rw, ry + rh], radius=rrad, fill=accent_color)


def make_feature_graphic(out_path):
    """
    Generates a 1024×500 Store Banner (Feature Graphic) following best practices:
      - Clean, bold branding
      - Premium charcoal gradient
      - Centered large Koya logo in white and terracotta accent
      - Stylized title and subtitle
    """
    W, H = 1024, 500
    
    # Background: charcoal gradient
    canvas = Image.new("RGB", (W, H), BG_DARK)
    draw = ImageDraw.Draw(canvas)
    for i in range(H):
        ratio = i / H
        r = int(BG_DARK[0] + (ACCENT[0] - BG_DARK[0]) * ratio * 0.15)
        g = int(BG_DARK[1] + (ACCENT[1] - BG_DARK[1]) * ratio * 0.08)
        b = int(BG_DARK[2] + (ACCENT[2] - BG_DARK[2]) * ratio * 0.04)
        draw.line([(0, i), (W, i)], fill=(r, g, b))
        
    # Draw large Koya logo in center
    logo_h = 190
    draw_koya_logo(draw, cx=W//2, cy=H//2 - 50, height=logo_h, foreground_color=WHITE, accent_color=ACCENT)
    
    # Draw Text
    f_title = font_mono(44, bold=True)
    f_sub = font_sans(20, bold=False)
    
    y = H//2 + 70
    center_text(draw, "KOYA", y, f_title, WHITE, width=W)
    
    y += 58
    accent_rule(draw, y, length=120, color=(*ACCENT, 200), width=W)
    
    y += 18
    center_text(draw, "Distraction-free home launcher", y, f_sub, MUTED, width=W)
    
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    canvas.save(out_path, "PNG", optimize=True)
    size_kb = os.path.getsize(out_path) // 1024
    print(f"  🎨 Feature Graphic → {out_path}  ({W}×{H}, {size_kb} KB)")


# ─── Showcase Copy per Language ────────────────────────────────────────────────

SHOWCASES = {
    "en-US": [
        ("home_screenshot.png",     "ESSENTIALS ONLY",    "A clean clock, battery bar, and a single widget slot. Keep it minimal.", "showcase_home.png"),
        ("drawer_screenshot.png",   "INSTANT SEARCH",     "Find and launch your apps instantly with a fast, alphabetical app list.", "showcase_drawer.png"),
        ("settings_screenshot.png", "TAILORED FIT",       "Customize themes, text sizes, and shortcuts to suit your daily focus.",  "showcase_settings.png"),
    ],
    "es-ES": [
        ("home_screenshot.png",     "SOLO LO ESENCIAL",   "Un reloj limpio, barra de batería y un solo espacio para widget.",      "showcase_home.png"),
        ("drawer_screenshot.png",   "BÚSQUEDA AL INSTANTE","Encuentra y abre tus apps de inmediato con una lista alfabética rápida.", "showcase_drawer.png"),
        ("settings_screenshot.png", "A TU MEDIDA",         "Personaliza temas, tamaños de texto y accesos directos según tu enfoque.", "showcase_settings.png"),
    ],
    "fr-FR": [
        ("home_screenshot.png",     "L'ESSENTIEL UNIQUEMENT", "Une horloge épurée, une barre de batterie et un unique emplacement de widget.", "showcase_home.png"),
        ("drawer_screenshot.png",   "RECHERCHE INSTANTANÉE",   "Trouvez et lancez vos applications instantanément avec une liste fluide.", "showcase_drawer.png"),
        ("settings_screenshot.png", "SUR MESURE",             "Personnalisez les thèmes, tailles de texte et raccourcis pour rester concentré.", "showcase_settings.png"),
    ],
}


# ─── Main ──────────────────────────────────────────────────────────────────────

def process_lang(lang):
    src = f"{SRC_DIR}/{lang}"
    out = f"{OUT_DIR}/{lang}"
    print(f"\n{'─'*54}")
    print(f"  Language Track: {lang}")
    print(f"{'─'*54}")

    for phone_file, headline, subtext, out_file in SHOWCASES[lang]:
        phone_path = f"{src}/{phone_file}"
        out_path   = f"{out}/{out_file}"
        if not os.path.exists(phone_path):
            print(f"  ⚠  {phone_path} not found — skipping showcase")
            continue
            
        make_showcase_card(phone_path, headline, subtext, out_path)


def main():
    print("=" * 60)
    print("  Koya Launcher — Google Play Store Resource Preparation")
    print("=" * 60)

    # 1. Process Screenshots per Language
    for lang in LANGS:
        process_lang(lang)

    # 2. Generate Feature Graphic
    print(f"\n{'─'*54}")
    print("  Feature Graphic Banner")
    print(f"{'─'*54}")
    make_feature_graphic(f"{OUT_DIR}/feature_graphic.png")

    # Summary
    print(f"\n{'='*60}")
    print("  PREPARATION SUMMARY")
    print(f"{'='*60}")
    total = 0
    for lang in LANGS:
        out = f"{OUT_DIR}/{lang}"
        if not os.path.exists(out):
            continue
        files = sorted(f for f in os.listdir(out) if f.endswith(".png"))
        count = len(files)
        total += count
        status = "✅" if count == 3 else "⚠ "
        print(f"  {status} {lang}: {count}/3 screenshots generated in {out}/")
        for f in files:
            kb = os.path.getsize(f"{out}/{f}") // 1024
            print(f"       {f}  ({kb} KB)")
            
    fg_path = f"{OUT_DIR}/feature_graphic.png"
    if os.path.exists(fg_path):
        kb = os.path.getsize(fg_path) // 1024
        print(f"  ✅ Feature Graphic: generated successfully ({kb} KB) → {fg_path}")
    else:
        print("  ❌ Feature Graphic: not found!")
    
    print("\n  All done! Staging completed.")


if __name__ == "__main__":
    main()

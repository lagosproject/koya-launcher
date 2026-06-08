"""
google_play_prep_tablet.py — Google Play Store image compliance tool for Koya Launcher (Tablet)
=============================================================================
Converts all raw tablet screenshots (1080×2412 captured with density 280) to Google Play-valid
9:16 images (1080×1920), and generates beautiful showcase cards. Copies output to both 
7-inch and 10-inch categories.

Output: 
  google_play_tablet/7_inch/{lang}/
  google_play_tablet/10_inch/{lang}/
  
  Files:
    showcase_home.png          ← Feature card: Home Screen
    showcase_gestures.png      ← Feature card: Swipe Gestures
    showcase_drawer.png        ← Feature card: App Drawer
    showcase_settings.png      ← Feature card: Settings Screen

Run:  python3 google_play_prep_tablet.py
"""

import os, sys, shutil
from PIL import Image, ImageDraw, ImageFont, ImageFilter

# ─── Constants ─────────────────────────────────────────────────────────────────

OUT_W = 1080
OUT_H = 1920

STATUS_BAR = 96    # pixels at top of tablet screenshot to crop (status bar)
NAV_BAR    = 132   # pixels at bottom (nav bar)

LANGS = ["en-US", "es-ES", "fr-FR"]
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
SRC_DIR = os.path.join(SCRIPT_DIR, "screenshots_tablet")
OUT_DIR = os.path.join(SCRIPT_DIR, "google_play_tablet")

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


# ─── Feature Card Helpers ──────────────────────────────────────────────────────

def get_wrap_height(draw, text, font, max_w, line_gap):
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
    return len(lines) * lh


def draw_gesture_arrows(img):
    draw = ImageDraw.Draw(img)
    w, h = img.size
    cx = w // 2
    cy = 880
    
    accent_color = (*ACCENT, 230)
    white_color = (*WHITE, 230)
    
    # Draw central touch anchor circle
    draw.ellipse([cx - 14, cy - 14, cx + 14, cy + 14], fill=accent_color, outline=white_color, width=2)
    
    # 1. Swipe Up Arrow
    draw.line([(cx, cy - 35), (cx, cy - 200)], fill=white_color, width=4)
    draw.polygon([(cx, cy - 215), (cx - 12, cy - 195), (cx + 12, cy - 195)], fill=white_color)
    
    # 2. Swipe Right Arrow
    draw.line([(cx + 35, cy), (cx + 170, cy)], fill=white_color, width=4)
    draw.polygon([(cx + 185, cy), (cx + 165, cy - 12), (cx + 165, cy + 12)], fill=white_color)

    # 3. Swipe Left Arrow
    draw.line([(cx - 35, cy), (cx - 170, cy)], fill=white_color, width=4)
    draw.polygon([(cx - 185, cy), (cx - 165, cy - 12), (cx - 165, cy + 12)], fill=white_color)


LABEL_TASKS = {
    "en-US": "Tasks & Lists",
    "es-ES": "Tareas y Listas",
    "fr-FR": "Tâches et Listes"
}

LABEL_MUSIC = {
    "en-US": "Music / Weather",
    "es-ES": "Música / Tiempo",
    "fr-FR": "Musique / Météo"
}

def draw_split_widget_overlay(img, lang, phone_path, phone_h_scale_base):
    """
    Applies a diagonal split screen overlay on the widget slot of the Home Screen screenshot,
    scaling bounds correctly for the tablet dimensions.
    """
    # Base configuration: scaled to 1460 height
    w, h = 523, 477
    x_offset, y_offset = 26, 318
    
    config_path = os.path.join(SCRIPT_DIR, "split_config_tablet.json")
    if not os.path.exists(config_path):
        config_path = os.path.join(SCRIPT_DIR, "split_config.json")
    p1 = None
    p2 = None
    show_labels = True
    if os.path.exists(config_path):
        try:
            import json
            with open(config_path, "r") as f:
                config = json.load(f)
                
                # Check if the config contains coordinates from the configurator (1080x1920 space)
                raw_img = Image.open(phone_path)
                raw_w, raw_h = raw_img.size
                scale = float(phone_h_scale_base) / (raw_h - STATUS_BAR - NAV_BAR)
                
                if "x_offset" in config:
                    x_offset = int(config["x_offset"] * scale)
                    y_offset = int((config["y_offset"] - STATUS_BAR) * scale)
                    w = int(config["width"] * scale)
                    h = int(config["height"] * scale)
                    
                    p1_orig = config.get("p1", [0, config["height"]])
                    p2_orig = config.get("p2", [config["width"], 0])
                    p1 = [p1_orig[0] * scale, p1_orig[1] * scale]
                    p2 = [p2_orig[0] * scale, p2_orig[1] * scale]
                else:
                    x_offset = config.get("x_offset", x_offset)
                    y_offset = config.get("y_offset", y_offset)
                    w = config.get("width", w)
                    h = config.get("height", h)
                    p1 = config.get("p1", None)
                    p2 = config.get("p2", None)
                
                show_labels = config.get("show_labels", show_labels)
        except Exception as e:
            print(f"  ⚠ Failed to load split_config.json: {e}")
            
    if p1 is None:
        p1 = [0, h]
    if p2 is None:
        p2 = [w, 0]
    
    # Check if a real second screenshot with a different widget exists
    phone_widget2_path = phone_path.replace("home_screenshot.png", "home_widget2_screenshot.png")
    
    use_custom_music = True
    widget2_img = None
    
    if os.path.exists(phone_widget2_path):
        try:
            raw2 = Image.open(phone_widget2_path).convert("RGBA")
            cropped2 = raw2.crop((0, STATUS_BAR, raw2.width, raw2.height - NAV_BAR))
            scale_factor = img.height / cropped2.height
            pw2 = int(cropped2.width * scale_factor)
            ph2 = img.height
            scaled2 = cropped2.resize((pw2, ph2), Image.LANCZOS)
            widget2_img = scaled2.crop((x_offset, y_offset, x_offset + w, y_offset + h))
            use_custom_music = False
        except Exception as e:
            print(f"  ⚠ Failed to load second widget screenshot: {e}. Falling back to music widget.")
            use_custom_music = True
            
    if use_custom_music:
        widget2_img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        mdraw = ImageDraw.Draw(widget2_img)
        mdraw.rounded_rectangle([0, 0, w, h], radius=28, fill=(18, 18, 18, 245))
        
        # Draw album cover (scaled for tablet proportions)
        cover_size = int(120 * (h/477.0))
        cx = int(360 * (w/523.0))
        cy = int(160 * (h/477.0))
        mdraw.rounded_rectangle([cx, cy, cx + cover_size, cy + cover_size], radius=16, fill=(167, 104, 89, 255))
        mdraw.ellipse([cx + int(25*(h/477.0)), cy + int(25*(h/477.0)), cx + int(95*(h/477.0)), cy + int(95*(h/477.0))], fill=(28, 25, 23, 255))
        mdraw.ellipse([cx + int(50*(h/477.0)), cy + int(50*(h/477.0)), cx + int(70*(h/477.0)), cy + int(70*(h/477.0))], fill=(250, 250, 249, 255))
        
        f_title = font_sans(max(14, int(26 * (h/477.0))), bold=True)
        f_artist = font_sans(max(11, int(20 * (h/477.0))), bold=False)
        mdraw.text((int(220*(w/523.0)), int(295*(h/477.0))), "Golden Hour", font=f_title, fill=WHITE)
        mdraw.text((int(220*(w/523.0)), int(330*(h/477.0))), "JVKE", font=f_artist, fill=MUTED)
        
        bar_y = int(375 * (h/477.0))
        bar_w = int(260 * (w/523.0))
        mdraw.rounded_rectangle([int(220*(w/523.0)), bar_y, int(220*(w/523.0)) + bar_w, bar_y + 6], radius=3, fill=(168, 162, 158, 80))
        mdraw.rounded_rectangle([int(220*(w/523.0)), bar_y, int(220*(w/523.0)) + int(bar_w * 0.45), bar_y + 6], radius=3, fill=ACCENT)
        
        # Simple play triangle
        mdraw.polygon([(int(230*(w/523.0)), int(400*(h/477.0))), (int(230*(w/523.0)), int(420*(h/477.0))), (int(242*(w/523.0)), int(410*(h/477.0)))], fill=WHITE)

    # Compile split masking
    def get_dist(p):
        px, py = p
        if py == 0: return px
        if px == w: return w + py
        if py == h: return w + h + (w - px)
        if px == 0: return w + h + w + (h - py)
        return 0

    d1 = get_dist(p1)
    d2 = get_dist(p2)
    
    corners = [
        ((w, 0), w),
        ((w, h), w + h),
        ((0, h), w + h + w),
        ((0, 0), w + h + w + h)
    ]
    
    pts1, pts2 = [], []
    if d1 < d2:
        pts1.append(tuple(p1))
        for c, cd in corners:
            if d1 < cd < d2: pts1.append(c)
        pts1.append(tuple(p2))
        
        pts2.append(tuple(p2))
        for c, cd in corners:
            if cd > d2 or cd < d1: pts2.append(c)
        pts2.append(tuple(p1))
    else:
        pts1.append(tuple(p2))
        for c, cd in corners:
            if d2 < cd < d1: pts1.append(c)
        pts1.append(tuple(p1))
        
        pts2.append(tuple(p1))
        for c, cd in corners:
            if cd > d1 or cd < d2: pts2.append(c)
        pts2.append(tuple(p2))

    mask1 = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask1).polygon(pts2, fill=255)
    
    mask2 = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask2).polygon(pts1, fill=255)
    
    widget_area = Image.new("RGBA", (w, h), (18, 18, 18, 255))
    widget1_img = img.crop((x_offset, y_offset, x_offset + w, y_offset + h))
    
    w1_rgba = widget1_img.convert("RGBA")
    w1_rgba.putalpha(mask1)
    widget_area.alpha_composite(w1_rgba, (0, 0))
    
    w2_rgba = widget2_img.convert("RGBA")
    w2_rgba.putalpha(mask2)
    widget_area.alpha_composite(w2_rgba, (0, 0))
    
    img.paste(widget_area, (x_offset, y_offset), widget_area)
    
    # Divider line
    draw = ImageDraw.Draw(img)
    draw.line([(x_offset + p1[0], y_offset + p1[1]), (x_offset + p2[0], y_offset + p2[1])], fill=(*WHITE, 255), width=3)
    
    if show_labels:
        f_lbl = font_sans(max(10, int(16 * (h/477.0))), bold=True)
        lbl_tasks = LABEL_TASKS.get(lang, "Tasks & Lists")
        lbl_music = LABEL_MUSIC.get(lang, "Music / Weather")
        
        if lbl_tasks:
            tasks_w = draw.textbbox((0,0), lbl_tasks, font=f_lbl)[2]
            bx1 = x_offset + 25
            by1 = y_offset + 25
            bx2 = bx1 + tasks_w + 20
            by2 = by1 + int(32 * (h/477.0))
            draw.rounded_rectangle([bx1, by1, bx2, by2], radius=16, fill=(18, 18, 18, 230), outline=(*MUTED, 120), width=1)
            draw.text((bx1 + 10, by1 + 4), lbl_tasks, font=f_lbl, fill=MUTED)
        
        if lbl_music:
            music_w = draw.textbbox((0,0), lbl_music, font=f_lbl)[2]
            mx1 = x_offset + w - music_w - 45
            my1 = y_offset + h - int(55 * (h/477.0))
            mx2 = mx1 + music_w + 20
            my2 = my1 + int(32 * (h/477.0))
            draw.rounded_rectangle([mx1, my1, mx2, my2], radius=16, fill=(18, 18, 18, 230), outline=(*ACCENT, 120), width=1)
            draw.text((mx1 + 10, my1 + 4), lbl_music, font=f_lbl, fill=ACCENT)


# ─── Showcase Card Generation ──────────────────────────────────────────────────

def make_showcase_card(phone_path, headline, subtext, out_path, lang=None):
    """
    Build a 1080×1920 Google Play showcase card for tablets.
    Layout: Top text block, bottom screenshot block.
    """
    W, H = OUT_W, OUT_H
    TEXT_H = 460               # Start screenshot below this
    PHONE_H = H - TEXT_H       # 1460 px

    # ── Background
    canvas = dark_gradient(W, H).convert("RGBA")
    draw   = ImageDraw.Draw(canvas)

    # ── 1. Text Section (Top)
    f_label = font_sans(24, bold=True)
    f_head  = font_sans(56, bold=True)
    f_sub   = font_sans(32, bold=False)

    # Draw the elements sequentially in the top panel
    center_text(draw, "K  O  Y  A", 54, f_label, (*ACCENT, 210))
    accent_rule(draw, 102, length=100, color=(*ACCENT, 140))
    
    # Headline
    y_used = wrap_text_centered(draw, headline, 134, f_head, (*WHITE, 255), max_w=W - 100, line_gap=16)
    accent_rule(draw, 134 + y_used + 16, length=60, color=(*ACCENT, 160))
    
    # Subtext
    wrap_text_centered(draw, subtext, 134 + y_used + 46, f_sub, (*MUTED, 220), max_w=W - 120, line_gap=12)

    # ── 2. Screenshot embed (Bottom)
    raw  = Image.open(phone_path).convert("RGBA")
    cropped = raw.crop((0, STATUS_BAR, raw.width, raw.height - NAV_BAR))
    
    # Scale to fill PHONE_H
    scale   = PHONE_H / cropped.height
    pw      = int(cropped.width * scale)
    ph      = PHONE_H
    scaled  = cropped.resize((pw, ph), Image.LANCZOS)
    
    # Create rounded corner mask
    mask    = rounded_rect_mask((pw, ph), radius=48)
    scaled.putalpha(mask)

    # Draw dynamic gesture overlays (arrows) directly on the scaled screenshot
    if "gestures" in out_path:
        draw_gesture_arrows(scaled)

    # Draw split widget overlay (diagonal split) directly on the scaled screenshot
    if out_path.endswith("showcase_home.png"):
        draw_split_widget_overlay(scaled, lang, phone_path, PHONE_H)
    
    # Draw a thin premium accent border around the screenshot
    border_draw = ImageDraw.Draw(scaled)
    border_draw.rounded_rectangle([(0, 0), (pw - 1, ph - 1)], radius=48, outline=(*ACCENT, 180), width=4)
    
    # Drop shadow
    shadowed, (six, siy) = apply_shadow(scaled, blur=22, offset=(0, 14))
    
    # Center horizontally, start at y = TEXT_H
    sx = (W - shadowed.width) // 2
    sy = TEXT_H - siy
    canvas.paste(shadowed, (sx, sy), shadowed)

    # Soft vertical fade-out transition between the text and screenshot areas
    fade_h = 100
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


# ─── Showcase Copy per Language ────────────────────────────────────────────────

SHOWCASES = {
    "en-US": [
        ("home_screenshot.png",     "ESSENTIALS ONLY",    "A clean clock, battery bar, and a single widget slot. Keep it minimal.", "showcase_home.png"),
        ("home_screenshot.png",     "SWIPE GESTURES",     "Swipe up for apps, swipe right to call, and swipe left to open the camera.", "showcase_gestures.png"),
        ("drawer_screenshot.png",   "INSTANT SEARCH",     "Find and launch your apps instantly with a fast, alphabetical app list.", "showcase_drawer.png"),
        ("settings_screenshot.png", "TAILORED FIT",       "Customize themes, text sizes, and shortcuts to suit your daily focus.",  "showcase_settings.png"),
    ],
    "es-ES": [
        ("home_screenshot.png",     "SOLO LO ESENCIAL",   "Un reloj limpio, barra de batería y un solo espacio para widget.",      "showcase_home.png"),
        ("home_screenshot.png",     "GESTOS RÁPIDOS",     "Desliza arriba para apps, derecha para llamar e izquierda para cámara.", "showcase_gestures.png"),
        ("drawer_screenshot.png",   "BÚSQUEDA AL INSTANTE","Encuentra y abre tus apps de inmediato con una lista alfabética rápida.", "showcase_drawer.png"),
        ("settings_screenshot.png", "A TU MEDIDA",         "Personaliza temas, tamaños de texto y accesos directos según tu enfoque.", "showcase_settings.png"),
    ],
    "fr-FR": [
        ("home_screenshot.png",     "L'ESSENTIEL UNIQUEMENT", "Une horloge épurée, une barre de batterie et un unique emplacement de widget.", "showcase_home.png"),
        ("home_screenshot.png",     "GESTES INTUITIFS",       "Glissez vers le haut pour les applis, à droite pour appeler, à gauche pour l'appareil photo.", "showcase_gestures.png"),
        ("drawer_screenshot.png",   "RECHERCHE INSTANTANÉE",   "Trouvez et lancez vos applications instantanément avec une liste fluide.", "showcase_drawer.png"),
        ("settings_screenshot.png", "SUR MESURE",             "Personnalisez les thèmes, tailles de texte et raccourcis pour rester concentré.", "showcase_settings.png"),
    ],
}


# ─── Main ──────────────────────────────────────────────────────────────────────

def process_lang(lang):
    src = f"{SRC_DIR}/{lang}"
    out_base = f"{OUT_DIR}/base/{lang}"
    print(f"\n{'─'*54}")
    print(f"  Language Track (Tablet): {lang}")
    print(f"{'─'*54}")

    # Process screenshots to base folder
    for phone_file, headline, subtext, out_file in SHOWCASES[lang]:
        phone_path = f"{src}/{phone_file}"
        out_path   = f"{out_base}/{out_file}"
        if not os.path.exists(phone_path):
            print(f"  ⚠  {phone_path} not found — skipping showcase")
            continue
            
        make_showcase_card(phone_path, headline, subtext, out_path, lang)

    # Copy to 7_inch and 10_inch categories
    out_7 = f"{OUT_DIR}/7_inch/{lang}"
    out_10 = f"{OUT_DIR}/10_inch/{lang}"
    os.makedirs(out_7, exist_ok=True)
    os.makedirs(out_10, exist_ok=True)

    if os.path.exists(out_base):
        for item in os.listdir(out_base):
            src_item = os.path.join(out_base, item)
            if os.path.isfile(src_item):
                shutil.copy(src_item, os.path.join(out_7, item))
                shutil.copy(src_item, os.path.join(out_10, item))
        print(f"  ✓ Copied processed {lang} assets to 7_inch/ and 10_inch/ folders.")


def main():
    print("=" * 60)
    print("  Koya Launcher — Google Play Store Tablet Resource Preparation")
    print("=" * 60)

    # 1. Process Screenshots per Language
    for lang in LANGS:
        process_lang(lang)

    # 2. Summary
    print(f"\n{'='*60}")
    print("  PREPARATION SUMMARY (TABLET)")
    print(f"{'='*60}")
    for size_cat in ["7_inch", "10_inch"]:
        print(f"\n  Category: {size_cat}")
        for lang in LANGS:
            out = f"{OUT_DIR}/{size_cat}/{lang}"
            if not os.path.exists(out):
                continue
            files = sorted(f for f in os.listdir(out) if f.endswith(".png"))
            print(f"    ✓ {lang}: {len(files)}/{len(SHOWCASES[lang])} screenshots generated in {out}/")
            for f in files:
                kb = os.path.getsize(f"{out}/{f}") // 1024
                print(f"         {f}  ({kb} KB)")
                
    print("\n  All done! Tablet staging completed.")


if __name__ == "__main__":
    main()

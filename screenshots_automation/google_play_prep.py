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
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
SRC_DIR = os.path.join(SCRIPT_DIR, "screenshots")
OUT_DIR = os.path.join(SCRIPT_DIR, "google_play")

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
    """
    Draws minimalist, beautiful translucent white swipe indicator arrows
    over the phone screenshot to demonstrate launcher gestures:
      - Swipe Up (to App Drawer)
      - Swipe Left (to Camera)
      - Swipe Right (to Phone)
    """
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

def draw_split_widget_overlay(img, lang, phone_path):
    """
    Applies a diagonal split screen overlay on the widget slot of the Home Screen screenshot,
    showing a Task/Todoist widget on the top-left and either a real second widget screenshot
    (home_widget2_screenshot.png) or a custom Music Player widget on the bottom-right.
    """
    w, h = 523, 477
    x_offset, y_offset = 26, 318
    
    # 1. Load custom split config from split_config.json if it exists
    config_path = os.path.join(os.path.dirname(__file__), "split_config.json")
    p1 = None
    p2 = None
    show_labels = True
    if os.path.exists(config_path):
        try:
            import json
            with open(config_path, "r") as f:
                config = json.load(f)
                
                # Check if the config contains coordinates from the configurator (1080x1920 space)
                # If so, scale them to the cropped-and-scaled space (1200 height)
                raw_img = Image.open(phone_path)
                raw_w, raw_h = raw_img.size
                scale = 1200.0 / (raw_h - STATUS_BAR - NAV_BAR)
                
                if "x_offset" in config:
                    # Convert original coordinates to cropped-and-scaled space
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
            # Let's scale to match the main screenshot scaling exactly!
            scale_factor = img.height / cropped2.height
            pw2 = int(cropped2.width * scale_factor)
            ph2 = img.height
            scaled2 = cropped2.resize((pw2, ph2), Image.LANCZOS)
            # Crop the widget region from the scaled second screenshot
            widget2_img = scaled2.crop((x_offset, y_offset, x_offset + w, y_offset + h))
            use_custom_music = False
        except Exception as e:
            print(f"  ⚠ Failed to load second widget screenshot: {e}. Falling back to music widget.")
            use_custom_music = True
            
    if use_custom_music:
        # Create the music player overlay image (RGBA)
        widget2_img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        mdraw = ImageDraw.Draw(widget2_img)
        # Matching translucent widget background
        mdraw.rounded_rectangle([0, 0, w, h], radius=28, fill=(18, 18, 18, 245))
        
        # Draw album cover
        cover_size = 120
        cx = 360
        cy = 160
        mdraw.rounded_rectangle([cx, cy, cx + cover_size, cy + cover_size], radius=16, fill=(167, 104, 89, 255))
        mdraw.ellipse([cx + 25, cy + 25, cx + 95, cy + 95], fill=(28, 25, 23, 255))
        mdraw.ellipse([cx + 50, cy + 50, cx + 70, cy + 70], fill=(250, 250, 249, 255))
        
        # Title / Artist
        f_title = font_sans(26, bold=True)
        f_artist = font_sans(20, bold=False)
        mdraw.text((220, 295), "Golden Hour", font=f_title, fill=WHITE)
        mdraw.text((220, 330), "JVKE", font=f_artist, fill=MUTED)
        
        # Progress bar
        bar_y = 375
        bar_w = 260
        mdraw.rounded_rectangle([220, bar_y, 220 + bar_w, bar_y + 6], radius=3, fill=(168, 162, 158, 80))
        mdraw.rounded_rectangle([220, bar_y, 220 + int(bar_w * 0.45), bar_y + 6], radius=3, fill=ACCENT)
        
        # Controls
        mdraw.polygon([(230, 400), (230, 420), (242, 410)], fill=WHITE)
        mdraw.polygon([(270, 402), (270, 418), (280, 410)], fill=MUTED)
        mdraw.line([(282, 402), (282, 418)], fill=MUTED, width=3)

    # Generate custom split polygons using perimeter distance sorting
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
    
    pts1 = []  # Bottom-Right (widget 2)
    pts2 = []  # Top-Left (widget 1)
    
    # Sort points clockwise
    if d1 < d2:
        pts1.append(tuple(p1))
        for c, cd in corners:
            if d1 < cd < d2:
                pts1.append(c)
        pts1.append(tuple(p2))
        
        pts2.append(tuple(p2))
        for c, cd in corners:
            if cd > d2 or cd < d1:
                pts2.append(c)
        pts2.append(tuple(p1))
    else:
        pts1.append(tuple(p2))
        for c, cd in corners:
            if d2 < cd < d1:
                pts1.append(c)
        pts1.append(tuple(p1))
        
        pts2.append(tuple(p1))
        for c, cd in corners:
            if cd > d1 or cd < d2:
                pts2.append(c)
        pts2.append(tuple(p2))

    # Create the split masks
    mask1 = Image.new("L", (w, h), 0)
    mask_draw1 = ImageDraw.Draw(mask1)
    mask_draw1.polygon(pts2, fill=255)
    
    mask2 = Image.new("L", (w, h), 0)
    mask_draw2 = ImageDraw.Draw(mask2)
    mask_draw2.polygon(pts1, fill=255)
    
    # 3. Create a clean container for the widget area with standard dark background
    widget_area = Image.new("RGBA", (w, h), (18, 18, 18, 255))
    
    # Crop the first widget from the original screenshot
    widget1_img = img.crop((x_offset, y_offset, x_offset + w, y_offset + h))
    
    # Paste widget 1 (top-left) with mask1
    w1_rgba = widget1_img.convert("RGBA")
    w1_rgba.putalpha(mask1)
    widget_area.alpha_composite(w1_rgba, (0, 0))
    
    # Paste widget 2 (bottom-right) with mask2
    w2_rgba = widget2_img.convert("RGBA")
    w2_rgba.putalpha(mask2)
    widget_area.alpha_composite(w2_rgba, (0, 0))
    
    # Paste the compiled clean widget area back onto the main screenshot
    img.paste(widget_area, (x_offset, y_offset), widget_area)
    
    # 4. Draw the diagonal divider line
    draw = ImageDraw.Draw(img)
    line_start = (x_offset + p1[0], y_offset + p1[1])
    line_end = (x_offset + p2[0], y_offset + p2[1])
    draw.line([line_start, line_end], fill=(*WHITE, 255), width=3)
    
    # 5. Add small descriptive labels with badge backgrounds for high readability
    if show_labels:
        f_lbl = font_sans(16, bold=True)
        
        lbl_tasks = LABEL_TASKS.get(lang, "Tasks & Lists")
        lbl_music = LABEL_MUSIC.get(lang, "Music / Weather")
        
        # Draw Tasks Badge
        if lbl_tasks:
            tasks_w = draw.textbbox((0,0), lbl_tasks, font=f_lbl)[2]
            bx1 = x_offset + 25
            by1 = y_offset + 25
            bx2 = bx1 + tasks_w + 20
            by2 = by1 + 32
            draw.rounded_rectangle([bx1, by1, bx2, by2], radius=16, fill=(18, 18, 18, 230), outline=(*MUTED, 120), width=1)
            draw.text((bx1 + 10, by1 + 4), lbl_tasks, font=f_lbl, fill=MUTED)
        
        # Draw Music Badge
        if lbl_music:
            music_w = draw.textbbox((0,0), lbl_music, font=f_lbl)[2]
            mx1 = x_offset + w - music_w - 45
            my1 = y_offset + h - 55
            mx2 = mx1 + music_w + 20
            my2 = my1 + 32
            draw.rounded_rectangle([mx1, my1, mx2, my2], radius=16, fill=(18, 18, 18, 230), outline=(*ACCENT, 120), width=1)
            draw.text((mx1 + 10, my1 + 4), lbl_music, font=f_lbl, fill=ACCENT)


# ─── Feature Graphic Generation ────────────────────────────────────────────────

def make_showcase_card(phone_path, headline, subtext, out_path, lang=None):
    """
    Build a 1080×1920 Google Play showcase card with:
      - Clean top-aligned phone screenshot (rounded, premium drop-shadow, subtle border)
      - Bottom copy layout featuring premium geometric sans-serif typography,
        dynamically centered vertically in the bottom panel to ensure balanced margins.
    """
    W, H = OUT_W, OUT_H
    PHONE_H = 1200
    
    # ── Background
    canvas = dark_gradient(W, H).convert("RGBA")
    draw   = ImageDraw.Draw(canvas)

    # ── 1. Phone screenshot embed (Centered at the top)
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

    # Draw dynamic gesture overlays (arrows) directly on the scaled phone screenshot
    if "gestures" in out_path:
        draw_gesture_arrows(scaled)

    # Draw split widget overlay (diagonal split) directly on the scaled phone screenshot
    if out_path.endswith("showcase_home.png"):
        draw_split_widget_overlay(scaled, lang, phone_path)
    
    # Draw a thin premium accent border around the phone screenshot
    border_draw = ImageDraw.Draw(scaled)
    border_draw.rounded_rectangle([(0, 0), (pw - 1, ph - 1)], radius=48, outline=(*ACCENT, 180), width=4)
    
    # Drop shadow
    shadowed, (six, siy) = apply_shadow(scaled, blur=22, offset=(0, 14))
    
    # Center horizontally, start at y = 100
    sx = (W - shadowed.width) // 2
    sy = 100 - siy
    canvas.paste(shadowed, (sx, sy), shadowed)

    # Soft vertical fade-out transition between the phone and bottom text areas
    fade_h = 100
    fade_y = 1250
    fade   = Image.new("RGBA", (W, fade_h))
    fd     = ImageDraw.Draw(fade)
    for i in range(fade_h):
        a = int(255 * ((i/fade_h) ** 1.6))
        fd.line([(0,i),(W,i)], fill=(*BG_DARK, a))
    canvas.alpha_composite(fade, (0, fade_y))

    # ── 2. Text section (Bottom)
    f_label = font_sans(24, bold=True)
    f_head  = font_sans(56, bold=True)
    f_sub   = font_sans(32, bold=False)

    # Calculate text heights and padding dynamically to center it perfectly
    bbox = draw.textbbox((0,0), "K  O  Y  A", font=f_label)
    label_h = bbox[3] - bbox[1]
    gap1 = 50
    rule1_w = 2
    gap2 = 36
    headline_h = get_wrap_height(draw, headline, f_head, W - 100, 16)
    gap3 = 24
    rule2_w = 2
    gap4 = 30
    subtext_h = get_wrap_height(draw, subtext, f_sub, W - 120, 12)

    total_text_h = label_h + gap1 + rule1_w + gap2 + headline_h + gap3 + rule2_w + gap4 + subtext_h

    # The bottom panel goes from y = 1300 to y = 1920
    panel_start = 1300
    panel_h = H - panel_start
    
    # Center the text block vertically in the panel
    y = panel_start + (panel_h - total_text_h) // 2

    # Draw the elements sequentially
    center_text(draw, "K  O  Y  A", y, f_label, (*ACCENT, 210))
    y += label_h + gap1
    accent_rule(draw, y, length=100, color=(*ACCENT, 140))
    y += rule1_w + gap2

    # Headline
    y_used = wrap_text_centered(draw, headline, y, f_head, (*WHITE, 255), max_w=W - 100, line_gap=16)
    y += y_used + gap3
    accent_rule(draw, y, length=60, color=(*ACCENT, 160))
    y += rule2_w + gap4
    
    # Subtext
    wrap_text_centered(draw, subtext, y, f_sub, (*MUTED, 220), max_w=W - 120, line_gap=12)

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
      - Split layout (logo on the left, copy on the right) to differentiate from FunCoStory
      - Premium charcoal gradient
      - Premium geometric sans-serif typography
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
        
    # Draw Koya logo on the left (centered vertically at x = 280, y = 250)
    logo_h = 220
    draw_koya_logo(draw, cx=280, cy=H//2, height=logo_h, foreground_color=WHITE, accent_color=ACCENT)
    
    # Draw Text on the right (centered vertically, left-aligned starting at x = 480)
    f_title = font_sans(54, bold=True)
    f_sub = font_sans(24, bold=False)
    
    # Draw brand title (widely spaced)
    title_text = "K O Y A"
    y = H//2 - 60
    draw.text((490, y), title_text, font=f_title, fill=WHITE)
    
    # Accent rule below title
    y += 84
    draw.line([(490, y), (610, y)], fill=(*ACCENT, 220), width=3)
    
    # Subtitle
    y += 24
    draw.text((490, y), "Distraction-free home launcher", font=f_sub, fill=MUTED)
    
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    canvas.save(out_path, "PNG", optimize=True)
    size_kb = os.path.getsize(out_path) // 1024
    print(f"  🎨 Feature Graphic → {out_path}  ({W}×{H}, {size_kb} KB)")


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
            
        make_showcase_card(phone_path, headline, subtext, out_path, lang)


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
        status = "✅" if count == len(SHOWCASES[lang]) else "⚠ "
        print(f"  {status} {lang}: {count}/{len(SHOWCASES[lang])} screenshots generated in {out}/")
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

import os
from PIL import Image, ImageDraw

# Brand Colors
BG_LIGHT = (250, 249, 246)  # Washi Paper light background #FAF9F6
LOGO_DARK = (52, 52, 50)     # Warm Charcoal/Dark Stone #343432
ACCENT = (167, 104, 89)      # Terracotta/Vermilion #A76859

def draw_koya_logo(draw, cx, cy, height, foreground_color, accent_color):
    """
    Draws the Koya logo in vector path logic.
    height specifies the vertical scale of the logo.
    """
    # The source height is 128.80893. Scale is height / 128.80893.
    scale = height / 128.80893
    
    # Translate coordinates from source layout: translate(205.86948, -253.50184)
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

    # 1. Rebuild coordinates for 'K' cabin structure
    pts = []
    cx_s, cy_s = -194.3809, 253.50184
    pts.append(transform_pt(cx_s, cy_s))
    
    cy_s += 128.80867
    pts.append(transform_pt(cx_s, cy_s))
    
    cx_s += 12.47624
    pts.append(transform_pt(cx_s, cy_s))
    
    cy_s -= 36.73367
    pts.append(transform_pt(cx_s, cy_s))
    
    cx_s += 40.40787
    cy_s -= 39.8539
    pts.append(transform_pt(cx_s, cy_s))
    
    cx_s += 40.44714
    cy_s += 39.89266
    pts.append(transform_pt(cx_s, cy_s))
    
    cy_s += 36.69491
    pts.append(transform_pt(cx_s, cy_s))
    
    cx_s += 12.476762
    pts.append(transform_pt(cx_s, cy_s))
    
    cy_s -= 41.9132
    pts.append(transform_pt(cx_s, cy_s))
    
    cx_s -= 44.040202
    cy_s -= 43.43559
    pts.append(transform_pt(cx_s, cy_s))
    
    cx_s += 44.063973
    cy_s -= 43.45988
    pts.append(transform_pt(cx_s, cy_s))
    
    cx_s -= 17.767393
    pts.append(transform_pt(cx_s, cy_s))
    
    cx_s -= 75.58815
    cy_s += 74.551
    pts.append(transform_pt(cx_s, cy_s))
    
    cy_s -= 74.551
    pts.append(transform_pt(cx_s, cy_s))
    
    draw.polygon(pts, fill=foreground_color)

    # 2. Draw rounded square widget representation
    rx, ry = transform_pt(-163.45758, 338.29114)
    rw, rh = 44.019638 * scale, 44.019638 * scale
    rrad = 7.2854557 * scale
    
    draw.rounded_rectangle([rx, ry, rx + rw, ry + rh], radius=rrad, fill=accent_color)


def generate_play_store_icon(out_path):
    W, H = 512, 512
    # Create high-quality RGBA canvas
    img = Image.new("RGBA", (W, H), BG_LIGHT)
    draw = ImageDraw.Draw(img)
    
    # Scale: Draw the logo mark centered at 512x512, with a height of 310 pixels (about 60% of the canvas)
    # This leaves a safe margin of ~100px all around so the logo isn't clipped by Google Play's mask
    logo_height = 310
    draw_koya_logo(draw, cx=W//2, cy=H//2, height=logo_height, foreground_color=LOGO_DARK, accent_color=ACCENT)
    
    # Save as PNG
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    img.save(out_path, "PNG", optimize=True)
    print(f"Generated Play Store Icon (512x512) -> {out_path}")


if __name__ == "__main__":
    script_dir = os.path.dirname(os.path.abspath(__file__))
    out_file = os.path.join(script_dir, "google_play", "play_store_icon.png")
    generate_play_store_icon(out_file)

import os
from PIL import Image, ImageDraw

def make_circle(img):
    # Crop to square first
    min_dim = min(img.size)
    left = (img.width - min_dim) / 2
    top = (img.height - min_dim) / 2
    img = img.crop((left, top, left + min_dim, top + min_dim))
    
    # Create mask
    mask = Image.new('L', img.size, 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse((0, 0) + img.size, fill=255)
    
    # Apply mask
    out = Image.new('RGBA', img.size, (0, 0, 0, 0))
    out.paste(img, mask=mask)
    return out

def generate_icons(source_path, res_dir):
    if not os.path.exists(source_path):
        print("Source image not found.")
        return

    # Sizes in pixels for legacy icons
    sizes = {
        "mdpi": 48,
        "hdpi": 72,
        "xhdpi": 96,
        "xxhdpi": 144,
        "xxxhdpi": 192
    }
    
    # Sizes in pixels for adaptive foreground (108dp)
    # mdpi: 108, hdpi: 162, xhdpi: 216, xxhdpi: 324, xxxhdpi: 432
    adaptive_sizes = {
        "mdpi": 108,
        "hdpi": 162,
        "xhdpi": 216,
        "xxhdpi": 324,
        "xxxhdpi": 432
    }

    try:
        with Image.open(source_path) as img:
            img = img.convert("RGBA")
            
            for density, size in sizes.items():
                folder = os.path.join(res_dir, f"mipmap-{density}")
                os.makedirs(folder, exist_ok=True)
                
                # Square (legacy)
                square = img.resize((size, size), Image.Resampling.LANCZOS)
                square.save(os.path.join(folder, "ic_launcher.png"), "PNG")
                
                # Round (legacy)
                round_img = make_circle(img).resize((size, size), Image.Resampling.LANCZOS)
                round_img.save(os.path.join(folder, "ic_launcher_round.png"), "PNG")
                
                # Adaptive Foreground
                # Since we don't know the logo content, we'll just scale it to fit the 108dp size
                # However, adaptive icons usually have the logo inside a 72dp safe zone. 
                # Let's scale the original image to the safe zone (72/108 = 2/3) and put it on a solid background (we'll extract corner color as background)
                bg_color = img.getpixel((0,0)) # Assumes corner is background color
                
                fg_size = adaptive_sizes[density]
                safe_size = int(fg_size * (72.0 / 108.0))
                
                fg_base = Image.new("RGBA", (fg_size, fg_size), bg_color)
                logo_resized = img.resize((safe_size, safe_size), Image.Resampling.LANCZOS)
                
                offset = (fg_size - safe_size) // 2
                # Create a circular mask for the logo to blend smoothly if it's not transparent
                mask = Image.new('L', (safe_size, safe_size), 255)
                # Actually, if it's a solid logo with same bg color, pasting is fine. 
                fg_base.paste(logo_resized, (offset, offset))
                
                fg_base.save(os.path.join(folder, "ic_launcher_foreground.png"), "PNG")
                
            print("Successfully generated all icons.")
    except Exception as e:
        print(f"Error processing image: {e}")

res_dir = r"c:\Users\Naiteek\Downloads\postureapp\didi project\NeckGuardApp\app\src\main\res"
generate_icons(r"c:\Users\Naiteek\Downloads\postureapp\didi project\logo.png", res_dir)

import os
from PIL import Image

logo_path = r"c:\Users\Naiteek\Downloads\postureapp\didi project\logo.png"
if os.path.exists(logo_path):
    with Image.open(logo_path) as img:
        print(f"Size: {img.size}")
        print(f"Mode: {img.mode}")
        print(f"Format: {img.format}")
else:
    print("Logo not found.")

import os
import glob
from PIL import Image

def get_average_color(image_path):
    img = Image.open(image_path)
    img = img.convert('RGB')
    
    # Analyze the top half of the image specifically (since it's a hero backgound)
    width, height = img.size
    img = img.crop((0, 0, width, height // 2))
    
    pixels = img.getdata()
    total = len(pixels)
    
    if total == 0:
        return 0, 0, 0
    
    r = sum(p[0] for p in pixels) // total
    g = sum(p[1] for p in pixels) // total
    b = sum(p[2] for p in pixels) // total
    return r, g, b

def is_color_light(r, g, b):
    # W3C perceived brightness
    brightness = (r * 299 + g * 587 + b * 114) / 1000
    return brightness > 128

def rgb_to_hex(r, g, b):
    return f'#{r:02x}{g:02x}{b:02x}'

def main():
    directory = r'c:\Users\Naiteek\Downloads\postureapp\didi project\gradients'
    files = glob.glob(os.path.join(directory, '*.jpg'))
    
    for file in files:
        r, g, b = get_average_color(file)
        light = is_color_light(r, g, b)
        hex_color = rgb_to_hex(r, g, b)
        
        # Recommended text colors
        title_color = "#2C3E50" if light else "#FFFFFF"
        subtitle_color = "#7F9099" if light else "#D1D5DB" 
        
        filename = os.path.basename(file)
        new_name = 'gradient_' + filename[:5] + '.jpg'
        print(f"File: {new_name} | AvgColor: {hex_color} | IsLight: {light} | Text: {title_color}")

if __name__ == '__main__':
    main()

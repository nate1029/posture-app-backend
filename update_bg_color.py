import re
from PIL import Image

logo_path = r"c:\Users\Naiteek\Downloads\postureapp\didi project\logo.png"
bg_xml_path = r"c:\Users\Naiteek\Downloads\postureapp\didi project\NeckGuardApp\app\src\main\res\drawable\ic_launcher_background.xml"

try:
    with Image.open(logo_path) as img:
        img = img.convert("RGB")
        r, g, b = img.getpixel((0, 0))
        hex_color = f"#{r:02X}{g:02X}{b:02X}"
        print(f"Extracted background color: {hex_color}")
        
    with open(bg_xml_path, 'r') as f:
        xml_content = f.read()
        
    new_xml = re.sub(r'android:fillColor=".*?"', f'android:fillColor="{hex_color}"', xml_content)
    
    with open(bg_xml_path, 'w') as f:
        f.write(new_xml)
        print("Updated ic_launcher_background.xml")
        
except Exception as e:
    print(f"Error: {e}")

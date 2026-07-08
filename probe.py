import sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
path = r'c:\Users\Naiteek\Downloads\postureapp\didi project\NeckGuardApp\app\src\main\java\com\example\neckguard\service\NeckGuardService.kt'
with open(path, 'r', encoding='utf-8-sig') as f:
    txt = f.read()

# Check what posture-related fields exist
import re
for m in re.finditer(r'private var (last\w+|posture\w*|currentState\w*)', txt):
    idx = m.start()
    print(repr(txt[idx:idx+60]))

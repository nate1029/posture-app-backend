import fitz # PyMuPDF
import sys
import re
import os

def extract_messages(pdf_path):
    doc = fitz.open(pdf_path)
    text = ''
    for page in doc:
        text += page.get_text()
    
    bad_messages = []
    good_messages = []
    
    current_category = None
    for line in text.split('\n'):
        line = line.strip()
        if 'BAD POSTURE DETECTED' in line:
            current_category = 'bad'
        elif 'GOOD POSTURE DETECTED' in line:
            current_category = 'good'
        elif re.match(r'^\d+\.', line):
            msg = re.sub(r'^\d+\.\s*', '', line).strip()
            # escape quotes
            msg = msg.replace('\"', '\\\"')
            if current_category == 'bad':
                bad_messages.append(msg)
            elif current_category == 'good':
                good_messages.append(msg)
                
    return bad_messages, good_messages

try:
    v2_bad, v2_good = extract_messages(r'c:\Users\Naiteek\Downloads\postureapp\didi project\NudgeUp_Notifications_V2.PDF')
    flirty_bad, flirty_good = extract_messages(r'c:\Users\Naiteek\Downloads\postureapp\didi project\NudgeUp_Notifications_FlirtyBank.PDF')
    n3_bad, n3_good = extract_messages(r'c:\Users\Naiteek\Downloads\postureapp\didi project\Notifications .pdf')
    
    all_bad = list(set(v2_bad + flirty_bad + n3_bad))
    all_good = list(set(v2_good + flirty_good + n3_good))
    
    bad_list_str = '\n'.join(f'        "{m}",' for m in all_bad)
    good_list_str = '\n'.join(f'        "{m}",' for m in all_good)
    
    kt_code = f"""package com.example.neckguard

object NotificationPool {{
    val badPostureMessages = listOf(
{bad_list_str}
    )
    
    val goodPostureMessages = listOf(
{good_list_str}
    )
}}
"""
    with open(r'c:\Users\Naiteek\Downloads\postureapp\didi project\NeckGuardApp\app\src\main\java\com\example\neckguard\NotificationPool.kt', 'w', encoding='utf-8') as f:
        f.write(kt_code)
        
    print(f'Successfully generated NotificationPool.kt with {len(all_bad)} bad messages and {len(all_good)} good messages.')

except Exception as e:
    print('Error:', e)

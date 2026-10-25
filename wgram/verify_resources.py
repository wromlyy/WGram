#!/usr/bin/env python3
"""Validate WGram resources without requiring Android SDK."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
root = Path(__file__).resolve().parents[1]
res = root / 'TMessagesProj/src/main/res'
strings = ET.parse(res / 'values/strings.xml').getroot()
names = [item.get('name') for item in strings]
assert len(names) == len(set(names)), 'Duplicate default resource names'
available = set(names)
required = set()
for source in (root / 'TMessagesProj/src/main/java/org/telegram').rglob('*.java'):
    required.update(re.findall(r'R\.string\.(WGram\w+)', source.read_text()))
assert required <= available, f'Missing WGram strings: {required - available}'
xml_paths = list(res.rglob('wgram_*.xml'))
xml_paths += list((root / 'TMessagesProj/config').rglob('AndroidManifest*.xml'))
xml_paths += [root / 'TMessagesProj/src/main/AndroidManifest.xml', res / 'values-v31/styles.xml', res / 'values-night/styles.xml']
for path in xml_paths:
    ET.parse(path)
print(f'PASS: {len(required)} WGram strings and {len(xml_paths)} icon/manifest XML files')

#!/usr/bin/env python3
"""Create a small, reproducible BotHost upload archive; never include runtime data or secrets."""
from pathlib import Path
import hashlib
import sys
import zipfile

root = Path(__file__).resolve().parent / 'bot'
output = Path(sys.argv[1]) if len(sys.argv) > 1 else Path('WGramBot.zip')
files = ['bot.py', 'Dockerfile', 'requirements.txt', 'bothost.json', 'README.md', '.gitignore', 'tests/test_service.py', 'tests/test_customs.py']
with zipfile.ZipFile(output, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
    for name in files:
        info = zipfile.ZipInfo(name, date_time=(2026, 10, 26, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        info.external_attr = 0o100644 << 16
        archive.writestr(info, (root / name).read_bytes())
with zipfile.ZipFile(output) as archive:
    assert archive.testzip() is None
    assert set(archive.namelist()) == set(files)
checksum = hashlib.sha256(output.read_bytes()).hexdigest()
output.with_suffix(output.suffix+'.sha256').write_text(checksum+'  '+output.name+'\n')
print(f'{output}: {output.stat().st_size} bytes, SHA256 {checksum}')

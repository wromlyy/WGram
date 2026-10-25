#!/usr/bin/env python3
"""Reuse the exact native library built from this pinned, unchanged source tree."""
import hashlib, subprocess, sys, zipfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]
expected={
 'TMessagesProj/jni':'78ba02f7188a4ad580de9b781827a29fc51b0430',
 '.gitmodules':'88430c54b62eb74c2e5266a8ae2bf8d8453ea450',
 'gradle/wrapper/gradle-wrapper.properties':'2733ed5dc3cd1ac040d18b9f10f8b806ce846704',
}
for path,oid in expected.items():
 actual=subprocess.check_output(['git','rev-parse','HEAD:'+path],cwd=root,text=True).strip()
 if actual!=oid:raise SystemExit('Native source/build metadata changed; use a full native build: '+path)
apk=Path(sys.argv[1])
if hashlib.sha256(apk.read_bytes()).hexdigest()!='18a191ed2dd3dbb0593740cd74ed8d437ce89be5d812911dd32f46e8b57b08f3':
 raise SystemExit('Unexpected source APK checksum')
dest=root/'wgram/native-cache'
with zipfile.ZipFile(apk) as archive:
 for abi in ['arm64-v8a','armeabi-v7a','x86','x86_64']:
  path=dest/abi/'libtmessages.49.so';path.parent.mkdir(parents=True,exist_ok=True)
  path.write_bytes(archive.read('lib/'+abi+'/libtmessages.49.so'))
print('Verified and extracted unchanged native code for all four Android ABIs.')

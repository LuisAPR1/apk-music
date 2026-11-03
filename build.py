#!/usr/bin/env python3
"""Build the local APK using Android SDK 36 and JDK 17+ (or ECJ)."""
import argparse
from datetime import datetime, timezone
import hashlib
from pathlib import Path
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile


def run(*args):
    subprocess.run([str(a) for a in args], check=True)


def build():
    root = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk-root', type=Path, required=True)
    parser.add_argument('--build-tools', default='36.0.0')
    parser.add_argument('--ecj', type=Path, help='Optional ECJ compiler JAR when javac is unavailable')
    args = parser.parse_args()
    android = args.sdk_root / 'platforms/android-36/android.jar'
    bt = args.sdk_root / 'build-tools' / args.build_tools
    if not android.is_file() or not (bt / 'aapt2').is_file():
        parser.error('Android SDK Platform 36 and the selected Build Tools are required')
    config = (root / 'app/build.gradle').read_text()
    version_code = re.search(r'versionCode\s+(\d+)', config).group(1)
    version_name = re.search(r"versionName\s+'([^']+)'", config).group(1)
    # Never replace the existing signing identity automatically.
    key = root / 'oledmusic-local.jks'
    if not key.is_file():
        parser.error('The original oledmusic-local.jks signing key is required')
    work = root / 'build' / datetime.now(timezone.utc).strftime('work-%Y%m%d-%H%M%S-%f')
    classes, generated, dex = [work / n for n in ('classes', 'generated', 'dex')]
    for path in (classes, generated, dex):
        path.mkdir(parents=True)
    ET.register_namespace('android', 'http://schemas.android.com/apk/res/android')
    manifest = ET.parse(root / 'app/src/main/AndroidManifest.xml')
    manifest.getroot().set('package', 'pt.luis.oledmusic')
    manifest_path = work / 'AndroidManifest.xml'
    manifest.write(manifest_path, encoding='utf-8', xml_declaration=True)
    run(bt / 'aapt2', 'compile', '--dir', root / 'app/src/main/res', '-o', work / 'resources.zip')
    run(bt / 'aapt2', 'link', '-I', android, '--manifest', manifest_path,
        '--min-sdk-version', '26', '--target-sdk-version', '36',
        '--version-code', version_code, '--version-name', version_name,
        '--java', generated, '-o', work / 'unsigned.apk', work / 'resources.zip')
    sources = sorted((root / 'app/src/main/java').rglob('*.java')) + sorted(generated.rglob('*.java'))
    if args.ecj:
        run('java', '-jar', args.ecj, '-8', '-proc:none', '-nowarn', '-encoding', 'UTF-8',
            '-classpath', android, '-d', classes, *sources)
    else:
        javac = shutil.which('javac')
        if not javac:
            parser.error('Install a JDK with javac, or pass --ecj /path/to/ecj.jar')
        run(javac, '--release', '8', '-encoding', 'UTF-8', '-classpath', android, '-d', classes, *sources)
    with zipfile.ZipFile(work / 'classes.jar', 'w', zipfile.ZIP_DEFLATED) as jar:
        for path in sorted(classes.rglob('*.class')):
            jar.write(path, path.relative_to(classes))
    run('java', '-cp', bt / 'lib/d8.jar', 'com.android.tools.r8.D8', '--min-api', '26',
        '--lib', android, '--output', dex, work / 'classes.jar')
    with zipfile.ZipFile(work / 'unsigned.apk', 'a', zipfile.ZIP_DEFLATED) as apk:
        for path in sorted(dex.glob('*.dex')):
            apk.write(path, path.name)
    run(bt / 'zipalign', '-f', '4', work / 'unsigned.apk', work / 'aligned.apk')
    output = root / 'build' / f'OLED-Music-v{version_name}.apk'
    run('java', '-jar', bt / 'lib/apksigner.jar', 'sign', '--ks', key,
        '--ks-key-alias', 'oledmusic', '--ks-pass', 'pass:android', '--key-pass', 'pass:android',
        '--out', output, work / 'aligned.apk')
    run('java', '-jar', bt / 'lib/apksigner.jar', 'verify', '--verbose', '--print-certs', output)
    run(bt / 'zipalign', '-c', '4', output)
    print('SHA256:', hashlib.sha256(output.read_bytes()).hexdigest())
    print('APK:', output)


if __name__ == '__main__':
    build()

#!/usr/bin/env python3
"""Run the dependency-free regression suite without an Android device."""
import argparse
from pathlib import Path
import shutil
import subprocess
import tempfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--ecj', type=Path, help='ECJ JAR if javac is unavailable')
args = parser.parse_args()
root = Path(__file__).resolve().parent.parent
suites = ['PlayerSelection', 'AlbumBorderPalette', 'LyricTimeline', 'PlaybackClock', 'YouTubeVideoId', 'PlayerExtrasLayout']
sources = []
for name in suites:
    sources += [root / f'app/src/main/java/pt/luis/oledmusic/{name}.java', root / f'tests/{name}Test.java']
with tempfile.TemporaryDirectory(prefix='oledmusic-test-') as output:
    compiler = ['java', '-jar', str(args.ecj), '-8', '-proc:none'] if args.ecj else [shutil.which('javac') or 'javac', '--release', '8']
    subprocess.run(compiler + ['-encoding', 'UTF-8', '-d', output] + [str(p) for p in sources], check=True)
    for name in suites:
        subprocess.run(['java', '-cp', output, f'pt.luis.oledmusic.{name}Test'], check=True)

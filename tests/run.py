#!/usr/bin/env python3
"""Run player selection and album border colour regressions on the JVM."""
import argparse
from pathlib import Path
import shutil
import subprocess
import tempfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--ecj', type=Path, help='ECJ JAR if javac is unavailable')
args = parser.parse_args()
root = Path(__file__).resolve().parent.parent
sources = [root / 'app/src/main/java/pt/luis/oledmusic/PlayerSelection.java',
           root / 'tests/PlayerSelectionTest.java',
           root / 'app/src/main/java/pt/luis/oledmusic/AlbumBorderPalette.java',
           root / 'tests/AlbumBorderPaletteTest.java']
with tempfile.TemporaryDirectory(prefix='oledmusic-test-') as output:
    compiler = ['java', '-jar', str(args.ecj), '-8', '-proc:none'] if args.ecj else [shutil.which('javac') or 'javac', '--release', '8']
    subprocess.run(compiler + ['-encoding', 'UTF-8', '-d', output] + [str(p) for p in sources], check=True)
    subprocess.run(['java', '-cp', output, 'pt.luis.oledmusic.PlayerSelectionTest'], check=True)
    subprocess.run(['java', '-cp', output, 'pt.luis.oledmusic.AlbumBorderPaletteTest'], check=True)

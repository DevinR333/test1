# -*- coding: utf-8 -*-
"""Inline every source file into one standalone HTML that runs by
double-clicking it. No server, no folders, no dependencies."""
import os, re

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
SRC = os.path.join(ROOT, 'src')

html = open(os.path.join(ROOT, 'index.html')).read()
css = open(os.path.join(SRC, 'style.css')).read()

# scripts, in the order index.html loads them
order = re.findall(r'<script src="src/([^"]+)"></script>', html)
js = []
for name in order:
    js.append('/* ===== %s ===== */\n%s' % (name, open(os.path.join(SRC, name)).read()))

out = html
out = out.replace('<link rel="stylesheet" href="src/style.css">',
                  '<style>\n%s\n</style>' % css.strip())
# drop the individual script tags, then append one combined block
for name in order:
    out = out.replace('  <script src="src/%s"></script>\n' % name, '')
out = out.replace('</body>', '  <script>\n%s\n  </script>\n</body>' % '\n'.join(js))

dest = os.path.join(ROOT, 'buddy-blade.html')
open(dest, 'w').write(out)
print('wrote %s (%.0f KB, %d sources inlined)'
      % (os.path.relpath(dest, ROOT), os.path.getsize(dest) / 1024.0, len(order)))

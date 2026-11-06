#!/usr/bin/env python3
"""
Make (or refresh) a Sphinx template folder for semantic-doc's html preview:

    sdoc-template/
      manifest.json    the source files the previewer loads into Pyodide, and the pinned packages
      source/conf.py   written by hand; a new template gets a copy of the demo's
      source/static/theme-roles.css   copied from editor/styles (the editor's role styles)
      _static/         copied from a native build: the files the preview page's <base> points at

Usage (in semantic-doc/src):
    python3 make-template.py                        web-dist/sdoc-template, the demo's
    python3 make-template.py <template folder>      e.g. jserv-album's sdoc-template-0.8

Needs the pinned packages installed natively (PACKAGES), as the browser loads the same versions:
    pip install "sphinx==9.2.0" "sphinx_rtd_theme==3.1.0" "docutils==0.22.4"
"""
import json
import os
import shutil
import subprocess
import sys
import tempfile

# The versions the preview installs with micropip (Pyodide 0.29), and the synode builds with.
PACKAGES = ['sphinx==9.2.0', 'sphinx_rtd_theme==3.1.0', 'docutils==0.22.4']
PYODIDE = 'https://cdn.jsdelivr.net/pyodide/v0.29.0/full/pyodide.js'

here = os.path.dirname(os.path.abspath(__file__))


def unused(folder, names):
    """
    Files the preview never loads: the theme's legacy _static/fonts/ (theme.css uses css/fonts/),
    and the old font formats (.eot, .ttf, .svg) beside woff / woff2.
    """
    if os.path.basename(folder) == '_static':
        return {'fonts'}
    return {n for n in names if os.path.splitext(n)[1] in ('.eot', '.ttf', '.svg')}


def main():
    tpl = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else os.path.join(here, 'web-dist', 'sdoc-template'))
    src = os.path.join(tpl, 'source')
    demo_conf = os.path.join(here, 'web-dist', 'sdoc-template', 'source', 'conf.py')
    if not os.path.isfile(os.path.join(src, 'conf.py')):
        os.makedirs(src, exist_ok=True)
        shutil.copy2(demo_conf, src)
        print(f'{src}/conf.py: copied from the demo template')

    os.makedirs(os.path.join(src, 'static'), exist_ok=True)
    shutil.copy2(os.path.join(here, 'editor', 'styles', 'theme-roles.css'), os.path.join(src, 'static'))

    files = sorted(os.path.relpath(os.path.join(d, f), src).replace(os.sep, '/')
                   for d, dirs, names in os.walk(src)
                   for f in names
                   if not f.endswith('.rst') and '__pycache__' not in d)
    with open(os.path.join(tpl, 'manifest.json'), 'w', encoding='utf-8') as f:
        json.dump({'comment': 'Written by semantic-doc/src/make-template.py; paths are relative to source/.',
                   'source': 'source/', 'files': files, 'packages': PACKAGES, 'pyodide': PYODIDE},
                  f, indent=2)
        f.write('\n')

    # a native build of an empty doc, for the theme's _static/
    with tempfile.TemporaryDirectory() as tmp:
        work = os.path.join(tmp, 'source')
        shutil.copytree(src, work)
        with open(os.path.join(work, 'index.rst'), 'w', encoding='utf-8') as f:
            f.write('Template\n========\n')
        out = os.path.join(tmp, 'html')
        subprocess.run([sys.executable, '-m', 'sphinx', '-b', 'html', '-q', work, out], check=True)
        shutil.rmtree(os.path.join(tpl, '_static'), ignore_errors=True)
        shutil.copytree(os.path.join(out, '_static'), os.path.join(tpl, '_static'), ignore=unused)

    print(f'{tpl}: {len(files)} source files, _static from sphinx {PACKAGES[0]}')


if __name__ == '__main__':
    main()

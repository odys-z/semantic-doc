#!/usr/bin/env python3
"""
Compare the @anclient/* packages semantic-doc builds with against their sources
(Anclient/js/<package>), and open the differences in VS Code.
Adapted from Anclient/examples/example.js/diff-anclient.py.

semantic-doc (this folder, src/) can get @anclient/* from:
    node_modules/@anclient/* -> ../../anclient/js/*   the "file:" devDependencies: the sources themselves
    node_modules/@anclient/*                          an npm install of a published version
    a webpack resolve.alias                           if webpack.config.js ever aliases them

The location is found the way the build finds it: the resolve.alias of webpack.config.js
first, then the node_modules of this folder and its parents. A copy of another release line
than the sources (e.g. anreact 0.7.x, while the doc tree needs 0.8) is marked, and can be
compared, but s / u won't copy files between them.

Usage (in semantic-doc/src):
    python3 diff-anclient.py                         compare node_modules/@anclient/* with the sources
    python3 diff-anclient.py -p anreact
    python3 diff-anclient.py --src ~/github/anclient/js
    python3 diff-anclient.py -h

The sources default to ../../anclient/js (or ../../Anclient/js), the checkout the
"file:" devDependencies in package.json point at.

Then it lists the files that differ, and asks:
    3         open file 3 in VS Code's diff editor
    1 4 7     several
    a         all modified files
    s 3 5     copy files 3 and 5 from the source into the project's copy (source only, modified)
    u 4       copy file 4 from the project's copy into the source (used only, modified)
    s a, u a  all the files that can be copied that way
    x         swap the sides of the diff editor (which file is on the right)
    f         open the whole package pair in VS Code, with the Compare Folders extension
    r         rescan
    q         quit

Editing in both directions, in VS Code:
  - diff editor (3, a): the right side is the one to edit; the arrows in the gutter between
    the sides copy a change from the left to the right. To copy the other way, x swaps the sides.
    By default the copy used by the project is on the left, the Anclient source on the right.
  - folder view (f): extension moshfeu.compare-folders (installed when asked), listing the
    files that differ or exist on one side only. "Take My File / Take Compared File" and
    "Copy to My / Compared Folder" copy whole files in both directions; clicking a file
    opens it in the diff editor. "My folder" is the project's copy, "compared" the source.
    Exclude noise with the user setting
        "compareFolders.excludeFilter": ["**/node_modules", "**/dist", "**/.git"]

s and u list what they will copy, and ask before writing; overwritten files are marked.
A copy installed from npm is overwritten by the next npm install: take what is worth keeping
into the source (and publish it). A copy that is a symlink to the source (npm link, "file:"
dependencies, e.g. registry.ts) is the source itself; then the shadowed copy, if any, e.g.
an older install, is compared instead.

Files only in one side are opened with `code <file>`. Without VS Code's `code` command
on PATH, the unified diff is printed instead. Line endings are ignored.
"""

import argparse
import difflib
import json
import os
import shutil
import subprocess
import sys

here = os.path.dirname(os.path.abspath(__file__))
packages = ['anreact', 'semantier']

skip_dirs = {'node_modules', '.git', 'dist', 'ts', 'test', 'tests', '.vscode', '__pycache__'}
skip_files = {'package-lock.json', '.DS_Store'}


# ---------------------------------------------------------------- locations

def pkg_root(path: str, pkg: str):
    """The folder with package.json of @anclient/<pkg>, at or above path (an alias can name a file)."""
    folder = path if os.path.isdir(path) else os.path.dirname(path)
    while True:
        try:
            with open(os.path.join(folder, 'package.json'), encoding='utf-8') as f:
                if json.load(f).get('name') == f'@anclient/{pkg}':
                    return folder
        except (OSError, ValueError):
            pass
        parent = os.path.dirname(folder)
        if parent == folder:
            return path
        folder = parent


def webpack_aliases(project: str) -> dict:
    """
    resolve.alias of the project's webpack.config.js, by loading it with node.
    :return: {alias: absolute path}; {'?': message} if the config can't be loaded
    """
    cfg = os.path.join(project, 'webpack.config.js')
    if not os.path.isfile(cfg):
        return {}
    with open(cfg, encoding='utf-8') as f:
        if '@anclient/' not in f.read():
            return {}
    node = shutil.which('node')
    if not node:
        return {'?': 'node is not found to read webpack.config.js'}
    # the config may be an object, an array, or a function (env, argv) as semantic-doc's
    js = ("let c = require(process.argv[1]); if (typeof c === 'function') c = c({}, {mode: 'development'});"
          "const r = (Array.isArray(c) ? c[0] : c).resolve || {};"
          "console.log(JSON.stringify(r.alias || {}))")
    try:
        out = subprocess.run([node, '-e', js, cfg], cwd=project, capture_output=True, text=True, timeout=20)
        if out.returncode == 0:
            alias = json.loads(out.stdout.strip().splitlines()[-1])
            return {k.rstrip('$'): v for k, v in alias.items() if isinstance(v, str)}
        return {'?': out.stderr.strip().splitlines()[-1] if out.stderr.strip() else 'failed'}
    except (OSError, ValueError, IndexError, subprocess.SubprocessError) as e:
        return {'?': str(e)}


def node_chain(pkg: str, project: str) -> list:
    """node_modules/@anclient/<pkg> in the project folder and its parents, nearest first."""
    found, folder = [], os.path.abspath(project)
    while True:
        p = os.path.join(folder, 'node_modules', '@anclient', pkg)
        if os.path.isdir(p):
            found.append(p)
        parent = os.path.dirname(folder)
        if parent == folder:
            return found
        folder = parent


class Location:
    def __init__(self, pkg: str, path: str, how: str):
        self.pkg, self.path, self.how = pkg, path, how   # how: alias | node_modules
        self.real = os.path.realpath(path)

    def is_source(self, source: str) -> bool:
        return os.path.realpath(source) == self.real

    def label(self, source: str) -> str:
        rel = os.path.relpath(self.path)
        if self.how == 'alias':
            return f'{rel}  (webpack alias){"  = source" if self.is_source(source) else ""}'
        link = f'  -> {os.path.relpath(self.real)}' if self.real != os.path.abspath(self.path) else ''
        return f'{rel}{link}{"  = source" if self.is_source(source) else ""}'


def locate(pkg: str, project: str):
    """
    :return: (the location used by the build, the shadowed ones), or (None, [], message)
    """
    alias = webpack_aliases(project)
    chain = [Location(pkg, p, 'node_modules') for p in node_chain(pkg, project)]
    if f'@anclient/{pkg}' in alias:
        return Location(pkg, pkg_root(alias[f'@anclient/{pkg}'], pkg), 'alias'), chain, None
    note = f'webpack.config.js not read: {alias["?"]}' if '?' in alias else None
    return (chain[0] if chain else None), chain[1:], note


def release_line(v: str) -> str:
    """
    The release line of a version: major.minor for 0.x (npm's ^0.7.2 never reaches 0.8),
    otherwise the major, e.g. 0.7.2 -> 0.7, 1.0.6 -> 1.
    """
    parts = v.split('.')
    if not parts or not parts[0].isdigit():
        return v
    return '.'.join(parts[:2]) if parts[0] == '0' else parts[0]


def line_note(pkg_dir: str, source: str) -> str:
    """:return: '' for the release line of the source, otherwise a note, e.g. '(0.7.x, source 0.8.x)'"""
    a, b = release_line(version(pkg_dir)), release_line(version(source))
    return '' if a == b else f'({a}.x, source {b}.x)'


def version(pkg_dir: str) -> str:
    try:
        with open(os.path.join(pkg_dir, 'package.json'), encoding='utf-8') as f:
            return json.load(f).get('version', '?')
    except (OSError, ValueError):
        return '?'


# ---------------------------------------------------------------- compare

def files_under(root: str):
    out = set()
    for d, dirs, files in os.walk(root):
        dirs[:] = [x for x in dirs if x not in skip_dirs]
        for f in files:
            if f not in skip_files:
                out.add(os.path.relpath(os.path.join(d, f), root))
    return out


def same(a: str, b: str) -> bool:
    with open(a, 'rb') as fa, open(b, 'rb') as fb:
        return fa.read().replace(b'\r\n', b'\n') == fb.read().replace(b'\r\n', b'\n')


def compare(installed: str, source: str):
    """
    :return: list of (state, rel, installed path, source path); state: M, I (installed only), S (source only)
    """
    inst = files_under(installed)
    # only the source files in the folders / top-level files the published package has,
    # e.g. src/** for anreact, *.ts for semantier
    tops = {r.split(os.sep)[0] for r in inst if os.sep in r}
    root_exts = {os.path.splitext(r)[1] for r in inst if os.sep not in r} - {'.json', '.md', ''}
    src = {r for r in files_under(source)
           if (os.sep in r and r.split(os.sep)[0] in tops)
           or (os.sep not in r and (r in inst or os.path.splitext(r)[1] in root_exts
                                    and not r.endswith('.d.ts')))}

    rows = []
    for r in sorted(inst | src):
        a, b = os.path.join(installed, r), os.path.join(source, r)
        if r in inst and r in src:
            if not same(a, b):
                rows.append(('M', r, a, b))
        elif r in inst:
            rows.append(('I', r, a, b))
        else:
            rows.append(('S', r, a, b))
    return rows


# packages of the last scan whose copy is of another release line than the source
other_lines = {}


def scan(project: str, pkgs, src_root, pairs=None):
    rows = []
    other_lines.clear()
    print(f'\nProject {os.path.relpath(project)}')
    for pkg in pkgs:
        source = os.path.join(src_root, pkg)
        used, shadowed, note = locate(pkg, project)
        if not os.path.isdir(source):
            print(f'@anclient/{pkg}: source {source} not found, use --src <Anclient/js>.')
            continue
        if used is None:
            print(f'@anclient/{pkg}: not found by webpack alias or node_modules'
                  f'{" (" + note + ")" if note else ""}.')
            continue
        left = used
        if used.is_source(source):
            # built from the sources: nothing to compare, unless an installed copy is shadowed
            others = [s for s in shadowed if not s.is_source(source)]
            print(f'\n@anclient/{pkg}  built from the source: {used.label(source)}')
            if not others:
                continue
            left = others[0]
            print(f'    comparing the shadowed copy instead: {left.label(source)}')

        if pairs is not None:
            pairs.append((left.real, os.path.realpath(source)))
        r = compare(left.path, source)
        side = 'used  ' if left is used else 'copy  '
        print(f'\n@anclient/{pkg}  {side} {version(left.path):8} {left.label(source)}\n'
              f'{" " * (len(pkg) + 11)}source {version(source):8} {os.path.relpath(source)}')
        note = line_note(left.path, source)
        if note:
            other_lines[pkg] = note
            print(f'    another release line {note}: differences are expected; s / u are disabled.\n'
                  f'    Compare with a checkout of {release_line(version(left.path))}.x, see --src (-h).')
        if not r:
            print('    identical')
        rows += [(pkg,) + x for x in r]

    labels = {'M': 'modified', 'I': 'used only', 'S': 'source only'}
    if rows:
        print()
    for i, (pkg, st, rel, _, _) in enumerate(rows, 1):
        print(f'{i:4}  {labels[st]:11}  {pkg}/{rel}')
    return rows


compare_folders_ext = 'moshfeu.compare-folders'


def has_extension(code: str, ext: str) -> bool:
    try:
        out = subprocess.run([code, '--list-extensions'], capture_output=True, text=True, timeout=60)
        return ext.lower() in out.stdout.lower().split()
    except (OSError, subprocess.SubprocessError):
        return False


def open_folders(code, pairs):
    """
    Open each (copy, source) package pair with the Compare Folders extension, in a new window.
    """
    if not code:
        print("VS Code's `code` command is not on PATH.")
        return
    if not has_extension(code, compare_folders_ext):
        if ask(f'VS Code extension {compare_folders_ext} (Compare Folders) is not installed. Install it? [y/N] ') != 'y':
            return
        subprocess.run([code, '--install-extension', compare_folders_ext])
    for copy, source in pairs:
        print(f'Compare Folders: my folder {os.path.relpath(copy)}  <->  compared {os.path.relpath(source)}')
        subprocess.Popen([code, '--new-window', copy, source], env=dict(os.environ, COMPARE_FOLDERS='DIFF'))


def open_rows(picks, code, swap=False):
    for pkg, st, rel, a, b in picks:
        if code:
            left, right = (b, a) if swap else (a, b)
            subprocess.Popen([code, '--reuse-window', '--diff', left, right] if st == 'M'
                             else [code, '--reuse-window', a if st == 'I' else b])
        elif st == 'M':
            with open(a, encoding='utf-8', errors='replace') as fa, \
                 open(b, encoding='utf-8', errors='replace') as fb:
                sys.stdout.writelines(difflib.unified_diff(
                    fa.read().splitlines(True), fb.read().splitlines(True),
                    f'used/{pkg}/{rel}', f'source/{pkg}/{rel}'))
        else:
            print(f'{"used" if st == "I" else "source"} only: {a if st == "I" else b}')


def copy_rows(rows, picks, to_source: bool) -> bool:
    """
    Copy the picked files: the project's copy -> source (to_source), or source -> the project's copy.
    Only the rows that can go that way: used only / modified, or source only / modified.
    :return: whether anything was copied
    """
    can = ('I', 'M') if to_source else ('S', 'M')
    todo = [r for r in picks if r[1] in can and r[0] not in other_lines]
    for r in picks:
        if r[0] in other_lines:
            print(f'  skip {rows.index(r) + 1}: {r[0]}/{r[2]}, another release line {other_lines[r[0]]}.')
        elif r not in todo:
            print(f'  skip {rows.index(r) + 1}: {r[0]}/{r[2]} is {"source" if r[1] == "S" else "used"} only.')
    if not todo:
        return False

    where = 'to the source' if to_source else "into the project's copy"
    print(f'\nCopy {where}:')
    for pkg, st, rel, a, b in todo:
        src, dst = (a, b) if to_source else (b, a)
        print(f'  {rows.index((pkg, st, rel, a, b)) + 1:3}  {os.path.relpath(src)}\n'
              f'       -> {os.path.relpath(dst)}{"   (overwrite)" if st == "M" else "   (new)"}')
    if ask(f'Copy {len(todo)} file(s)? [y/N] ') != 'y':
        return False

    for pkg, st, rel, a, b in todo:
        src, dst = (a, b) if to_source else (b, a)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copy2(src, dst)
    print(f'Copied {len(todo)} file(s).')
    return True


def ask(prompt: str) -> str:
    try:
        return input(prompt).strip().lower()
    except (EOFError, KeyboardInterrupt):
        print()
        return 'q'


def default_src() -> str:
    """../../anclient/js beside semantic-doc, as the "file:" devDependencies point at"""
    for name in ('anclient', 'Anclient'):
        p = os.path.normpath(os.path.join(here, '..', '..', name, 'js'))
        if os.path.isdir(p):
            return p
    return os.path.normpath(os.path.join(here, '..', '..', 'anclient', 'js'))


def main():
    ap = argparse.ArgumentParser(
        description="Diff semantic-doc's @anclient/* against the Anclient/js sources, in VS Code.")
    ap.add_argument('-p', '--packages', nargs='+', default=packages, choices=packages,
                    help='packages under @anclient, default: anreact semantier')
    ap.add_argument('--src', help='folder of the package sources, default: ../../anclient/js')
    args = ap.parse_args()

    src_root = os.path.abspath(os.path.expanduser(args.src)) if args.src else default_src()
    if not os.path.isdir(src_root):
        sys.exit(f'Cannot find {src_root}, use --src <path to Anclient/js>.')
    print(f'Sources: {src_root}  ' +
          '  '.join(f'{p} {version(os.path.join(src_root, p))}' for p in packages))
    project = here

    code = shutil.which('code')
    if not code:
        print("VS Code's `code` command is not on PATH, diffs are printed instead.\n"
              "(VS Code: Command Palette > Shell Command: Install 'code' command in PATH)")

    pairs = []
    rows = scan(project, args.packages, src_root, pairs)
    swap = False
    while rows:
        sides = 'left: source, right: copy' if swap else 'left: copy, right: source'
        cmd = ask(f'\nNumbers to open ({sides}), a = all modified, x = swap sides, f = folders,\n'
                  f's <numbers|a> = source -> copy, u <numbers|a> = copy -> source, r = rescan, q = quit: ')
        if cmd in ('q', ''):
            break
        if cmd == 'r':
            pairs = []
            rows = scan(project, args.packages, src_root, pairs)
            continue
        if cmd == 'x':
            swap = not swap
            continue
        if cmd == 'f':
            open_folders(code, pairs)
            continue
        if cmd[:1] in ('s', 'u') and (len(cmd) == 1 or cmd[1] == ' '):
            to_source, arg = cmd[0] == 'u', cmd[1:].strip()
            try:
                picks = rows if arg == 'a' else [rows[int(n) - 1] for n in arg.replace(',', ' ').split()]
            except (ValueError, IndexError):
                picks = None
            if not picks:
                print(f'Type e.g. "{cmd[0]} 1 3" or "{cmd[0]} a".')
                continue
            if copy_rows(rows, picks, to_source):
                pairs = []
                rows = scan(project, args.packages, src_root, pairs)
                if not rows:
                    print('\nNo differences left.')
            continue
        if cmd == 'a':
            picks = [r for r in rows if r[1] == 'M']
        else:
            try:
                picks = [rows[int(n) - 1] for n in cmd.replace(',', ' ').split()]
            except (ValueError, IndexError):
                print(f'Type 1 - {len(rows)}, a, x, f, s <numbers>, u <numbers>, r or q.')
                continue
        open_rows(picks, code, swap)


if __name__ == '__main__':
    main()

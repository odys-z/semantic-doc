'''
Build and package the demo server SmandocServ (io.oz.semandoc.SmandocServ), copied from jserv-sample's tasks.py,
for the semantic-doc demo (src/demo).

The package is laid out like a synode's (jserv-album), with the provided db files (no installation):

    <package>/
        bin/semantic-doc.jserv-<version>.jar  shaded, main class io.oz.semandoc.SmandocServ
        WEB-INF/config.xml, connects.xml, settings.json, ...
        volume/system.sqlite, ...           connects.xml: $VOLUME_HOME = ../volume, relative to WEB-INF

Usage, in semantic-doc/jserv (--target: the package folder, default tasks.json/package_dir):
    invoke build                                  mvn clean package -DskipTests, installing ../doctree.jserv
                                                  first if it is not installed
    invoke package [--target] [--require RANGE]   check the pom version in RANGE, e.g. "[1.5.9,)",
                                                  build if needed, then copy into the package folder
    invoke clean

A failed task exits with code 1. Starting and stopping the app is the clients' (tests') job, in the package folder:
    start: java -jar bin/semantic-doc.jserv-<version>.jar [settings.json]
    stop:  post to JsampleCtrl (ctrl.less, port "ctrl") from localhost
'''
import glob
import re
import shutil
import subprocess
from pathlib import Path
from typing import Optional, Union, cast

from invoke import task, Context
from invoke.exceptions import Exit

from anson.io.odysz.anson import Anson
from anson.io.odysz.common import requir_pkg, mvn, Utils

requir_pkg('anson.py3', '0.6.9')        # mvn helpers
requir_pkg('semantics.py3', '0.6.17')   # SynodeTask, the same as jserv-album's tasks.json

from semanticshare.io.oz.invoke import SynodeTask

here = Path(__file__).parent.resolve()


def load_cfg(deploy: str = 'tasks.json') -> SynodeTask:
    '''
    tasks.json, a SynodeTask like jserv-album's, using web_inf_dir, vol_resource and package_dir.
    '''
    Anson.java_src('semanticshare')
    return cast(SynodeTask, Anson.from_file(here / deploy))


def pom_version() -> str:
    pom = (here / 'pom.xml').read_text(encoding='utf-8')
    m = re.search(r'<artifactId>semantic-doc\.jserv</artifactId>\s*<version>([^<]+)</version>', pom)
    if m is None:
        raise ValueError(f'Cannot find semantic-doc.jserv\'s version in {here / "pom.xml"}.')
    return m.group(1).strip()


def doctree_version() -> str:
    '''
    The doc-tree tier's version required by the pom, property doctree.jserv.version.
    '''
    pom = (here / 'pom.xml').read_text(encoding='utf-8')
    m = re.search(r'<doctree\.jserv\.version>([^<]+)</doctree\.jserv\.version>', pom)
    if m is None:
        raise ValueError(f'Cannot find doctree.jserv.version in {here / "pom.xml"}.')
    return m.group(1).strip()


def app_jar() -> Path:
    return here / 'target' / f'semantic-doc.jserv-{pom_version()}.jar'


def build_jar(force: bool = False) -> Path:
    '''
    mvn clean package -DskipTests (no gpg signing, which is at verify), if the jar is not built or force.
    '''
    jar = app_jar()
    if force or not jar.is_file():
        # AnSession.init(conn), used by SmandocServ, is since semantic.jserv 1.5.18.
        if mvn.requir_installed('io.github.odys-z:semantic.jserv', '[1.5.18,)', tolerate=True) is None:
            raise RuntimeError('semantic.jserv [1.5.18,) is not installed. Install it first, e.g. '
                               'semantic-jserv/semantic.jserv$ mvn install, or jserv-album$ invoke install-maven-local.')
        exe = shutil.which('mvn')  # mvn.cmd on Windows
        if exe is None:
            raise RuntimeError('Cannot find mvn.')

        # the doc-tree tier core, semantic-doc/doctree.jserv
        dtv = doctree_version()
        if mvn.requir_installed('io.github.odys-z:doctree.jserv', f'[{dtv},)', tolerate=True) is None:
            doctree = here.parent / 'doctree.jserv'
            print(f'Installing {doctree}: mvn clean install -DskipTests')
            r = subprocess.run([exe, '-B', '-q', 'clean', 'install', '-DskipTests'], cwd=doctree)
            if r.returncode != 0:
                raise RuntimeError(f'Failed to install doctree.jserv {dtv} (mvn exit code {r.returncode}).')

        print(f'Building {here}: mvn clean package -DskipTests')
        r = subprocess.run([exe, '-B', '-q', 'clean', 'package', '-DskipTests'], cwd=here)
        if r.returncode != 0 or not jar.is_file():
            raise RuntimeError(f'Failed to build {jar} (mvn exit code {r.returncode}).')
    return jar


def package_app(target: Union[str, Path, None] = None, deploy: str = 'tasks.json', rebuild: bool = False) -> Path:
    '''
    Build the jar if needed, then copy it, WEB-INF and the provided db files into target.
    :param target: default tasks.json/package_dir
    :return: the package folder
    '''
    cfg = load_cfg(deploy)
    jar = build_jar(rebuild)

    pkg = Path(target if target is not None else here / cfg.package_dir).resolve()
    if pkg.exists():
        shutil.rmtree(pkg)

    (pkg / 'bin').mkdir(parents=True)
    shutil.copy2(jar, pkg / 'bin' / jar.name)
    shutil.copytree(here / cfg.web_inf_dir, pkg / 'WEB-INF')

    # vol_resource: {package folder: source glob}, e.g. {"volume": "src/test/res/volume/*.sqlite"}
    for dst, src in cfg.vol_resource.items():
        (pkg / dst).mkdir(parents=True, exist_ok=True)
        files = glob.glob(str(here / src))
        if len(files) == 0:
            raise RuntimeError(f'No files found for vol_resource {dst}: {src}')
        for f in files:
            shutil.copy2(f, pkg / dst / Path(f).name)

    print('SmandocServ package:', pkg)
    return pkg


def package_dir(target: Optional[str], deploy: str) -> Path:
    return Path(target).resolve() if target else here / load_cfg(deploy).package_dir


@task
def build(c: Context, deploy: str = 'tasks.json'):
    try: build_jar(force=True)
    except Exception as e: raise Exit(f'[build] {e}', code=1)


@task(help={'target': 'package folder, default tasks.json/package_dir',
            'require': 'maven version range of semantic-doc.jserv (the pom), e.g. "[1.5.9,)"'})
def package(c: Context, deploy: str = 'tasks.json', target: str = None, require: str = None):
    ver = pom_version()
    if require and not mvn.in_range(ver, mvn.parse_range(require)):
        raise Exit(f'[package] semantic-doc.jserv {ver} is not in the required range {require}.', code=1)
    try: package_app(package_dir(target, deploy), deploy)
    except Exception as e: raise Exit(f'[package] {e}', code=1)


@task
def clean(c: Context, deploy: str = 'tasks.json'):
    Utils.rm_any([here / 'target', here / load_cfg(deploy).package_dir])

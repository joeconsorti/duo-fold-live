"""Package a clean committed source tree alongside its local test APK."""
import hashlib,json,subprocess,zipfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]
def git(*args):return subprocess.check_output(['git',*args],cwd=root)
def require_clean():
    if git('status','--porcelain').strip():raise SystemExit('Commit all source changes before a delivery build; source backup must match a clean commit.')
def package(apk):
    require_clean()
    commit=git('rev-parse','HEAD').decode().strip()
    out=apk.parent
    source=out/'Duo-Fold-Live-Reconstructed-Source.zip'
    bundle=out/'Duo-Fold-Live-Reconstructed.git.bundle'
    subprocess.run(['git','bundle','create',str(bundle),'HEAD'],cwd=root,check=True)
    subprocess.run(['git','archive','--format=zip','--prefix=source/','-o',str(source),'HEAD'],cwd=root,check=True)
    with zipfile.ZipFile(source,'a',compression=zipfile.ZIP_DEFLATED) as z:z.write(bundle,'history.git.bundle')
    files={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in (apk,source)}
    manifest={'source_commit':commit,'files_sha256':files,'reconstruction_target':'3.5.9-alpha.20-local','handset_verified':False}
    (out/'BUILD-MANIFEST.json').write_text(json.dumps(manifest,indent=2)+'\n')
    (out/'SHA256SUMS.txt').write_text(''.join(h+'  '+n+'\n' for n,h in files.items()))
    print('Source backup:',source,'commit:',commit)

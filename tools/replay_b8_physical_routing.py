"""Compare unchanged historical production scenes against both routing orders.

Usage: python3 tools/replay_b8_physical_routing.py [--export DIRECTORY] [--candidate REV]
The exported kit runs standalone with: python3 replay_b8_physical_routing.py
Requires Java 17 (javac/java). No Android device is used or changed.
"""
import argparse
from pathlib import Path
import shutil
import subprocess
import tempfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
VERSIONS = {"alpha51": "95b41538df3a646605102badfa7dc1a308f50a68",
            "alpha79": "460be60", "alpha80": "c755bb2", "alpha82": "49fbd43486adb39680e34a7056942467a0d5bb38"}
SOURCE = "app/src/main/java/org/duofold/live/experimental/b8/"

def prepare(destination, candidate=None):
    versions = dict(VERSIONS)
    if candidate:
        versions["candidate"] = candidate
    for label, revision in versions.items():
        commit = subprocess.check_output(["git", "rev-parse", revision], cwd=ROOT, text=True).strip()
        folder = destination / "snapshots" / label
        folder.mkdir(parents=True)
        (folder / "commit.txt").write_text(commit + "\n")
        for name in ("LiveB8Scene.java", "SceneMapping.java"):
            (folder / name).write_bytes(subprocess.check_output(["git", "show", commit + ":" + SOURCE + name], cwd=ROOT))
    shutil.copytree(HERE / "b8-scene-tests", destination / "stubs")
    (destination / "stubs" / "LiveB8SceneTest.java").unlink()
    shutil.copy2(__file__, destination / "replay_b8_physical_routing.py")

def run(kit):
    results = {}
    for folder in sorted((kit / "snapshots").iterdir()):
        with tempfile.TemporaryDirectory(prefix="b8-routing-classes-") as tmp:
            files = list((kit / "stubs").rglob("*.java")) + list(folder.glob("*.java"))
            subprocess.run(["javac", "-d", tmp, *map(str, files)], check=True)
            print(folder.name + " " + (folder / "commit.txt").read_text().strip(), flush=True)
            result = subprocess.run(["java", "-cp", tmp, "org.duofold.live.experimental.b8.LiveB8PhysicalRoutingReplay"], check=True, text=True, capture_output=True)
            results[folder.name] = result.stdout
            print(result.stdout, end="", flush=True)
    if "candidate" in results:
        if results["candidate"] != results["alpha51"]:
            raise AssertionError("Candidate does not reproduce alpha51's behavior in both routing orders")
        print("Candidate matches alpha51 in both orders, including its known metadata-leading limitation.")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--export", type=Path)
    parser.add_argument("--candidate", help="Git revision to compare with alpha51 in both routing orders")
    args = parser.parse_args()
    if args.export:
        prepare(args.export, args.candidate)
        run(args.export)
    elif (HERE / "snapshots").exists():
        run(HERE)
    else:
        with tempfile.TemporaryDirectory(prefix="b8-routing-kit-") as tmp:
            prepare(Path(tmp), args.candidate)
            run(Path(tmp))

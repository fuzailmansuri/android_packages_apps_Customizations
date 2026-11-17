#!/usr/bin/env python3
"""Check and install feature source plus the selected ROM's integration hooks."""

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import subprocess
import tempfile


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else None


def checked_path(root, relative):
    parts = PurePosixPath(relative)
    if parts.is_absolute() or not parts.parts or any(p in ("..", ".git") for p in parts.parts):
        raise ValueError(f"Invalid integration path: {relative}")
    candidate = root / relative
    current = root
    for part in parts.parts:
        current /= part
        if current.is_symlink():
            raise ValueError(f"Symlink in integration path: {current}")
    candidate.resolve().relative_to(root.resolve())
    return candidate


def git_check(root, patch, reverse=False):
    command = ["git", "-C", str(root), "apply", "--check"]
    if reverse:
        command.append("--reverse")
    result = subprocess.run(command + [str(patch)], capture_output=True, text=True)
    return result.returncode == 0, result.stderr.strip()


def plan(bundle, tree, profile):
    manifest = json.loads(checked_path(bundle, f"adapters/{profile}/manifest.json").read_text())
    copies, patches = [], []
    for repo in manifest["repositories"]:
        destination = checked_path(tree, repo["path"])
        subprocess.run(["git", "-C", str(destination), "rev-parse", "--show-toplevel"],
                       check=True, stdout=subprocess.DEVNULL)
        for item in repo["files"]:
            source = checked_path(bundle, item["source"])
            if digest(source) != item["sha256"]:
                raise ValueError(f"Damaged feature source: {source}")
            target = checked_path(destination, item["target"])
            existing = digest(target)
            if existing == item["sha256"]:
                continue
            if target.exists():
                raise ValueError(f"Conflicting existing feature file: {target}")
            copies.append((source, target))
        patch = checked_path(bundle, repo["patch"])
        if digest(patch) != repo["patch_sha256"]:
            raise ValueError(f"Damaged adapter: {patch}")
        # Patch targets are explicit, checked paths as well; git apply adds its own validation.
        for relative in repo["patch_targets"]:
            checked_path(destination, relative)
        reverse_ok, _ = git_check(destination, patch, reverse=True)
        if reverse_ok:
            continue
        forward_ok, error = git_check(destination, patch)
        if not forward_ok:
            raise ValueError(f"Adapter conflicts in {destination}:\n{error}")
        patches.append((destination, patch))
    return copies, patches


def integrate(bundle, tree, profile, apply=False):
    bundle, tree = bundle.resolve(), tree.resolve()
    copies, patches = plan(bundle, tree, profile)
    print(f"{profile}: {len(copies)} feature files and {len(patches)} adapters to install")
    if not apply:
        return
    # Capture originals before any mutation and retain them if an unexpected failure occurs.
    backup = Path(tempfile.mkdtemp(prefix="customizations-integration-"))
    original = {}
    try:
        for destination, patch in patches:
            result = subprocess.run(["git", "-C", str(destination), "apply", "--numstat", str(patch)],
                                    check=True, capture_output=True, text=True)
            for line in result.stdout.splitlines():
                relative = line.split("\t", 2)[2]
                target = checked_path(destination, relative)
                original[target] = target.read_bytes() if target.exists() else None
        for source, target in copies:
            target.parent.mkdir(parents=True, exist_ok=True)
            original[target] = None
            with tempfile.NamedTemporaryFile(dir=target.parent, delete=False) as temporary:
                temporary.write(source.read_bytes())
                name = temporary.name
            os.chmod(name, 0o644)
            os.replace(name, target)
        for destination, patch in patches:
            subprocess.run(["git", "-C", str(destination), "apply", str(patch)], check=True)
    except Exception:
        for number, (target, contents) in enumerate(original.items()):
            if contents is not None:
                (backup / str(number)).write_bytes(contents)
            target.parent.mkdir(parents=True, exist_ok=True)
            if contents is None:
                target.unlink(missing_ok=True)
            else:
                target.write_bytes(contents)
        print(f"Integration failed; originals restored. Backup: {backup}")
        raise
    else:
        backup.rmdir()
        print("Integration complete.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("tree", type=Path)
    parser.add_argument("--rom", default="yaap-17")
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    try:
        integrate(Path(__file__).resolve().parents[1], args.tree, args.rom, args.apply)
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        parser.exit(1, f"{error}\n")


if __name__ == "__main__":
    main()

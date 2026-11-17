import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest import mock

MODULE = Path(__file__).resolve().parents[1] / "tools/integrate.py"
SPEC = importlib.util.spec_from_file_location("integrate", MODULE)
integration = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(integration)


class IntegrationTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.bundle = self.root / "bundle"
        self.tree = self.root / "rom"
        self.repo = self.tree / "frameworks/base"
        self.repo.mkdir(parents=True)
        subprocess.run(["git", "init", "-q", "-b", "main", str(self.repo)], check=True)
        (self.repo / "tracked.txt").write_text("old\n")
        self.payload = self.bundle / "features/feature.kt"
        self.payload.parent.mkdir(parents=True)
        self.payload.write_text("feature\n")
        self.patch = self.bundle / "adapters/test/hook.patch"
        self.patch.parent.mkdir(parents=True)
        self.patch.write_text("--- a/tracked.txt\n+++ b/tracked.txt\n@@ -1 +1 @@\n-old\n+new\n")
        manifest = {"repositories": [{
            "path": "frameworks/base",
            "files": [{"source": "features/feature.kt", "target": "new/feature.kt",
                       "sha256": hashlib.sha256(self.payload.read_bytes()).hexdigest()}],
            "patch": "adapters/test/hook.patch",
            "patch_sha256": hashlib.sha256(self.patch.read_bytes()).hexdigest(),
            "patch_targets": ["tracked.txt"],
        }]}
        (self.patch.parent / "manifest.json").write_text(json.dumps(manifest))

    def tearDown(self):
        self.temporary.cleanup()

    def test_check_does_not_write(self):
        integration.integrate(self.bundle, self.tree, "test")
        self.assertEqual((self.repo / "tracked.txt").read_text(), "old\n")
        self.assertFalse((self.repo / "new/feature.kt").exists())

    def test_apply_is_repeatable_and_preserves_unrelated_files(self):
        (self.repo / "user.txt").write_text("keep me\n")
        integration.integrate(self.bundle, self.tree, "test", apply=True)
        integration.integrate(self.bundle, self.tree, "test", apply=True)
        self.assertEqual((self.repo / "tracked.txt").read_text(), "new\n")
        self.assertEqual((self.repo / "new/feature.kt").read_text(), "feature\n")
        self.assertEqual((self.repo / "user.txt").read_text(), "keep me\n")

    def test_existing_feature_conflict_stops_before_hook_changes(self):
        target = self.repo / "new/feature.kt"
        target.parent.mkdir()
        target.write_text("user change\n")
        with self.assertRaises(ValueError):
            integration.integrate(self.bundle, self.tree, "test", apply=True)
        self.assertEqual(target.read_text(), "user change\n")
        self.assertEqual((self.repo / "tracked.txt").read_text(), "old\n")

    def test_hook_conflict_stops_before_feature_copy(self):
        (self.repo / "tracked.txt").write_text("different ROM\n")
        with self.assertRaises(ValueError):
            integration.integrate(self.bundle, self.tree, "test", apply=True)
        self.assertFalse((self.repo / "new/feature.kt").exists())

    def test_symlink_cannot_write_outside_the_repo(self):
        outside = self.root / "outside"
        outside.mkdir()
        (self.repo / "new").symlink_to(outside, target_is_directory=True)
        with self.assertRaises(ValueError):
            integration.integrate(self.bundle, self.tree, "test", apply=True)
        self.assertFalse((outside / "feature.kt").exists())

    def test_damaged_payload_stops_before_any_write(self):
        self.payload.write_text("changed unexpectedly\n")
        with self.assertRaises(ValueError):
            integration.integrate(self.bundle, self.tree, "test", apply=True)
        self.assertFalse((self.repo / "new/feature.kt").exists())
        self.assertEqual((self.repo / "tracked.txt").read_text(), "old\n")

    def test_unexpected_apply_failure_restores_originals(self):
        run = subprocess.run

        def failing_run(command, **kwargs):
            if "apply" in command and "--check" not in command and "--numstat" not in command:
                raise subprocess.CalledProcessError(1, command)
            return run(command, **kwargs)

        with mock.patch.object(integration.subprocess, "run", side_effect=failing_run):
            with self.assertRaises(subprocess.CalledProcessError):
                integration.integrate(self.bundle, self.tree, "test", apply=True)
        self.assertEqual((self.repo / "tracked.txt").read_text(), "old\n")
        self.assertFalse((self.repo / "new/feature.kt").exists())


if __name__ == "__main__":
    unittest.main()

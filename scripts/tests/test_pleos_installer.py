import hashlib
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

spec = importlib.util.spec_from_file_location(
    "pleos_installer", Path(__file__).resolve().parents[1] / "install-pleos-emulator.py")
installer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(installer)


class PleosInstallerTest(unittest.TestCase):
    def test_verified_image_preserved_with_default_and_override_ram(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            archive = root / "image.zip"
            payload = b"\0" * (1024 * 1024) + b"official-image-bytes"
            with zipfile.ZipFile(archive, "w") as z:
                z.writestr("crp_avd_out_x86_64/system.img", payload)
            with patch.object(installer, "SHA1", hashlib.sha1(archive.read_bytes()).hexdigest()), \
                    patch.object(installer, "SIZE", archive.stat().st_size):
                for ram in (None, 8192):
                    avd_home = root / str(ram)
                    args = (archive, root / "sdk", avd_home)
                    installer.install(*args) if ram is None else installer.install(*args, ram)
                    config = (avd_home / "CabinOS_Pleos_21.avd/config.ini").read_text()
                    self.assertIn(f"hw.ramSize={4096 if ram is None else ram}\n", config)
                    self.assertIn("fastboot.forceColdBoot=yes\n", config)
                    self.assertEqual((root / "sdk/system-images/android-34/pleos_car_emulator/x86_64/system.img").read_bytes(), payload)

    def test_invalid_archive_does_not_create_sdk_or_avd(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            archive = root / "bad.zip"
            archive.write_bytes(b"not an official image")
            with self.assertRaisesRegex(SystemExit, "checksum mismatch"):
                installer.install(archive, root / "sdk", root / "avd")
            self.assertFalse((root / "sdk").exists())
            self.assertFalse((root / "avd").exists())

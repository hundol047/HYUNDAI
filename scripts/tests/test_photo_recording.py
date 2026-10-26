import importlib.util
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

spec=importlib.util.spec_from_file_location('photo_recording',Path(__file__).resolve().parents[1]/'record_place_photos.py')
module=importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class CategoryControlTest(unittest.TestCase):
    def test_filter_wins_over_loading_photo_category_label(self):
        tree=ET.fromstring('<hierarchy><node text="카페" bounds="[860,280][920,330]"/><node text="카페" bounds="[820,480][850,500]"/></hierarchy>')
        self.assertEqual([860,280,920,330],module.category_control(tree,'카페'))

    def test_clipped_and_missing_labels_are_not_tappable(self):
        tree=ET.fromstring('<hierarchy><node text="카페" bounds="[0,0][0,0]"/></hierarchy>')
        with self.assertRaises(AssertionError):module.category_control(tree,'카페')

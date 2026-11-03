import importlib.util
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

spec = importlib.util.spec_from_file_location('stop_plan', Path(__file__).resolve().parents[1] / 'record_stop_plan.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ControlStateTest(unittest.TestCase):
    def test_disabled_button_can_contain_enabled_text_node(self):
        tree = ET.fromstring('<hierarchy><node enabled="false"><node text="extend" enabled="true"/></node></hierarchy>')
        self.assertTrue(module.control_disabled(tree, 'extend'))

    def test_enabled_ancestors_do_not_hide_enabled_button(self):
        tree = ET.fromstring('<hierarchy><node enabled="true"><node text="extend" enabled="true"/></node></hierarchy>')
        self.assertFalse(module.control_disabled(tree, 'extend'))

    def test_missing_control_is_a_failure(self):
        with self.assertRaises(AssertionError):
            module.control_disabled(ET.fromstring('<hierarchy/>'), 'extend')

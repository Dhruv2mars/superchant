"""Unit tests for play_version.py's pure part: python3 -m unittest discover -s expo/scripts"""
import unittest

from play_version import next_build_number


class NextBuildNumber(unittest.TestCase):
    def test_first_upload_of_the_day(self):
        self.assertEqual(0, next_build_number(26293, []))
        # Earlier days and the 1.x versionCodes
        self.assertEqual(0, next_build_number(26293, [3, 2629299]))

    def test_one_above_the_highest_today(self):
        # Two builds in the same quarter-hour, or a re-run after an upload that went through
        self.assertEqual(49, next_build_number(26293, [2629200, 2629348]))
        self.assertEqual(99, next_build_number(26293, [2629398]))

    def test_never_reuses_or_goes_down(self):
        with self.assertRaises(ValueError):
            next_build_number(26293, [2629399])
        with self.assertRaises(ValueError):
            next_build_number(26293, [2629400])  # a later day was already uploaded


if __name__ == "__main__":
    unittest.main()

"""Unit tests for the release scripts' pure parts: python3 -m unittest discover -s scripts"""
import unittest

from play_promote import pick_release
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


class PickRelease(unittest.TestCase):
    TREE = "1a2b3c4d5e6f7a8b9c0d1a2b3c4d5e6f7a8b9c0d"

    @staticmethod
    def release(code, tree, status="completed"):
        return {"versionCodes": [str(code)], "name": f"{code} (tree {tree})", "status": status}

    def test_the_build_of_this_source_not_the_newest_upload(self):
        mine = self.release(2629348, "1a2b3c4d5e6f")
        newer = self.release(2629349, "ffffffffffff")
        self.assertEqual(mine, pick_release([newer, mine], self.TREE))
        # The same source uploaded twice: the newer copy
        again = self.release(2629350, "1a2b3c4d5e6f")
        self.assertEqual(again, pick_release([mine, again], self.TREE))

    def test_none_when_this_source_was_not_uploaded(self):
        self.assertIsNone(pick_release([self.release(2629349, "ffffffffffff")], self.TREE))
        self.assertIsNone(pick_release([self.release(2629348, "1a2b3c4d5e6f", status="draft")], self.TREE))
        self.assertIsNone(pick_release([{"versionCodes": ["2629300"], "status": "completed"}], self.TREE))
        self.assertIsNone(pick_release([], self.TREE))


if __name__ == "__main__":
    unittest.main()

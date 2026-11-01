import unittest

from version import release_metadata


class ReleaseVersionTests(unittest.TestCase):
    def test_version_and_tag_have_identical_apk_metadata(self) -> None:
        expected = {"version": "1.2.3", "tag": "v1.2.3", "code": 1_002_003}
        self.assertEqual(release_metadata("1.2.3"), expected)
        self.assertEqual(release_metadata("v1.2.3"), expected)

    def test_first_version_and_android_version_code_limit(self) -> None:
        self.assertEqual(release_metadata("0.0.1")["code"], 1)
        self.assertEqual(release_metadata("1999.999.999")["code"], 1_999_999_999)

    def test_versions_increase_across_patch_minor_and_major_rollovers(self) -> None:
        versions = ["0.1.999", "0.2.0", "0.999.999", "1.0.0"]
        codes = [release_metadata(version)["code"] for version in versions]
        self.assertEqual(codes, sorted(set(codes)))

    def test_rejects_ambiguous_overflow_and_shell_or_output_injection(self) -> None:
        for version in ["0.0.0", "01.2.3", "1.2", "1.2.3-beta", "2000.0.0", "1.1000.0", "1.0.1000", "1.2.3\ntag=other"]:
            with self.subTest(version=version), self.assertRaises(ValueError):
                release_metadata(version)


if __name__ == "__main__":
    unittest.main()

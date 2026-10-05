#!/usr/bin/env python3
"""Print measured Gradle test results; fail on missing, failed or skipped tests."""

import argparse
from pathlib import Path
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--results", type=Path, default=Path("build/test-results"))
    args = parser.parse_args()
    print("| Profile | Suites | Tests | Passed | Failures | Errors | Skipped | Suite time |")
    print("|---|---:|---:|---:|---:|---:|---:|---:|")
    unsuccessful = False
    for profile in ("test", "integrationTest"):
        files = sorted((args.results / profile).glob("TEST-*.xml"))
        if not files:
            print(f"Missing XML results: {profile}")
            unsuccessful = True
            continue
        totals = dict(tests=0, failures=0, errors=0, skipped=0)
        duration = 0.0
        for path in files:
            suite = ET.parse(path).getroot()
            for key in totals:
                totals[key] += int(suite.attrib[key])
            duration += float(suite.attrib["time"])
        tests, failures, errors, skipped = (totals[key] for key in totals)
        passed = tests - failures - errors - skipped
        print(f"| {profile} | {len(files)} | {tests} | {passed} | {failures} | {errors} | {skipped} | {duration:.3f}s |")
        unsuccessful |= tests == 0 or failures > 0 or errors > 0 or skipped > 0
    return int(unsuccessful)


if __name__ == "__main__":
    raise SystemExit(main())

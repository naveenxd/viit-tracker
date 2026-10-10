#!/usr/bin/env python3
"""Generates the in-app update manifest (version.json) for viit-tracker.

The manifest lives on this repo's `main` branch and is served to clients via
raw.githubusercontent.com's CDN (no api.github.com calls, no rate limits —
mirrors naveenxd/mirai-releases' version.json architecture). The APK
URLs point at this repo's GitHub Release assets for the tag (also CDN-served).

Unlike mirai's generator, this one reads the *actual* versionCode out of each
built APK (apkanalyzer/aapt) instead of hardcoding AGP's per-ABI offsets, and
computes sha256 itself — so the manifest always matches what really gets
installed.

Schema (v2 — old keys keep working, new keys are additive):
  {
    "latest_version": "1.1.0",
    "latest_build": 64,                     // max of all APK codes
    "apk_build": 63,                        // universal APK's code
    "apk_url":  ".../viit-tracker-1.1.0.apk",
    "apk_urls": { "arm64-v8a": "...", ... },
    "apk_builds": { "arm64-v8a": 64, ... },
    "sha256": { "universal": "<hex>", "arm64-v8a": "<hex>", ... },
    "changelog": ["bullet", ...],
    "force_update": false,
    "history": [ { "version": "...", "build": N, "changelog": [...] } ]
  }

Usage:
  python3 tool/release/generate_version_json.py \\
    --tag v1.1.0 \\
    --universal-apk path/to/viit-tracker-1.1.0.apk \\
    --split arm64-v8a=path/to/viit-tracker-1.1.0-arm64-v8a.apk \\
    --split armeabi-v7a=... --split x86_64=... \\
    [--changelog changelog/1.1.0.md] \\
    [--existing version.json] \\
    --out version.json \\
    [--force-update]
"""

import argparse
import hashlib
import json
import os
import re
import subprocess

ABIS = ["arm64-v8a", "armeabi-v7a", "x86_64"]

RELEASE_BASE = "https://github.com/naveenxd/viit-tracker/releases/download/{tag}"


def sha256_of(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def _sdk_tool(*candidates):
    for tool in candidates:
        if tool and os.path.exists(tool):
            return tool
    return None


def read_version_code(apk: str) -> int:
    """Reads versionCode straight from a built APK.

    Prefers `apkanalyzer` (cmdline-tools), falls back to aapt/aapt2 from any
    installed build-tools. Fails loudly: a wrong code bricks update delivery
    for every install flavor.
    """
    sdk = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME") or ""

    apkanalyzer = _sdk_tool(
        os.path.join(sdk, "cmdline-tools", "latest", "bin", "apkanalyzer"),
    )
    if apkanalyzer:
        out = subprocess.run(
            [apkanalyzer, "manifest", "version-code", apk],
            capture_output=True, text=True, check=True,
        ).stdout.strip()
        if out.isdigit():
            return int(out)

    build_tools = ""
    bt_root = os.path.join(sdk, "build-tools")
    if os.path.isdir(bt_root):
        # Highest installed build-tools version.
        versions = sorted(os.listdir(bt_root))
        if versions:
            build_tools = os.path.join(bt_root, versions[-1])
    aapt = _sdk_tool(
        os.path.join(build_tools, "aapt") if build_tools else None,
        shutil_which("aapt"),
    )
    if aapt:
        out = subprocess.run(
            [aapt, "dump", "badging", apk],
            capture_output=True, text=True, check=True,
        ).stdout
        m = re.search(r"versionCode='(\d+)'", out)
        if m:
            return int(m.group(1))

    raise SystemExit(
        f"error: could not read versionCode from {apk} "
        "(apkanalyzer/aapt not found — set ANDROID_SDK_ROOT)"
    )


def shutil_which(name: str):
    import shutil
    return shutil.which(name)


def read_changelog(path: str, version: str) -> list:
    if not path or not os.path.exists(path):
        return [f"Release {version}"]
    bullets = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            line = re.sub(r"^[-*]\s+", "", line)
            bullets.append(line)
    return bullets or [f"Release {version}"]


def build_manifest(args: argparse.Namespace) -> dict:
    version = args.tag.lstrip("v")
    tag = f"v{version}"
    base = RELEASE_BASE.format(tag=tag)
    changelog = read_changelog(args.changelog, version)

    prefix = f"viit-tracker-{version}"

    universal_name = f"{prefix}.apk"
    apk_urls = {}
    apk_builds = {}
    sha256 = {}

    # Per-ABI split APKs (the path the updater prefers).
    for abi, path in args.split:
        if abi not in ABIS:
            raise SystemExit(f"error: unknown ABI '{abi}' (expected one of {ABIS})")
        name = f"{prefix}-{abi}.apk"
        apk_urls[abi] = f"{base}/{name}"
        apk_builds[abi] = read_version_code(path)
        sha256[abi] = sha256_of(path)

    # Flat fallback (unknown ABIs, manual downloads): the universal build.
    universal_build = read_version_code(args.universal_apk)
    sha256["universal"] = sha256_of(args.universal_apk)

    codes = [universal_build, *apk_builds.values()]
    manifest = {
        "latest_version": version,
        # Max across flavors so a stale install of ANY flavor sees the update.
        "latest_build": max(codes),
        "apk_build": universal_build,
        "apk_url": f"{base}/{universal_name}",
        "apk_urls": apk_urls,
        "apk_builds": apk_builds,
        "sha256": sha256,
        "changelog": changelog,
        "force_update": args.force_update,
    }

    # Preserve + extend the history array from the existing manifest.
    history = []
    if args.existing and os.path.exists(args.existing):
        try:
            with open(args.existing, encoding="utf-8") as f:
                history = json.load(f).get("history") or []
        except (json.JSONDecodeError, OSError):
            history = []
    history = [
        entry for entry in history
        if not (entry.get("version") == version and entry.get("build") == manifest["apk_build"])
    ]
    history.insert(
        0, {"version": version, "build": manifest["apk_build"], "changelog": changelog}
    )
    manifest["history"] = history

    return manifest


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", required=True, help="Release tag, e.g. v1.1.0")
    parser.add_argument("--universal-apk", required=True, help="Universal/fat APK path")
    parser.add_argument("--split", action="append", default=[], metavar="ABI=PATH",
                        help="Per-ABI split APK, repeatable (e.g. arm64-v8a=x.apk)")
    parser.add_argument("--changelog", default="", help="Markdown changelog file")
    parser.add_argument("--existing", default="", help="Current manifest to inherit history from")
    parser.add_argument("--out", required=True, help="Output file path")
    parser.add_argument("--force-update", action="store_true", help="Set force_update: true")
    args = parser.parse_args()

    args.split = [s.split("=", 1) for s in args.split]

    manifest = build_manifest(args)
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print(f"wrote {args.out} (version={manifest['latest_version']}, "
          f"build={manifest['latest_build']}, force={manifest['force_update']}, "
          f"abis={sorted(manifest['apk_builds'])})")


if __name__ == "__main__":
    main()

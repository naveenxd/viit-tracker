# Changelog

One markdown file per release, named after the version **without** the `v`
(`1.1.0.md` for the `v1.1.0` tag). Each non-empty line becomes one bullet in
the in-app update sheet and is copied into `version.json`'s `changelog` array
by `tool/release/generate_version_json.py`.

```markdown
* Faster dashboard loads and less jank while scrolling
* Fixed stale attendance projections
* Smaller app updates that download the build for your device
```

If the file is missing for a tag, the updater shows `Release <version>`
instead — nothing fails.

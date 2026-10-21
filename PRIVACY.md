# Privacy Policy — VIIT Tracker

Last updated: 21 October 2026

VIIT Tracker is an open-source attendance app for VIIT students. This policy
explains the small amount of install and usage data the release app sends to a
private analytics endpoint. It does **not** collect your attendance records,
credentials, name, roll number, location, contacts, files, or advertising ID.

## What is collected

On a fresh install, after an app update, and at most once per IST calendar day,
the release build may send:

- a **hashed device identifier** — SHA-256 of the Android ID plus an
  app-specific salt, computed on the device; the raw Android ID never leaves the
  device
- app version name and version code
- build channel (`github` or `play`) and installer package
- Android API level and release
- device manufacturer, model, device codename, and CPU ABI
- event type: `install`, `update`, or `open`
- the server-side timestamp and IST calendar day of the check-in

The analytics database does **not** store your IP address. It does not use
cookies, advertising IDs, location, timezone, or locale. Debug builds do not
send analytics.

## Why it is collected

The data is used only to understand:

- how many devices have the app installed
- how many are active daily, weekly, and monthly
- which app and Android versions are in use
- which device models and distribution channels are common
- whether updates are being adopted

There is no advertising, profiling, sale, or sharing of this data for marketing.

## Where it is stored

Data is stored in Cloudflare D1 (Cloudflare, Inc.) behind a Worker endpoint.
Cloudflare acts as the hosting provider. All app-to-server traffic uses HTTPS.
The analytics dashboard and stats API are private and protected by Cloudflare
Access with email one-time PIN authentication.

Raw per-day activity rows are kept for **90 days**. The device row and daily
aggregate statistics are kept longer so historical trends remain available.
No third-party analytics SDK (for example Firebase Analytics) is included.

## Your choices

The check-in is part of the release build and has no in-app toggle. Uninstalling
the app stops all future check-ins. Because the device identifier is a one-way
hash and is not linked to an account, historical rows cannot be reliably mapped
back to a person; aggregate statistics may remain after uninstalling.

If you have a privacy question or want to report a problem, open an issue at
<https://github.com/naveenxd/viit-tracker/issues>.

## Children

VIIT Tracker is intended for college students and is not directed to children
under 13.

## Changes

If this policy changes, the “Last updated” date above will change and the new
version will be published with the app.

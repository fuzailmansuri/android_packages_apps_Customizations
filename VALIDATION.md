# Validation

A previous YAAP Android 17 GApps build completed successfully on 2026-11-17 using
`lunch yaap_asteroids cp2a user`, `TARGET_BUILD_GAPPS=true`, and `m -j64 yaap`.
SystemUI, Settings and Launcher3QuickStep compiled, signed and installed into the
target images. The build completed with compiler/build warnings and an empty
`out/error.log`.

Generated OTA: `YAAP-17-HOMEMADE-asteroids-20261117.zip` (2,929,932,710 bytes).
SHA256: `ee27905fcd0e0676beb563785b4554402b6d9cbb77623fed72e793d14231f759`.

The subsequent Settings, Launcher and Lunarisbar updates have not completed a
new full ROM build or installed-device acceptance. Focused source checks and the
integration checks below cover the current source; the earlier OTA is not evidence
that these later changes compile or run correctly.

All seven integration-tool host tests pass, covering repeat application, checks
without writes, damaged payloads, file and patch conflicts, symlink rejection and
rollback after unexpected failure. All 187 shared feature files match the active
source tree; the YAAP adapter check reports zero files or patches to install.
Android port tests have been written but have not been run on a device.

Run the integration tool's host checks with:

```sh
python3 -m unittest discover -s packages/apps/Customizations/tests -v
```

Device acceptance remains required for Launcher layout/actions, QS click/long-press,
edit/drag/resize, Lunarisbar cutouts/rotation/input, notification privacy, media/call
actions, widgets, user switching and disable/enable cleanup.

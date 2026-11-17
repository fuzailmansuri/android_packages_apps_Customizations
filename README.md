# Android UI Customizations

Shared feature source and explicit ROM adapters, maintained by fruitzail.

This repository carries At a Glance, circular quick settings, and Lunarisbar without
requiring a personal fork of every ROM repository. Imported source keeps its license
headers; pinned upstream revisions and contributor credits live in `PROVENANCE.md`.

The feature files live under `features/`. Small changes to existing framework,
SystemUI, Settings and Launcher files live under `adapters/<rom>/`. They remain in
their owning Android modules after integration, so their existing privileged APIs,
resources and build rules continue to apply.

For the prepared YAAP Android 17 adapter, clone this repository to
`packages/apps/Customizations` in the ROM source tree, then run:

```sh
python3 packages/apps/Customizations/tools/integrate.py /path/to/rom --rom yaap-17
python3 packages/apps/Customizations/tools/integrate.py /path/to/rom --rom yaap-17 --apply
```

The first command checks all files and patches without changing anything. The second
installs the checked feature files and applies the adapter. Repeating either command
is safe: matching files and applied patches are detected. Conflicting existing files,
invalid paths, symlinks and damaged payloads stop integration before writing.

All three features start disabled and remain configurable in their normal Launcher
or Settings pages. Lunarisbar observes the current user, keyguard and doze state and
uses the existing status bar window. Its notification filter only suppresses an awake
heads-up notification already visible in the island; full-screen intents and ambient
pulses keep their existing behavior.

Other ROMs need their own adapter. Copy the nearest adapter into a new profile, adapt
its hooks to that ROM's actual APIs and settings layout, and validate its build and
device behavior. Shared source is portable; framework and Launcher internals differ
between ROMs and Android releases.

Validation status is recorded in `VALIDATION.md`. Compilation and host checks do not
establish phone behavior.

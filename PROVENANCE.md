# Source and contributor credits

Imported files retain their original copyright and license headers. fruitzail
maintains the ROM integration and is the committer for the imported work.

## At a Glance

Source: ArcvelleOS/android_packages_apps_Launcher3.

- Introduction: `b9dab666ed1e6ce12c1819bdc2b53ef493ac95af`, Ghosuto
  `<clash.raja10@gmail.com>`.
- Weather: `801e96ac0de9bab1c1b7f82b96551dc3b8682eaf`, Ghosuto.
- Preview: `17e9fd68f1f43e0bee2d833dd0c3fc5723174778`, ShadeDark6
  `<nizamfa6@gmail.com>`.
- Tap handling: `b9bb56ba8def5c05eef77ca5e8e0d8e88ad00c83`, ShadeDark6.

The YAAP adapter adds lifecycle and visibility gating, preserves workspace contents,
uses Launcher launch helpers, validates actions, and keeps the feature off by default.

## Circular quick settings

Source: ArcvelleOS/android_packages_apps_Settings,
`cc96f4f5a4a0cf9ccc099b5d215591ec12835303`, ShadeDark6
`<nizamfa6@gmail.com>`; associated framework wiring is recorded in the adapter.

Original AOSPA and Android copyright headers remain in the source. The YAAP adapter
uses its current Settings repository interface and Compose tile APIs, covers expanded,
collapsed, edit and scene paths, and keeps circular tiles disabled by default.

## Lunarisbar

Source: Lunaris-AOSP/frameworks_base, branch `16.2`,
`9cd112189c92229aabb7762af48b0019100cf070`.

Source: Lunaris-AOSP/packages_apps_Singularity, branch `16.2`,
`7fb8795d8da0a683d0acc8a0f385caed54484f0d`.

Upstream feature and Settings work: Ghosuto `<clash.raja10@gmail.com>`; original
Android, Lunaris AOSP, and PenguinOS headers are retained for their respective files.

The YAAP adapter uses its notification pipeline, background blur drawable, display
repositories and native privacy policy. It owns view/session cleanup, limits heads-up
suppression to visible notification keys, and declares a permission-protected widget
picker. It does not change the global heads-up setting.

## Rounded QS brightness slider

Source: [ArcvelleOS/android_frameworks_base](https://github.com/ArcvelleOS/android_frameworks_base),
`e161b5f271c4ce604d3525dc0a8c99735db16204` (round track) and
`4ec40b9b380862709ddd2082f4ec39d15d3b6b08` (optional style), ShadeDark6
`<nizamfa6@gmail.com>`. Settings switch: [ArcvelleOS/android_packages_apps_Settings](https://github.com/ArcvelleOS/android_packages_apps_Settings/commit/0cc6c8365ef9b3b3431c4e6ba8119d25dfdb1c99),
`0cc6c8365ef9b3b3431c4e6ba8119d25dfdb1c99`.

The YAAP adapter observes the current user's setting in the shared brightness view
model, covering QS, scene, overlay and mirror callers, retains YAAP's auto-brightness
button and haptics, and defaults to the original slider. Circular QS framework
sources are ArcvelleOS `d6a5ec23eed4cdca14aa7e704b2ce274d5182813` and
`b2a32f19d5c53fe2b41dad5caa6ae9a639d4ef94`; its existing switch remains off by default.

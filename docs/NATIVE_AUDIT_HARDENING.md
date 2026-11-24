# Android native audit and regression alignment

Static ELF64 checks now reject PT_LOAD virtual-address overflow, libraries with no file-backed PT_LOAD segment, and Unix symlink entries disguised as native libraries. Existing ABI, file bounds and 16 KiB segment alignment checks remain. The checks are intentionally conservative for Android shared-library APK entries. Eight Python unittest methods executed against synthetic ELF/ZIP fixtures; this is not a real APK, Android installation or 16 KiB runtime test. Debug verification continues to retain diagnostic APKs even on alignment failure; production checks remain unchanged.

Two legacy structural assertions for browser restoration and failed mode switching are updated to match the existing saved-state resolver and rollback behavior. Full repository Python suite has not been executed in this environment.

References: https://developer.android.com/guide/practices/page-sizes ; https://developer.android.com/tools/zipalign ; https://github.com/mozilla/geckoview/issues/262 ; https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow . No external code copied, dependencies added or workflow trigger changed.

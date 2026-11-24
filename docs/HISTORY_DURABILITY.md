# History durability and corruption handling

The Android native conversation store now supplies a non-destructive corruption handler through SQLiteOpenHelper.setOpenParams before the first open, then enables WAL as previously. On a reported corruption event the handler throws SQLiteDatabaseCorruptException instead of invoking Android's default file-deleting handler. No recovery, deletion, recreation, backup overwrite or diagnostic upload is attempted. The app's existing operation failure path remains; a dedicated recovery screen is still required. This does not guarantee that the OS or SQLite will never change sidecar files while opening or closing a damaged database.

The builder explicitly selects FULL synchronization (API 28 method, minSdk 29) rather than relying on a device-specific WAL default. This is a conscious trade-off in favor of durable conversation commits; FULL adds a WAL sync per commit and may increase I/O latency. Retain foreign keys and the existing indexes/transaction boundaries. Existing schema version and database path are unchanged. The initialization callback also refuses an unversioned database containing unrelated objects, excluding Android's metadata and SQLite internal objects. No schema migration or destructive repair.

Verification: 46 actual host Python reference tests passed (42 existing plus four new tests); the new tests use real SQLite files, verify corrupt bytes are preserved, FULL/WAL/foreign keys on separate connections, successful reopening, and preservation of unknown/unrelated schemas. These test the shared Python reference, NOT the changed Android framework code. Exact tested regression-test blob verified before publication. Android SDK/NDK and .NET SDK are absent here. Android configuration and error handling have NOT been compiled or exercised on device. No power-cut or storage durability benchmark; fsync effectiveness still depends on the underlying filesystem and device. Before release verify actual Android PRAGMA values, deliberately corrupt only a disposable test database, verify non-destructive failure and recovery with a separate backup, and measure write latency. Never test corruption on the user's actual history. Local plaintext storage remains a separate limitation.

Research:
- https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/database/DefaultDatabaseErrorHandler.java
- https://android.googlesource.com/platform/frameworks/base/+/HEAD/core/java/android/database/sqlite/SQLiteOpenHelper.java
- https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase.OpenParams.Builder
- https://developer.android.com/topic/performance/sqlite-performance-best-practices
- https://www.sqlite.org/pragma.html#pragma_synchronous

No external implementation code imported, no additional dependency, no Windows production code changes, no APK/EXE, no scheduled operation or background monitoring.

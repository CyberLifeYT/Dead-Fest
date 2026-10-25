"""
Fails the build if R8 stripped a constructor that something creates by reflection.

The R8 build of 2.1.2 crashed on launch because R8 removed WorkDatabase_Impl's no-arg constructor
(Room creates it by reflection). Unit tests cannot see that; R8's own report can. After
`:app:assembleRelease`, seeds.txt lists every member R8 was told to keep, so each constructor below
must appear there.

    python tools/check-r8-keeps.py [app/build/outputs/mapping/release]
"""

import sys
from pathlib import Path

REQUIRED = [
    # Room / WorkManager: the background check-ins' job database.
    "androidx.work.impl.WorkDatabase_Impl: WorkDatabase_Impl()",
    # WorkManager instantiates workers by class name.
    "com.deadfest.work.CheckInWorker: CheckInWorker(android.content.Context,androidx.work.WorkerParameters)",
    # Firestore maps documents onto these through their no-arg constructors.
    "com.deadfest.data.model.User: User()",
    "com.deadfest.data.model.PlayerData: PlayerData()",
    "com.deadfest.data.model.EventLog: EventLog()",
]


def main() -> int:
    folder = Path(sys.argv[1] if len(sys.argv) > 1 else "app/build/outputs/mapping/release")
    seeds_file = folder / "seeds.txt"
    if not seeds_file.exists():
        print(f"No {seeds_file}: run :app:assembleRelease first.")
        return 2
    seeds = set(line.strip() for line in seeds_file.read_text(encoding="utf-8").splitlines())
    missing = [r for r in REQUIRED if r not in seeds]
    for r in REQUIRED:
        print(("MISSING  " if r in missing else "kept     ") + r)
    if missing:
        print("\nR8 removed constructors the app needs at runtime. Add keep rules in app/proguard-rules.pro.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

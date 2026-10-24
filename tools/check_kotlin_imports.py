"""Reports Kotlin imports that are never used in their file. Run from the repository root."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "android/app/src"


def main() -> int:
    problems = []
    for kt in ROOT.rglob("*.kt"):
        text = kt.read_text(encoding="utf-8")
        body = re.sub(r"^import .*$", "", text, flags=re.M)
        for imported in re.findall(r"^import ([\w.]+)(?: as (\w+))?$", text, flags=re.M):
            path, alias = imported
            name = alias or path.split(".")[-1]
            if not re.search(rf"\b{re.escape(name)}\b", body):
                problems.append(f"{kt.relative_to(ROOT)}: unused import {path}")
    print("\n".join(problems))
    print(f"{len(problems)} unused import(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())

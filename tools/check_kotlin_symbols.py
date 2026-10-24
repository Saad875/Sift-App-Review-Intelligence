"""Heuristic check that project types used in a Kotlin file are imported.

Collects top-level classes, objects and interfaces declared in the app module, then reports files that use
one from another package without importing it. Run from the repository root.
"""
from __future__ import annotations

import re
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "android/app/src"
DECL = re.compile(
    r"^(?:(?:private|internal|public|data|sealed|abstract|open|enum|inline|value|annotation|operator|infix|suspend)\s+)*"
    r"(?:class|object|interface|fun|val|var|typealias)\s+(?:<[^>]+>\s+)?(?:[\w.<>?, ]+\.)?(\w+)",
    re.M,
)


def package_of(text: str) -> str:
    match = re.search(r"^package ([\w.]+)", text, re.M)
    return match.group(1) if match else ""


def main() -> int:
    files = {kt: kt.read_text(encoding="utf-8") for kt in ROOT.rglob("*.kt")}
    symbols: dict[str, set[str]] = defaultdict(set)
    for kt, text in files.items():
        if "/test/" in kt.as_posix():
            continue
        package = package_of(text)
        for line in text.splitlines():
            if line.startswith((" ", "\t")) or line.startswith("private "):
                continue
            match = DECL.match(line)
            if match and match.group(1)[0].isupper():
                symbols[match.group(1)].add(package)
    problems = []
    for kt, text in files.items():
        package = package_of(text)
        imports = set(re.findall(r"^import ([\w.]+)", text, re.M))
        imported_names = {i.split(".")[-1] for i in imports}
        wildcard = {i[:-2] for i in re.findall(r"^import ([\w.]+\.\*)", text, re.M)}
        body = re.sub(r"^(import|package) .*$", "", text, flags=re.M)
        body = re.sub(r'"(?:\\.|[^"\\])*"', '""', body)
        body = re.sub(r"//.*$", "", body, flags=re.M)
        body = re.sub(r"/\*.*?\*/", "", body, flags=re.S)
        for name, packages in symbols.items():
            if package in packages or name in imported_names or packages & wildcard:
                continue
            # Member access like `x.name` could be a member, not the top-level symbol; only flag uses
            # that look like top-level references or extension calls on a receiver.
            pattern = rf"(?<![\w.])\b{name}\b(?!\s*=[^=])|\.{name}\b"
            for match in re.finditer(pattern, body):
                if match.group(0).startswith(".") and not name[0].islower():
                    continue
                if match.group(0).startswith("."):
                    # Extension use: only report when the symbol is an extension declared in the project.
                    if not any(re.search(rf"^(?:fun|val|var)\s+(?:<[^>]+>\s+)?[\w.<>?, ]+\.{name}\b", t, re.M)
                               for t in files.values()):
                        continue
                    # Skip if the file defines a member with the same name.
                    if re.search(rf"(?:fun|val|var)\s+{name}\b", text):
                        continue
                problems.append(f"{kt.relative_to(ROOT)}: uses {name} from {sorted(packages)} without import")
                break
    for problem in sorted(set(problems)):
        print(problem)
    print(f"{len(set(problems))} possible missing import(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())

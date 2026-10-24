"""Static checks for the Android module, used instead of a Gradle build.

Reports resources referenced from Kotlin or XML that are not defined,
ViewBinding properties that have no matching view id, and string format
arguments. Run from the repository root:

    python tools/check_android_resources.py
"""
from __future__ import annotations

import re
import sys
from pathlib import Path
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parent.parent / "android/app/src/main"
RES = ROOT / "res"
KOTLIN = ROOT / "java"
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"


def defined_resources() -> dict[str, set[str]]:
    found: dict[str, set[str]] = {}
    for values in RES.glob("values*/*.xml"):
        tree = ElementTree.parse(values)
        for element in tree.getroot():
            name = element.get("name")
            if not name:
                continue
            kind = element.tag
            if kind == "item":
                kind = element.get("type", "item")
            if kind == "declare-styleable":
                kind = "styleable"
            if kind in ("string-array", "integer-array", "array"):
                kind = "array"
            found.setdefault(kind, set()).add(name.replace(".", "_"))
    for folder in RES.iterdir():
        if not folder.is_dir() or folder.name.startswith("values"):
            continue
        kind = folder.name.split("-")[0]
        for file in folder.iterdir():
            found.setdefault(kind, set()).add(file.name.split(".")[0])
    # Ids declared with @+id/ anywhere.
    for xml in RES.rglob("*.xml"):
        for match in re.finditer(r"@\+id/(\w+)", xml.read_text(encoding="utf-8")):
            found.setdefault("id", set()).add(match.group(1))
    return found


def check_references(defined: dict[str, set[str]]) -> list[str]:
    problems = []
    xml_ref = re.compile(r"@(string|color|dimen|drawable|style|integer|plurals|layout|menu|xml|anim|animator|mipmap|font|bool|array|id)/([\w.]+)")
    for xml in RES.rglob("*.xml"):
        text = xml.read_text(encoding="utf-8")
        for kind, name in xml_ref.findall(text):
            key = name.replace(".", "_")
            if kind == "style" and key in defined.get("style", set()):
                continue
            if key not in defined.get(kind, set()):
                problems.append(f"{xml.relative_to(ROOT)}: missing @{kind}/{name}")
    kt_ref = re.compile(r"\bR\.(string|color|dimen|drawable|style|integer|plurals|layout|menu|xml|anim|animator|mipmap|id|styleable|attr|array|bool)\.(\w+)")
    for kt in KOTLIN.rglob("*.kt"):
        text = kt.read_text(encoding="utf-8")
        for kind, name in kt_ref.findall(text):
            if kind == "styleable":
                base = name.split("_")[0]
                if base not in defined.get("styleable", set()):
                    problems.append(f"{kt.relative_to(ROOT)}: missing R.styleable.{name}")
                continue
            if kind == "attr":
                continue
            if name not in defined.get(kind, set()):
                problems.append(f"{kt.relative_to(ROOT)}: missing R.{kind}.{name}")
    return problems


def layout_ids() -> dict[str, dict[str, str]]:
    """Map binding class name to {camelCaseId: included layout or ''}."""
    result = {}
    for layout in (RES / "layout").glob("*.xml"):
        binding = "".join(part.capitalize() for part in layout.stem.split("_")) + "Binding"
        ids = {}
        tree = ElementTree.parse(layout)
        for element in tree.iter():
            view_id = element.get(f"{ANDROID_NS}id")
            if view_id and view_id.startswith("@+id/"):
                raw = view_id[5:]
                camel = re.sub(r"_(\w)", lambda m: m.group(1).upper(), raw)
                include = element.get("layout", "") if element.tag == "include" else ""
                ids[camel] = include.replace("@layout/", "")
        result[binding] = ids
    return result


def check_bindings() -> list[str]:
    """Check `binding.x.y` chains against layout ids (one include level deep)."""
    problems = []
    layouts = layout_ids()
    for kt in KOTLIN.rglob("*.kt"):
        text = kt.read_text(encoding="utf-8")
        bindings = set(re.findall(r"\b(\w+Binding)\b", text)) & set(layouts)
        if not bindings:
            continue
        # Which binding does `binding` refer to in this file?
        primary = re.search(r"BaseFragment<(\w+Binding)>", text)
        holders = re.findall(r"private val binding: (\w+Binding)", text)
        candidates = [primary.group(1)] if primary else holders
        for binding_class in candidates:
            ids = layouts.get(binding_class, {})
            for first, second in re.findall(r"\bbinding\.(\w+)(?:\.(\w+))?", text):
                if first in ("root",):
                    continue
                if first not in ids:
                    problems.append(f"{kt.relative_to(ROOT)}: {binding_class} has no view '{first}'")
                    continue
                include = ids[first]
                if include and second and second != "root":
                    inner = layouts.get("".join(p.capitalize() for p in include.split("_")) + "Binding", {})
                    if second not in inner and not second[0].isupper() and second not in KNOWN_MEMBERS:
                        problems.append(f"{kt.relative_to(ROOT)}: {include} has no view '{second}' (via {first})")
    return problems


KNOWN_MEMBERS = {"bind", "let", "also", "apply", "run", "isVisible", "visibility"}


def check_format_args() -> list[str]:
    problems = []
    for values in RES.glob("values*/strings.xml"):
        text = values.read_text(encoding="utf-8")
        for name, body in re.findall(r'<string name="(\w+)"[^>]*>(.*?)</string>', text, re.S):
            if re.search(r"%(?!\d\$|%)[sdf]", body) and len(re.findall(r"%[sdf]", body)) > 1:
                problems.append(f"{values.name}: {name} has several unnumbered format arguments")
            if "'" in body and "\\'" not in body and not body.startswith('"'):
                problems.append(f"{values.name}: {name} has an unescaped apostrophe")
    return problems


def main() -> int:
    defined = defined_resources()
    problems = check_references(defined) + check_bindings() + check_format_args()
    for problem in sorted(set(problems)):
        print(problem)
    print(f"{len(set(problems))} problem(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())

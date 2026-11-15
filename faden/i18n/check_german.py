#!/usr/bin/env python3
"""Checks the German catalog against the strings the app looks up.

Finds every t("…") and tn(n, "…", "…") call with literal text in app/src/main, every entry in i18n/German*.kt,
and reports:
  - missing: looked up but not translated
  - unused:  translated but never looked up (often a typo in one of the two)
  - placeholders: the German uses different {0}/{1} than the English
  - templates: a looked-up literal contains a Kotlin $template, so the key never matches
  - conflicts: the same English string translated differently in two area files
With --suspects it also lists UI-looking literals not wrapped in t() (heuristic, to find what's left).

Usage: python3 faden/i18n/check_german.py [--suspects] [path filter]
Exits non-zero if anything but suspects is found.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "app/src/main/java/io/github/radixtrator/resync"
I18N = ROOT / "i18n"

STR = r'"((?:[^"\\]|\\.)*)"'
T_CALL = re.compile(r'(?<![\w.])t\(\s*' + STR)
TN_CALL = re.compile(r'(?<![\w.])tn\(\s*[^,]+?,\s*' + STR + r'\s*,\s*' + STR, re.S)
ENTRY = re.compile(r'^\s*' + STR + r'\s+to\s+' + STR + r'\s*,?\s*$', re.M)
PLURAL = re.compile(r'^\s*' + STR + r'\s+to\s+\(\s*' + STR + r'\s+to\s+' + STR + r'\s*\)\s*,?\s*$', re.M)
PH = re.compile(r'\{\d+\}')
TEMPLATE = re.compile(r'(?<!\\)\$[{\w]')

SUSPECT = re.compile(
    r'(?:Text\(|title\s*=\s*|label\s*=\s*|contentDescription\s*=\s*|showMessage\(|Toast\.makeText\([^,]+,\s*|'
    r'headline\s*=\s*|summary\s*=\s*|placeholder\s*=\s*|setContentTitle\(|setContentText\()' + STR
)


def unescape(s):
    return s.replace('\\"', '"').replace('\\$', '$').replace('\\n', '\n').replace('\\\\', '\\')


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    only = args[0] if args else ""
    suspects = "--suspects" in sys.argv

    looked_up, plural_keys, problems = {}, {}, []
    for f in sorted(ROOT.rglob("*.kt")):
        if f.parent == I18N:
            continue
        rel = f.relative_to(ROOT)
        src = f.read_text(encoding="utf8")
        for m in T_CALL.finditer(src):
            key = m.group(1)
            if TEMPLATE.search(key):
                problems.append(f"templates: {rel}: t(\"{key}\")")
            looked_up.setdefault(unescape(key), str(rel))
        for m in TN_CALL.finditer(src):
            one, other = m.group(1), m.group(2)
            if TEMPLATE.search(one + other):
                problems.append(f"templates: {rel}: tn(…, \"{one}\", \"{other}\")")
            plural_keys.setdefault(unescape(other), (unescape(one), str(rel)))
        if suspects and str(rel).startswith(only):
            for i, line in enumerate(src.splitlines(), 1):
                for m in SUSPECT.finditer(line):
                    text = m.group(1)
                    if re.search(r'[A-Za-z]{2}', text) and not line.lstrip().startswith(("//", "*", "/*")):
                        print(f"suspect: {rel}:{i}: \"{text}\"")

    strings, plurals = {}, {}
    for f in sorted(I18N.glob("German*.kt")):
        src = f.read_text(encoding="utf8")
        for m in PLURAL.finditer(src):
            key = unescape(m.group(1))
            val = (unescape(m.group(2)), unescape(m.group(3)))
            if key in plurals and plurals[key][0] != val:
                problems.append(f"conflicts: plural \"{key}\": {plurals[key][1]} vs {f.name}")
            plurals[key] = (val, f.name)
        for m in ENTRY.finditer(src):
            key, val = unescape(m.group(1)), unescape(m.group(2))
            if key in strings and strings[key][0] != val:
                problems.append(f"conflicts: \"{key}\": \"{strings[key][0]}\" ({strings[key][1]}) vs \"{val}\" ({f.name})")
            strings[key] = (val, f.name)

    for key, where in looked_up.items():
        if key not in strings:
            problems.append(f"missing: {where}: \"{key}\"")
        elif sorted(PH.findall(key)) != sorted(PH.findall(strings[key][0])):
            problems.append(f"placeholders: \"{key}\" -> \"{strings[key][0]}\"")
    for key, (one, where) in plural_keys.items():
        if key not in plurals:
            problems.append(f"missing plural: {where}: \"{one}\" / \"{key}\"")
        else:
            de_one, de_other = plurals[key][0]
            if sorted(set(PH.findall(key))) != sorted(set(PH.findall(de_other))):
                problems.append(f"placeholders: plural \"{key}\" -> \"{de_other}\"")
    for key, (_, f) in strings.items():
        if key not in looked_up:
            problems.append(f"unused: {f}: \"{key}\"")
    for key, (_, f) in plurals.items():
        if key not in plural_keys:
            problems.append(f"unused plural: {f}: \"{key}\"")

    problems = [p for p in problems if only in p] if only else problems
    for p in sorted(problems):
        print(p)
    print(f"{len(looked_up)} strings, {len(plural_keys)} plurals looked up; "
          f"{len(strings)} strings, {len(plurals)} plurals translated; {len(problems)} problems", file=sys.stderr)
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()

"""Generates values-xx/strings.xml and plurals.xml from per-language tables, with checks.

Usage (from tools/i18n): python -I gen.py de fr es pt ru tr id_ sw hi bn ar ur zh ja
Each module defines FOLDER (e.g. "values-de"), STRINGS (dict) and PLURALS (dict of dicts).
"""
import importlib.util
import os
import re
import sys
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.normpath(os.path.join(HERE, '..', '..', 'android', 'app', 'src', 'main', 'res'))
PLACEHOLDER = re.compile(r'%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[sdfx%]')

# Keys whose English text may be reused as is (formats and symbols that read the same everywhere).
COPY_OK = {'cd_metric', 'cd_rating_bar', 'cd_recent_analysis', 'cd_timeline_step', 'cd_language_option',
           'backlog_rank', 'percent_value', 'pro_compare_pro', 'home_pro', 'list_separator', 'quote_format'}


def english():
    strings, plurals = {}, {}
    for e in ET.parse(os.path.join(RES, 'values', 'strings.xml')).getroot():
        if e.tag == 'string' and e.get('translatable') != 'false':
            strings[e.get('name')] = unescape(''.join(e.itertext()))
    for e in ET.parse(os.path.join(RES, 'values', 'plurals.xml')).getroot():
        plurals[e.get('name')] = {i.get('quantity'): unescape(i.text) for i in e}
    return strings, plurals


def unescape(text):
    return text.replace("\\'", "'").replace('\\"', '"').replace('\\n', '\n')


def escape(text):
    text = text.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
    text = text.replace('\\', '\\\\').replace("'", "\\'").replace('"', '\\"').replace('\n', '\\n')
    if text[:1] in ('@', '?'):
        text = '\\' + text
    if text != text.strip() or '  ' in text:
        text = '"' + text.replace('\\"', '"').replace('"', '\\"') + '"'
    return text


def placeholders(text):
    return sorted(m.group(0) for m in PLACEHOLDER.finditer(text) if m.group(0) != '%%')


def load(name):
    spec = importlib.util.spec_from_file_location(name, os.path.join(HERE, name + '.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def generate(name, en_strings, en_plurals):
    lang = load(name)
    problems = []
    for key in lang.STRINGS:
        if key not in en_strings:
            problems.append(f'unknown key {key}')
    lines = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>']
    for key, en_text in en_strings.items():
        text = lang.STRINGS.get(key)
        if text is None:
            if key in COPY_OK:
                text = en_text
            else:
                problems.append(f'missing {key}')
                continue
        if placeholders(text) != placeholders(en_text):
            problems.append(f'placeholders differ in {key}: {placeholders(text)} vs {placeholders(en_text)}')
        lines.append(f'    <string name="{key}">{escape(text)}</string>')
    lines.append('</resources>')

    plines = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>']
    for key, en_forms in en_plurals.items():
        forms = lang.PLURALS.get(key)
        if not forms:
            problems.append(f'missing plural {key}')
            continue
        if 'other' not in forms:
            problems.append(f'plural {key} has no "other"')
        expected = placeholders(en_forms['other'])
        plines.append(f'    <plurals name="{key}">')
        for quantity in ('zero', 'one', 'two', 'few', 'many', 'other'):
            if quantity in forms:
                found = placeholders(forms[quantity])
                # Languages such as Arabic spell out zero, one and two, so the number may be omitted there.
                if found != expected and not (quantity in ('zero', 'one', 'two') and not found):
                    problems.append(f'plural {key}/{quantity} placeholders differ')
                plines.append(f'        <item quantity="{quantity}">{escape(forms[quantity])}</item>')
        plines.append('    </plurals>')
    plines.append('</resources>')

    if problems:
        print(f'{name}: {len(problems)} problem(s)')
        for p in problems:
            print('   ', p)
        return False
    folder = os.path.join(RES, lang.FOLDER)
    os.makedirs(folder, exist_ok=True)
    open(os.path.join(folder, 'strings.xml'), 'w', encoding='utf8', newline='\n').write('\n'.join(lines) + '\n')
    open(os.path.join(folder, 'plurals.xml'), 'w', encoding='utf8', newline='\n').write('\n'.join(plines) + '\n')
    print(f'{name}: wrote {lang.FOLDER} ({len(en_strings)} strings, {len(en_plurals)} plurals)')
    return True


if __name__ == '__main__':
    s, p = english()
    ok = all([generate(n, s, p) for n in sys.argv[1:]])
    sys.exit(0 if ok else 1)

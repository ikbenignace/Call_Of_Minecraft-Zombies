#!/usr/bin/env python3
"""Checks compiled plugin classes against several spigot-api jars.

Reports every org.bukkit reference that would fail to link on a given server version: missing
classes, missing fields/methods, and calls compiled as a class call to a type that is an interface on
that version (or the reverse), which throw IncompatibleClassChangeError at runtime.

Usage: api_compat_scan.py <classes-dir> <api-jar>... [--skip <package-prefix>]... [--only <package-prefix>]...
Exits non-zero when any problem is found.
"""
import collections
import os
import struct
import sys
import zipfile

ENUM_METHODS = {'name', 'ordinal', 'compareTo', 'equals', 'hashCode', 'toString', 'getClass', 'valueOf',
                'getDeclaringClass', 'describeConstable', 'clone'}


def parse(data):
    pos = 10
    count = struct.unpack('>H', data[8:10])[0]
    cp = [None] * count
    i = 1
    while i < count:
        tag = data[pos]
        if tag == 1:
            ln = struct.unpack('>H', data[pos + 1:pos + 3])[0]
            cp[i] = ('utf8', data[pos + 3:pos + 3 + ln].decode('utf-8', 'replace'))
            pos += 3 + ln
        elif tag in (3, 4):
            pos += 5
        elif tag in (5, 6):
            pos += 9
            i += 1
        elif tag in (7, 8, 16, 19, 20):
            cp[i] = (tag, struct.unpack('>H', data[pos + 1:pos + 3])[0])
            pos += 3
        elif tag in (9, 10, 11, 12, 17, 18):
            cp[i] = (tag, struct.unpack('>HH', data[pos + 1:pos + 5]))
            pos += 5
        elif tag == 15:
            pos += 4
        else:
            raise ValueError('unknown constant pool tag %d' % tag)
        i += 1

    def utf(k):
        return cp[k][1]

    def cls(k):
        return utf(cp[k][1])

    access, this, sup, nif = struct.unpack('>HHHH', data[pos:pos + 8])
    pos += 8
    ifaces = [cls(struct.unpack('>H', data[pos + 2 * j:pos + 2 * j + 2])[0]) for j in range(nif)]
    pos += 2 * nif

    def members(pos):
        n = struct.unpack('>H', data[pos:pos + 2])[0]
        pos += 2
        out = []
        for _ in range(n):
            _, ni, di, ac = struct.unpack('>HHHH', data[pos:pos + 8])
            pos += 8
            for _ in range(ac):
                ln = struct.unpack('>I', data[pos + 2:pos + 6])[0]
                pos += 6 + ln
            out.append((utf(ni), utf(di)))
        return out, pos

    fields, pos = members(pos)
    methods, pos = members(pos)
    refs = []
    for e in cp:
        if e and e[0] in (9, 10, 11):
            c, nt = e[1]
            n, d = cp[nt][1]
            refs.append(({9: 'F', 10: 'M', 11: 'IM'}[e[0]], cls(c), utf(n), utf(d)))
        elif e and e[0] == 7:
            refs.append(('C', utf(e[1]), None, None))
    return dict(name=cls(this), access=access, super=cls(sup) if sup else None, ifaces=ifaces,
                fields=fields, methods=methods, refs=refs)


class Api:
    def __init__(self, jar):
        self.zip = zipfile.ZipFile(jar)
        self.cache = {}

    def get(self, name):
        if name not in self.cache:
            try:
                self.cache[name] = parse(self.zip.read(name + '.class'))
            except KeyError:
                self.cache[name] = None
        return self.cache[name]

    def has_member(self, owner, name, desc, kind):
        c = self.get(owner)
        if c is None:
            # Inherited from java.lang.Enum / Object, which aren't in the api jar.
            return kind != 'F' and not owner.startswith('org/') and name in ENUM_METHODS
        members = c['fields'] if kind == 'F' else c['methods']
        if any(n == name and (kind == 'F' or d == desc) for n, d in members):
            return True
        parents = ([c['super']] if c['super'] else []) + c['ifaces']
        return any(p and self.has_member(p, name, desc, kind) for p in parents)


def main(argv):
    skips = []
    onlys = []
    args = []
    it = iter(argv)
    for a in it:
        if a == '--skip':
            skips.append(next(it))
        elif a == '--only':
            onlys.append(next(it))
        else:
            args.append(a)
    classes_dir, jars = args[0], args[1:]

    usage = collections.defaultdict(set)
    for root, _, files in os.walk(classes_dir):
        for f in files:
            if not f.endswith('.class'):
                continue
            c = parse(open(os.path.join(root, f), 'rb').read())
            if any(c['name'].startswith(s) for s in skips):
                continue
            if onlys and not any(c['name'].startswith(s) for s in onlys):
                continue
            for r in c['refs']:
                if r[1].startswith('org/bukkit/'):
                    usage[r].add(c['name'].split('/')[-1])

    problems = 0
    for jar in jars:
        api = Api(jar)
        for r in sorted(usage, key=lambda x: (x[1], x[2] or '')):
            kind, owner, name, desc = r
            c = api.get(owner)
            if c is None:
                problem = 'missing class'
            elif kind == 'C':
                continue
            else:
                is_iface = bool(c['access'] & 0x200)
                problem = None
                if kind == 'M' and is_iface:
                    problem = 'class call on interface'
                elif kind == 'IM' and not is_iface:
                    problem = 'interface call on class'
                if not api.has_member(owner, name, desc, 'F' if kind == 'F' else 'M'):
                    problem = (problem + ', ' if problem else '') + 'missing member'
            if problem:
                problems += 1
                print('%s: %s %s.%s%s <- %s' % (os.path.basename(jar), problem, owner, name or '', desc or '',
                                                 ', '.join(sorted(usage[r]))))
    print('%d problem(s)' % problems)
    return 1 if problems else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))

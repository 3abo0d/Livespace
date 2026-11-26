"""Relocates the app resources of the 16.1 SystemUI.apk from package id 0x7f to 0x7e (and renames the package),
so they can be added next to the device's own SystemUI resources without clashing. Pure python, no aapt needed."""
import struct, zipfile, re

OLD_ID, NEW_ID = 0x7f, 0x7e
REF_TYPES = (0x01, 0x02, 0x07, 0x08)  # reference, attribute, dynamic reference, dynamic attribute
def u16(b, o): return struct.unpack_from('<H', b, o)[0]
def u32(b, o): return struct.unpack_from('<I', b, o)[0]

def fix(b, o, stats=None):
    v = u32(b, o)
    if v >> 24 == OLD_ID:
        struct.pack_into('<I', b, o, (NEW_ID << 24) | (v & 0xffffff))
        if stats is not None: stats['refs'] += 1

def packages(b):
    o = u16(b, 2)  # table header size
    while o + 8 <= len(b):
        t, hs, sz = struct.unpack_from('<HHI', b, o)
        if sz < 8: break
        if t == 0x0200: yield o, hs, sz
        o += sz

def arsc_ids(arsc):
    """All resource ids (0x7fttee) declared in the table (from the type-spec chunks)."""
    b = bytes(arsc); ids = set()
    for po, hs, sz in packages(b):
        pid = u32(b, po + 8)
        if pid != OLD_ID: continue
        o = po + hs
        while o + 8 <= po + sz:
            t, chs, csz = struct.unpack_from('<HHI', b, o)
            if csz < 8: break
            if t == 0x0202:
                tid = b[o + 8]; n = u32(b, o + 12)
                ids.update((pid << 24) | (tid << 16) | i for i in range(n))
            o += csz
    return ids

def relocate_arsc(arsc):
    b = bytearray(arsc); stats = {'refs': 0, 'pkgs': 0}
    for po, hs, sz in packages(b):
        if u32(b, po + 8) != OLD_ID: continue
        struct.pack_into('<I', b, po + 8, NEW_ID); stats['pkgs'] += 1
        name = 'com.livespace.res'.encode('utf-16-le'); b[po + 12:po + 12 + 256] = name + b'\0' * (256 - len(name))
        o = po + hs
        while o + 8 <= po + sz:
            t, chs, csz = struct.unpack_from('<HHI', b, o)
            if csz < 8: break
            if t == 0x0201:
                flags = b[o + 9]; n = u32(b, o + 12); estart = u32(b, o + 16); arr = o + chs
                for i in range(n):
                    if flags & 2:  # FLAG_OFFSET16: u16 offsets in 4-byte units
                        v = u16(b, arr + 2 * i)
                        if v == 0xFFFF: continue
                        off = v * 4
                    else:
                        v = u32(b, arr + 4 * i)
                        if v == 0xFFFFFFFF: continue
                        off = (v >> 16) * 4 if flags & 1 else v
                    e = o + estart + off
                    eflags = u16(b, e + 2)
                    if eflags & 0x8:  # compact entry: data type in high byte of flags, data at +4
                        if (eflags >> 8) in REF_TYPES: fix(b, e + 4, stats)
                    elif eflags & 0x1:  # bag
                        fix(b, e + 8, stats)
                        cnt = u32(b, e + 12)
                        for k in range(cnt):
                            it = e + 16 + 12 * k
                            fix(b, it, stats)
                            if b[it + 7] in REF_TYPES: fix(b, it + 8, stats)
                    else:
                        if b[e + 11] in REF_TYPES: fix(b, e + 12, stats)
            o += csz
    return bytes(b), stats

def relocate_xml(x, stats):
    if len(x) < 8 or u16(x, 0) != 0x0003: return x
    b = bytearray(x); o = 8
    while o + 8 <= len(b):
        t, hs, sz = struct.unpack_from('<HHI', b, o)
        if sz < 8: break
        if t == 0x0180:
            for i in range(o + 8, o + sz, 4): fix(b, i, stats)
        elif t == 0x0102:
            astart = u16(b, o + 24); asize = u16(b, o + 26); cnt = u16(b, o + 28); base = o + 16 + astart
            for k in range(cnt):
                a = base + k * asize
                if b[a + 15] in REF_TYPES: fix(b, a + 16, stats)
        o += sz
    return bytes(b)

def build_res_apk(src_apk, out_apk):
    stats = {'refs': 0, 'pkgs': 0, 'xml': 0, 'files': 0}
    with zipfile.ZipFile(src_apk) as z, zipfile.ZipFile(out_apk, 'w') as o:
        arsc, st = relocate_arsc(z.read('resources.arsc')); stats['refs'] += st['refs']; stats['pkgs'] = st['pkgs']
        o.writestr(zipfile.ZipInfo('resources.arsc'), arsc, compress_type=zipfile.ZIP_STORED)
        for n in z.namelist():
            if not n.startswith('res/') or n.endswith('/'): continue
            data = z.read(n)
            if n.endswith('.xml'): data = relocate_xml(data, stats); stats['xml'] += 1
            o.writestr(n, data, compress_type=zipfile.ZIP_DEFLATED); stats['files'] += 1
    return stats

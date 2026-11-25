#!/usr/bin/env python3
"""Builds capsule.dex: the 16.1 lockscreen capsule classes (and everything they need that 16.0.x lacks),
patched so they run on the 16.0.x SystemUI:
  * calls to members that do not exist in the device's SystemUI are redirected to adapters (Adapters.java)
    or to harmless default stubs (so a missing method can never crash SystemUI),
  * R8 'ExternalSyntheticOutline' helper classes that differ between builds are copied under ls/o/.
Usage: make_capsule_dex.py --new SystemUI_16.1.apk --old <device SystemUI.apk> --apktool apktool.jar [--work DIR] [--out capsule.dex]
Needs: python3, java, ~2 GB free storage. Nothing from the APKs is uploaded anywhere."""
import argparse, glob, os, re, shutil, struct, subprocess, sys, zipfile, collections

PK = 'Lcom/oplus/systemui/notification/lockscreen/'
SHIM = 'Lcom/livespace/shim/Adapters;'
STUBS = 'Lls/Stubs;'
FIELD_DEFAULTS = {'isNotificationCapsuleMetaBallEnable': 'true', 'USE_OPLUS_PHYSICAL': 'true'}
ADAPT_STATIC = {  # (class, name+proto) -> (adapter name, adapter return type or None=same)
 ('Lcom/android/systemui/statusbar/notification/AnimatableProperty;',
  'from(Ljava/lang/String;Ljava/util/function/BiConsumer;Ljava/util/function/Function;III)Lcom/android/systemui/statusbar/notification/AnimatableProperty$6;'): 'animatableFrom'}
ADAPT_CTOR = {  # (class, ctor sig) -> (old sig, arg order over regs incl. this)
 ('Lcom/coui/appcompat/animation/dynamicanimation/COUISpringAnimation;',
  '<init>(Ljava/lang/Object;Landroidx/dynamicanimation/animation/FloatPropertyCompat;F)V'):
 ('<init>(Landroidx/dynamicanimation/animation/FloatPropertyCompat;Ljava/lang/Object;)V', [0, 2, 1])}

def log(*a): print(*a, flush=True)
def uleb(d, o):
    r = 0; s = 0
    while True:
        b = d[o]; o += 1; r |= (b & 0x7f) << s; s += 7
        if not b & 0x80: return r, o

def parse(path, T, members=True):
    d = open(path, 'rb').read(); g = lambda off: struct.unpack_from('<I', d, off)[0]
    soff = g(60); tsz, toff = g(64), g(68); poff = g(76); foff = g(84); moff = g(92); csz, coff = g(96), g(100)
    def string(i):
        o = g(soff + 4 * i); _, o = uleb(d, o); return d[o:d.index(b'\0', o)].decode('utf8', 'replace')
    types = [string(g(toff + 4 * i)) for i in range(tsz)]
    def proto(i):
        _, rt, po = struct.unpack_from('<III', d, poff + 12 * i); ps = ''
        if po: ps = ''.join(types[struct.unpack_from('<H', d, po + 4 + 2 * k)[0]] for k in range(g(po)))
        return '(' + ps + ')' + types[rt]
    def mid(i):
        c, p, n = struct.unpack_from('<HHI', d, moff + 8 * i); return types[c], string(n) + proto(p)
    def fid(i):
        c, t, n = struct.unpack_from('<HHI', d, foff + 8 * i); return types[c], string(n) + ':' + types[t]
    for i in range(csz):
        ci, acc, sup, ifo, _, _, cdo, _ = struct.unpack_from('<IIIIIIII', d, coff + 32 * i)
        e = T.setdefault(types[ci], {'m': set(), 'f': set(), 's': None, 'i': [], 'dex': os.path.basename(path)})
        if not members: continue
        e['s'] = types[sup] if sup != 0xffffffff else None
        if ifo: e['i'] = [types[struct.unpack_from('<H', d, ifo + 4 + 2 * k)[0]] for k in range(g(ifo))]
        if cdo:
            o = cdo; sf, o = uleb(d, o); inf, o = uleb(d, o); dm, o = uleb(d, o); vm, o = uleb(d, o)
            for cnt in (sf, inf):
                idx = 0
                for _ in range(cnt):
                    dl, o = uleb(d, o); _, o = uleb(d, o); idx += dl; e['f'].add(fid(idx)[1])
            for cnt in (dm, vm):
                idx = 0
                for _ in range(cnt):
                    dl, o = uleb(d, o); _, o = uleb(d, o); _, o = uleb(d, o); idx += dl; e['m'].add(mid(idx)[1])
    return d, (g(88), g(80), mid, fid)

def refs_of(path):
    d, (msz, fsz, mid, fid) = parse(path, {}, members=False)
    return [mid(i) for i in range(msz)], [fid(i) for i in range(fsz)]

OBJ_M = {'<init>()V', 'getClass()Ljava/lang/Class;', 'hashCode()I', 'equals(Ljava/lang/Object;)Z', 'toString()Ljava/lang/String;',
         'notify()V', 'notifyAll()V', 'wait()V', 'wait(J)V', 'clone()Ljava/lang/Object;', 'finalize()V'}
OLD = {}
def has(cls, kind, sig, seen=None):
    seen = seen if seen is not None else set()
    if cls in seen: return True
    seen.add(cls)
    if cls == 'Ljava/lang/Object;': return kind == 'm' and sig in OBJ_M
    e = OLD.get(cls)
    if e is None: return True  # framework class, cannot check
    if sig in e[kind]: return True
    if e['s'] and has(e['s'], kind, sig, seen): return True
    return any(has(i, kind, sig, seen) for i in e['i'])

def split_params(proto):
    ps = proto[1:proto.index(')')]; out = []; i = 0
    while i < len(ps):
        j = i
        while ps[j] == '[': j += 1
        j = ps.index(';', j) + 1 if ps[j] == 'L' else j + 1
        out.append(ps[i:j]); i = j
    return out

def stub_body(ret):
    if ret == 'V': return '    return-void'
    if ret in 'ZBSCIF': return '    const/4 v0, 0x0\n    return v0'
    if ret in 'JD': return '    const-wide/16 v0, 0x0\n    return-wide v0'
    return '    const/4 v0, 0x0\n    return-object v0'

INV = re.compile(r'^(\s*)(invoke-(?:virtual|interface|static|direct|super))(/range)? (\{[^}]*\}), (L[^;]+;)->(\S+?)(\(.*\)\S+)\s*$')
FLD = re.compile(r'^(\s*)([is](?:get|put)\S*) (.+), (L[^;]+;)->(\S+):(\S+)\s*$')
TOK = re.compile(r'L[A-Za-z0-9_/$]+;')

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--new', required=True); ap.add_argument('--old', required=True); ap.add_argument('--apktool', required=True)
    ap.add_argument('--work', default='ls_work'); ap.add_argument('--out', default='capsule.dex'); ap.add_argument('--xmx', default='1500m')
    a = ap.parse_args(); W = os.path.abspath(a.work); os.makedirs(W, exist_ok=True)
    for tag, apk in (('old', a.old), ('new', a.new)):
        dd = os.path.join(W, tag); os.makedirs(dd, exist_ok=True)
        with zipfile.ZipFile(apk) as z:
            for n in z.namelist():
                if re.fullmatch(r'classes\d*\.dex', n) or n == 'AndroidManifest.xml':
                    if not os.path.exists(os.path.join(dd, n)): z.extract(n, dd)
    log('parsing device (old) dex tables ...')
    for f in sorted(glob.glob(os.path.join(W, 'old', 'classes*.dex'))): parse(f, OLD)
    NEWMAP = {}
    for f in sorted(glob.glob(os.path.join(W, 'new', 'classes*.dex'))):
        t = {}; parse(f, t, members=False)
        for k in t: NEWMAP[k] = os.path.basename(f)
    log('device classes', len(OLD), '16.1 classes', len(NEWMAP))
    idx = {}; decoded = set()
    def decode(dexname):
        if dexname in decoded: return
        decoded.add(dexname); base = dexname[:-4]
        cands = [os.path.join(W, 'dec_' + base, 'smali'), os.path.join(W, 'dec_new_' + base, 'smali')]
        root = next((c for c in cands if os.path.isdir(c)), None)
        if not root:
            out = os.path.join(W, 'dec_' + base); sub = os.path.join(W, 'sub_' + base + '.apk')
            with zipfile.ZipFile(sub, 'w', zipfile.ZIP_DEFLATED) as z:
                z.write(os.path.join(W, 'new', dexname), 'classes.dex'); z.write(os.path.join(W, 'new', 'AndroidManifest.xml'), 'AndroidManifest.xml')
            log('disassembling', dexname, '...')
            r = subprocess.run(['java', '-Xmx' + a.xmx, '-jar', a.apktool, 'd', '-r', '-f', '-o', out, sub], capture_output=True, text=True)
            os.remove(sub); root = os.path.join(out, 'smali')
            if not os.path.isdir(root): sys.exit('apktool failed: ' + r.stdout[-500:] + r.stderr[-500:])
        for dp, _, fs in os.walk(root):
            for f in fs:
                if f.endswith('.smali'):
                    p = os.path.join(dp, f); idx['L' + os.path.relpath(p, root)[:-6] + ';'] = p
    def ensure(desc):
        if desc not in idx and desc in NEWMAP: decode(NEWMAP[desc])
        return idx.get(desc)
    seed = [c for c in NEWMAP if c.startswith(PK)]
    for dx in sorted(set(NEWMAP[c] for c in seed)): decode(dx)
    texts = {}; queue = list(seed); reloc = set()
    log('computing closure ...')
    while queue:
        c = queue.pop()
        if c in texts: continue
        p = ensure(c)
        if not p: log('  no smali for', c); continue
        t = open(p, errors='ignore').read(); texts[c] = t
        for m in (INV.match(l) for l in t.split('\n')):
            if m and m.group(5) in OLD and 'ExternalSyntheticOutline' in m.group(5) and not has(m.group(5), 'm', m.group(6) + m.group(7)):
                reloc.add(m.group(5))
        for tk in set(TOK.findall(t)):
            if tk not in OLD and tk in NEWMAP and tk not in texts: queue.append(tk)
    for r in reloc: ensure(r)
    log('closure', len(texts), 'classes; relocated helper classes', len(reloc))
    stubs = collections.OrderedDict(); sfields = collections.OrderedDict(); report = collections.Counter(); needs_cast = None
    def stub_for(kind, T, name, proto):
        ps = split_params(proto); ret = proto[proto.index(')') + 1:]
        sproto = '(' + ('' if kind == 'static' else T) + ''.join(ps) + ')' + ret
        k = (T, name, proto, kind)
        if k not in stubs: stubs[k] = ('s%d' % len(stubs), sproto, ret)
        return stubs[k][0], stubs[k][1]
    def patch(text):
        out = []; cast = None
        for line in text.split('\n'):
            if cast and re.match(r'\s*move-result-object (\S+)', line):
                out.append(line); out.append('    check-cast %s, %s' % (re.match(r'\s*move-result-object (\S+)', line).group(1), cast)); cast = None; continue
            m = INV.match(line)
            if m:
                ind, op, rng, regs, T, name, proto = m.groups(); sig = name + proto
                if T in OLD and not has(T, 'm', sig) and T not in reloc:
                    kind = op.split('-')[1]
                    if (T, sig) in ADAPT_STATIC and kind == 'static':
                        ret = proto[proto.index(')') + 1:]
                        out.append('%s%s%s %s, %s->%s%s' % (ind, op, rng or '', regs, SHIM, ADAPT_STATIC[(T, sig)], proto[:proto.index(')') + 1] + 'Ljava/lang/Object;'))
                        cast = ret; report['adapted ' + sig.split('(')[0]] += 1; continue
                    if (T, sig) in ADAPT_CTOR and not rng:
                        osig, order = ADAPT_CTOR[(T, sig)]; rl = [r.strip() for r in regs.strip('{}').split(',')]
                        out.append('%s%s {%s}, %s->%s' % (ind, op, ', '.join(rl[i] for i in order), T, osig)); report['adapted ctor ' + T.split('/')[-1]] += 1; continue
                    if name == '<init>' or kind == 'super':
                        report['UNPATCHED ' + kind + ' ' + T.split('/')[-1] + '.' + name] += 1; out.append(line); continue
                    sname, sproto = stub_for('static' if kind == 'static' else 'inst', T, name, proto)
                    out.append('%sinvoke-static%s %s, %s->%s%s' % (ind, rng or '', regs, STUBS, sname, sproto)); report['stubbed'] += 1; continue
            m = FLD.match(line)
            if m:
                ind, op, rest, T, name, ty = m.groups()
                if T in OLD and not has(T, 'f', name + ':' + ty) and T not in reloc:
                    if op.startswith('s'):
                        sfields[(name, ty)] = FIELD_DEFAULTS.get(name); out.append('%s%s %s, %s->%s:%s' % (ind, op, rest, STUBS, name, ty)); report['stub field'] += 1; continue
                    report['UNPATCHED instance field ' + T.split('/')[-1] + '.' + name] += 1
            out.append(line)
        return '\n'.join(out)
    B = os.path.join(W, 'asm'); shutil.rmtree(B, ignore_errors=True); os.makedirs(os.path.join(B, 'smali'))
    def rel(d): return d[1:-1] + '.smali'
    def subst(t):
        for r in reloc: t = t.replace(r, 'Lls/o/' + r[1:])
        return t
    for c, t in texts.items():
        p = os.path.join(B, 'smali', rel(c)); os.makedirs(os.path.dirname(p), exist_ok=True)
        open(p, 'w').write(subst(patch(t)))
    for r in reloc:
        p = os.path.join(B, 'smali', 'ls', 'o', rel(r)); os.makedirs(os.path.dirname(p), exist_ok=True)
        open(p, 'w').write(subst(open(idx[r], errors='ignore').read()))
    s = ['.class public ' + STUBS[:-1] + ';', '.super Ljava/lang/Object;', '']
    for (name, ty), dv in sfields.items():
        init = ''
        if ty == 'Z': init = ' = ' + (dv or 'false')
        elif ty in 'BSCIJFD': init = ' = 0x0'
        s.append('.field public static %s:%s%s' % (name, ty, init))
    for (T, name, proto, kind), (sn, sproto, ret) in stubs.items():
        s += ['', '.method public static %s%s' % (sn, sproto), '    .locals 2', stub_body(ret), '.end method']
    p = os.path.join(B, 'smali', 'ls', 'Stubs.smali'); os.makedirs(os.path.dirname(p), exist_ok=True); open(p, 'w').write('\n'.join(s) + '\n')
    log('patch report:'); [log('  ', k, v) for k, v in sorted(report.items())]
    # assemble with apktool: reuse an apktool.yml + manifest from any decoded dir
    src = next(os.path.join(W, d) for d in os.listdir(W) if d.startswith('dec_') and os.path.exists(os.path.join(W, d, 'apktool.yml')))
    shutil.copy(os.path.join(src, 'apktool.yml'), B); shutil.copy(os.path.join(W, 'new', 'AndroidManifest.xml'), B)
    log('assembling ...')
    apk = os.path.join(W, 'capsule_tmp.apk')
    r = subprocess.run(['java', '-Xmx' + a.xmx, '-jar', a.apktool, 'b', '-f', '-o', apk, B], capture_output=True, text=True)
    if not os.path.exists(apk): sys.exit('apktool build failed:\n' + r.stdout[-1500:] + r.stderr[-1500:])
    with zipfile.ZipFile(apk) as z: open(a.out, 'wb').write(z.read('classes.dex'))
    # verification: every member reference inside the produced dex that points at a device class must resolve
    ms, fs = refs_of(a.out); bad = collections.Counter()
    for T, sig in ms:
        if T in OLD and not has(T, 'm', sig): bad[T.split('/')[-1] + '.' + sig.split('(')[0]] += 1
    for T, sig in fs:
        if T in OLD and not has(T, 'f', sig): bad['field ' + T.split('/')[-1] + '.' + sig] += 1
    log('wrote', a.out, os.path.getsize(a.out), 'bytes; unresolved references left:', len(bad))
    for k, v in sorted(bad.items())[:40]: log('   LEFT', k)
if __name__ == '__main__': main()

#!/usr/bin/env python3
"""Port the device-tested v4 + dispatcher fix to a matching Android NFC source tree."""
import argparse
import difflib
from pathlib import Path
import re
import sys

POLL_MARKER='KALILOCK_LOCKED_SCREEN_POLL'
DISPATCH_MARKER='KALILOCK_LOCKED_SCREEN_DISPATCH'

def transform(service, dispatcher):
    if POLL_MARKER not in service:
        pattern=r'(\bvoid\s+applyScreenState\s*\(\s*int\s+(\w+)\s*\)\s*\{)'
        matches=list(re.finditer(pattern,service))
        if len(matches)!=1: raise ValueError('applyScreenState(int) nicht eindeutig')
        m=matches[0];var=m[2]
        code=f'''\n        // {POLL_MARKER}: keep existing lockscreen handlers active with display off.
        if ({var} == ScreenStateHelper.SCREEN_STATE_OFF_LOCKED
                && mNfcUnlockManager.isLockscreenPollingEnabled()) {{
            {var} = ScreenStateHelper.SCREEN_STATE_ON_LOCKED;
        }}
'''
        service=service[:m.end()]+code+service[m.end():]
    if DISPATCH_MARKER not in dispatcher:
        # The dispatcher has its own physical screen-state check, independent of NfcService.
        pattern=r'(?P<expr>(?:this\.)?mScreenStateHelper\.checkScreenState\(\s*(?:this\.)?mDeviceConfigFacade\.getCheckDisplayStateForScreenState\(\)\))\s*(?P<op>!=|==)\s*(?P<const>ScreenStateHelper\.SCREEN_STATE_ON_LOCKED|SCREEN_STATE_ON_LOCKED|4)'
        matches=list(re.finditer(pattern,dispatcher))
        if len(matches)!=1: raise ValueError('Dispatcher-Screen-State-Prüfung nicht eindeutig')
        m=matches[0]
        # Avoid two physical state reads: named helper evaluates its int argument once.
        replacement=('!' if m['op']=='!=' else '')+'isKaliLockLockedScreen('+m['expr']+')'
        dispatcher=dispatcher[:m.start()]+replacement+dispatcher[m.end():]
        pos=dispatcher.rfind('}')
        if pos<0: raise ValueError('Dispatcher-Klassenende fehlt')
        helper=f'''\n    // {DISPATCH_MARKER}: only route to existing handlers; never authorize by UID.
    private static boolean isKaliLockLockedScreen(int state) {{
        return state == ScreenStateHelper.SCREEN_STATE_ON_LOCKED
                || state == ScreenStateHelper.SCREEN_STATE_OFF_LOCKED;
    }}
'''
        dispatcher=dispatcher[:pos]+helper+dispatcher[pos:]
    return service,dispatcher

def main():
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument('source',type=Path,help='LineageOS-Root oder NFC-Modul-Verzeichnis')
    ap.add_argument('--check',action='store_true')
    ap.add_argument('--diff',action='store_true')
    args=ap.parse_args()
    candidates=[args.source/'packages/modules/Nfc/NfcNci/src/com/android/nfc',args.source/'NfcNci/src/com/android/nfc',args.source/'src/com/android/nfc',args.source]
    found=[p for p in candidates if (p/'NfcService.java').is_file() and (p/'NfcDispatcher.java').is_file()]
    found=list(dict.fromkeys(p.resolve() for p in found))
    if len(found)!=1: sys.exit('NFC-Quellpaar nicht eindeutig gefunden; nichts geändert')
    paths=[found[0]/'NfcService.java',found[0]/'NfcDispatcher.java']
    old=[p.read_text() for p in paths]
    try:new=transform(*old)
    except ValueError as e:sys.exit(str(e)+'; nichts geändert')
    changed=[(p,a,b) for p,a,b in zip(paths,old,new) if a!=b]
    if args.diff:
        for p,a,b in changed: print(''.join(difflib.unified_diff(a.splitlines(True),b.splitlines(True),fromfile=p.name,tofile=p.name)),end='')
    if not changed: print('Bereits angewendet');return
    # Validate the entire pair and all backup destinations before any write.
    for p,_,_ in changed:
        if not args.check and p.with_suffix('.java.kalilock-original').exists():sys.exit('Original-Backup existiert bereits; nichts geändert: '+str(p))
    if args.check: print('PASSEND: beide NFC-Patches geprüft; keine Änderung');return
    for p,a,b in changed:
        p.with_suffix('.java.kalilock-original').write_text(a)
        p.write_text(b)
    print('Beide Patches angewendet. NFC-APEX im ROM neu bauen und mit den ROM-Schlüsseln signieren.')

if __name__=='__main__':main()

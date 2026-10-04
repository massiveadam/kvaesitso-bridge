#!/usr/bin/env python3
"""Exercise the installed APK and real widget on a disposable emulator, using only adb."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

APP = 'com.adamdelisi.kvaesitsobridge'
FIXTURE = 'com.adamdelisi.bridgefixture'
LISTENER = APP + '/.notification.BridgeNotificationListener'

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True, help='Explicit disposable emulator serial')
parser.add_argument('--adb', default='adb')
parser.add_argument('--evidence', default='.verification/device')
parser.add_argument('--extended-only', action='store_true', help='Run extra lifecycle, ongoing, fallback and appearance checks after the basic run')
parser.add_argument('--from-privacy', action='store_true', help='Continue with two active fixture messages, without resetting')
parser.add_argument('--release-smoke', action='store_true', help='Verify startup, rendering, opening and dismissal in an installed minified APK')
parser.add_argument('--source-uninstall', action='store_true', help='Uninstall the observed fixture and verify app rules are removed (run last, with debug Bridge)')
args = parser.parse_args()
if not args.serial.startswith('emulator-'):
    parser.error('This destructive test resets Bridge settings. Use a disposable emulator only.')
out = Path(args.evidence); out.mkdir(parents=True, exist_ok=True)
results = json.loads((out / 'results.json').read_text()) if (args.from_privacy or args.extended_only) and (out / 'results.json').exists() else []

def adb(*parts, binary=False):
    r = subprocess.run([args.adb, '-s', args.serial, *map(str, parts)], check=True, capture_output=True)
    return r.stdout if binary else r.stdout.decode(errors='replace')

def shell(*parts):
    return adb('shell', *parts)

def start(package=APP, **extras):
    component = package + ('/.FixtureActivity' if package == FIXTURE else '/.MainActivity')
    command = ['am', 'start', '-W', '-n', component, '-f', '0x14000000' if package == FIXTURE else '0x10008000']
    for key, value in extras.items():
        kind = '--ez' if isinstance(value, bool) else '--ei' if isinstance(value, int) else '--es'
        command += [kind, key, str(value).lower() if isinstance(value, bool) else str(value)]
    # adb shell re-parses arguments. Quote them so message text remains one value.
    import shlex
    shell(shlex.join(command))

def tree():
    shell('uiautomator', 'dump', '/sdcard/bridge-window.xml')
    raw = shell('cat', '/sdcard/bridge-window.xml')
    return ET.fromstring(raw)

def wait_text(text, present=True, timeout=15):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        root = tree()
        # Google APIs emulator boot can ANR its own launcher over the test host.
        # Record that environment failure; never dismiss a Bridge/fixture ANR.
        if any(n.get('text') == "Pixel Launcher isn't responding" for n in root.iter('node')):
            ET.ElementTree(root).write(out / 'pixel-launcher-anr.xml', encoding='utf-8')
            (out / 'pixel-launcher-anr.png').write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
            close = next(n for n in root.iter('node') if n.get('resource-id') == 'android:id/aerr_close')
            x1, y1, x2, y2 = map(int, re.findall(r'\d+', close.get('bounds')))
            shell('input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
            print('Environment: dismissed recorded Pixel Launcher ANR', flush=True)
            continue
        found = any(text in n.get('text', '') or text in n.get('content-desc', '') for n in root.iter('node'))
        if found == present:
            return root
        time.sleep(.3)
    raise AssertionError(f'{text!r} present={present} was not observed')

def tap(text, description=False, app=None):
    field = 'content-desc' if description else 'text'
    nodes = []
    for attempt in range(6):
        root = tree()
        nodes = [n for n in root.iter('node') if n.get(field) == text]
        if app is not None:
            anchors = [n for n in root.iter('node') if n.get('text') == app]
            if anchors:
                anchor_y = int(re.findall(r'\d+', anchors[0].get('bounds'))[3])
                nodes = [n for n in nodes if int(re.findall(r'\d+', n.get('bounds'))[1]) >= anchor_y]
            else:
                nodes = []
        if nodes:
            break
        shell('input', 'swipe', '540', '1800', '540', '700', '300')
    if not nodes:
        raise AssertionError(f'No exact {field}: {text}')
    n = nodes[0]
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
    shell('input', 'tap', str((x1+x2)//2), str((y1+y2)//2))

def capture(name):
    (out / (name + '.png')).write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
    root = tree()
    ET.ElementTree(root).write(out / (name + '.xml'), encoding='utf-8')
    if name not in results:
        results.append(name)
    (out / 'results.json').write_text(json.dumps(results, indent=2) + '\n')
    print('PASS', name, flush=True)

def post(id=1, **extras):
    start(FIXTURE, action='post', id=id, **extras)

def host():
    start(FIXTURE)

# Doctor. Never select a device implicitly.
assert shell('getprop', 'sys.boot_completed').strip() == '1', 'Android is not booted'
assert 'versionName=0.1.0' in shell('dumpsys', 'package', APP), 'Install the Bridge debug APK first'
assert 'versionName=1' in shell('dumpsys', 'package', FIXTURE), 'Install the optional fixture APK first'
(out / 'doctor.txt').write_text(shell('getprop', 'ro.build.version.release') + shell('getprop', 'ro.build.version.sdk'))
if args.source_uninstall:
    start(); tap('Apps'); wait_text('Bridge test messages'); capture('source-before-uninstall')
    adb('uninstall', FIXTURE)
    start(); tap('Apps'); wait_text('Bridge test messages', present=False)
    deadline = time.monotonic() + 10
    while time.monotonic() < deadline:
        prefs = adb('exec-out', 'run-as', APP, 'cat', 'files/datastore/bridge_settings.preferences_pb', binary=True)
        if FIXTURE.encode() not in prefs:
            break
        time.sleep(.2)
    else:
        raise AssertionError('Uninstalled source remains in persisted app rules')
    capture('source-uninstalled-settings-cleared')
    print('Completed source uninstall check. Evidence:', out, flush=True)
    raise SystemExit(0)
if args.release_smoke:
    shell('logcat', '-c')
    shell('pm', 'clear', APP)
    shell('cmd', 'notification', 'allow_listener', LISTENER)
    start(); wait_text('Notification access enabled'); capture('release-startup')
    start(FIXTURE, action='clear')
    host()
    post(id=40, title='Release', text='Optimized notification opens', category='msg')
    wait_text('Release: Optimized notification opens'); capture('release-widget')
    tap('Release: Optimized notification opens')
    wait_text('Notification opened: 40'); capture('release-content-intent')
    host(); wait_text('Optimized notification opens', present=False)
    post(id=41, title='Release', text='Dismiss this actual notification', category='msg')
    wait_text('Release: Dismiss this actual notification')
    tap('Dismiss notification from Bridge test messages', description=True)
    wait_text('Dismiss this actual notification', present=False); capture('release-dismissed')
    log = shell('logcat', '-d', '-b', 'crash')
    assert APP not in log, 'Bridge crashed during minified release smoke test'
    (out / 'release-crash-log.txt').write_text(log)
    print('Completed minified release smoke test. Evidence:', out, flush=True)
    raise SystemExit(0)
if not args.from_privacy and not args.extended_only:
    shell('pm', 'clear', APP)
    shell('pm', 'clear', FIXTURE)
    shell('pm', 'grant', FIXTURE, 'android.permission.POST_NOTIFICATIONS')
    shell('appwidget', 'grantbind', '--package', FIXTURE, '--user', '0')
    shell('cmd', 'notification', 'disallow_listener', LISTENER)
    start(); wait_text('Notification access required'); capture('permission-missing-app')
    host(); wait_text('Tap to enable'); capture('permission-missing-widget')
    # Exercise the actual Settings button, then use Android's shell helper to grant on this test device.
    tap('Notification access required')
    wait_text('Enable notification access')
    tap('Enable notification access')
    capture('android-access-settings')
    shell('cmd', 'notification', 'allow_listener', LISTENER)
    start(); wait_text('Notification access enabled'); wait_text('0 visible notifications'); capture('permission-granted-empty')
    host(); wait_text('Notification access required', present=False); capture('empty-widget')
    post(title='Jess', text='Updated deck is ready', category='msg')
    wait_text('Jess: Updated deck is ready'); capture('one-notification-widget')
    tap('Jess: Updated deck is ready'); wait_text('Notification opened: 1'); capture('content-intent-opened')
    host(); wait_text('Jess: Updated deck is ready', present=False)
    post(id=2, title='Meg', text='grabbing dinner now', category='msg')
    post(id=3, title='Jess', text='Updated deck is ready', category='msg')
    wait_text('Meg: grabbing dinner now'); wait_text('Jess: Updated deck is ready'); capture('multiple-notifications-widget')
if not args.extended_only:
    start(); tap('Apps'); wait_text('Bridge test messages'); tap('App only', app='Bridge test messages')
    host(); wait_text('Bridge test messages'); wait_text('Updated deck is ready', present=False); capture('app-only-widget')
    start(); tap('Apps'); tap('Hidden', app='Bridge test messages')
    host(); wait_text('All notification apps are hidden'); capture('hidden-app-widget')
    start(); tap('Apps'); tap('Full', app='Bridge test messages')
    host(); wait_text('Jess: Updated deck is ready')
    tap('Dismiss notification from Bridge test messages', description=True)
    wait_text('Jess: Updated deck is ready', present=False); wait_text('Meg: grabbing dinner now'); capture('dismissed-widget')
    # Update the same Android id; the old text must disappear, not accumulate.
    post(id=2, title='Meg', text='Dinner at seven', category='msg')
    wait_text('Meg: Dinner at seven'); wait_text('grabbing dinner now', present=False); capture('notification-updated')
    post(id=4, title='VPN', text='Connected', category='service', ongoing=True)
    wait_text('VPN: Connected', present=False); capture('ongoing-filtered')
    # Group summary with two children should not duplicate the children's content.
    post(id=5, title='Group aggregate', text='Two messages', group='team', summary=True, category='msg')
    post(id=6, title='Kai', text='First child', group='team', category='msg')
    post(id=7, title='Rae', text='Second child', group='team', category='msg')
    wait_text('First child'); wait_text('Second child'); wait_text('Group aggregate', present=False); capture('group-children-widget')
    # Permission revocation must remove content with the settings app closed.
    shell('cmd', 'notification', 'disallow_listener', LISTENER)
    host(); wait_text('Tap to enable'); wait_text('Dinner at seven', present=False); capture('permission-revoked-widget')
    shell('cmd', 'notification', 'allow_listener', LISTENER)
    host(); wait_text('Dinner at seven'); capture('listener-reconnected-widget')
    # Android dismiss/removal, not a repository test hook.
    start(FIXTURE, action='clear')
    wait_text('Dinner at seven', present=False); capture('notifications-cleared-widget')
if args.extended_only:
    def tap_switch(title, app=None, value=True):
        root = tree()
        labels = [n for n in root.iter('node') if n.get('text') == title]
        if app is not None:
            anchor = next(n for n in root.iter('node') if n.get('text') == app)
            ay = int(re.findall(r'\d+', anchor.get('bounds'))[3])
            labels = [n for n in labels if int(re.findall(r'\d+', n.get('bounds'))[1]) >= ay]
        label = labels[0]
        ly = int(re.findall(r'\d+', label.get('bounds'))[1])
        switches = [n for n in root.iter('node') if n.get('checkable') == 'true']
        switch = min(switches, key=lambda n: abs(int(re.findall(r'\d+', n.get('bounds'))[1]) - ly))
        if (switch.get('checked') == 'true') == value:
            return
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', switch.get('bounds')))
        shell('input', 'tap', str((x1+x2)//2), str((y1+y2)//2))

    start(FIXTURE, action='clear')
    post(id=10, category='msg')
    wait_text('Bridge test messages'); wait_text('null', present=False); capture('null-content-widget')
    shell('logcat', '-c')
    post(id=11, title='Fallback', text='Open app', category='msg', noIntent=True)
    wait_text('Fallback: Open app'); tap('Fallback: Open app')
    wait_text('Android widget host')
    # Wait for the internal activity to finish and Android's launch log to prove the fallback.
    deadline = time.monotonic() + 10
    while time.monotonic() < deadline:
        log = shell('logcat', '-d', '-s', 'ActivityTaskManager')
        if 'act=android.intent.action.MAIN' in log and FIXTURE in log:
            break
        time.sleep(.2)
    else:
        raise AssertionError('PackageManager app-launch fallback was not observed')
    (out / 'fallback-launch.txt').write_text(log)
    capture('app-launch-fallback')
    start(); tap('Notifications'); tap_switch('Allow ongoing notifications')
    start(); tap('Apps'); tap_switch('Include background status', app='Bridge test messages')
    post(id=12, title='VPN', text='Connected', category='service', ongoing=True)
    root = wait_text('VPN: Connected')
    dismiss = [n for n in root.iter('node') if n.get('content-desc') == 'Dismiss notification from Bridge test messages']
    assert len(dismiss) == 2, 'Only the two clearable rows should have dismiss controls'
    capture('ongoing-override-nonclearable')
    start(FIXTURE, action='clear')
    start(); tap('Notifications'); tap_switch('Allow ongoing notifications', value=False)
    start(); tap('Apps'); tap_switch('Include background status', app='Bridge test messages', value=False)
    post(id=13, title='Chat', text='Before restart', category='msg')
    shell("cmd notification post -t Mail bridge-mail 'Second app'")
    wait_text('Chat: Before restart'); wait_text('Mail: Second app'); capture('multiple-apps-widget')
    # Kill the background app process as its own debug UID, without force-stopping the package.
    pid = shell('pidof', APP).strip().split()[0]
    shell('run-as', APP, 'kill', '-9', pid)
    post(id=14, title='Chat', text='After restart', category='msg')
    wait_text('Chat: Before restart'); wait_text('Chat: After restart'); capture('process-recreated-widget')
    # Same signature and version, installed through Android's real replacement path.
    (out / 'app-replacement.txt').write_text(adb('install', '-r', 'app/build/outputs/apk/debug/app-debug.apk'))
    host(); wait_text('Chat: After restart'); capture('app-replaced-widget')
    adb('reboot')
    deadline = time.monotonic() + 180
    while time.monotonic() < deadline:
        try:
            if shell('getprop', 'sys.boot_completed').strip() == '1':
                break
        except subprocess.CalledProcessError:
            pass
        time.sleep(1)
    else:
        raise AssertionError('Emulator failed to reboot')
    shell('input', 'keyevent', '82')
    host(); wait_text('Before restart', present=False); wait_text('After restart', present=False)
    capture('reboot-no-stale-content')
    post(id=15, title='Meg', text='A message after reboot', category='msg')
    wait_text('Meg: A message after reboot'); capture('notification-after-reboot')
    shell('cmd', 'uimode', 'night', 'yes')
    host(); wait_text('Meg: A message after reboot'); capture('widget-dark')
    shell('settings', 'put', 'system', 'font_scale', '1.8')
    post(id=16, title='Jess', text='Larger type in the same widget', category='msg')
    wait_text('Jess: Larger type in the same widget'); capture('widget-large-font')
    shell('settings', 'put', 'system', 'font_scale', '1.0')
    shell('cmd', 'uimode', 'night', 'no')
    start(FIXTURE, action='clear')
print('Completed', len(results), 'checks. Evidence:', out, flush=True)

"""CI-only: install real dedicated servers and test packaged jars over two boots."""
import json
import pathlib
import queue
import shutil
import subprocess
import sys
import threading
import time
import urllib.request

loader = sys.argv[1]
root = pathlib.Path.cwd()
work = root / loader / 'build' / 'smoke'
work.mkdir(parents=True, exist_ok=True)

urls = {
    'fabric': 'https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.0.1/fabric-installer-1.0.1.jar',
    'forge': 'https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.2.0/forge-1.20.1-47.2.0-installer.jar',
    'neoforge': 'https://maven.neoforged.net/releases/net/neoforged/forge/1.20.1-47.1.106/forge-1.20.1-47.1.106-installer.jar',
}
urllib.request.urlretrieve(urls[loader], work / 'installer.jar')
args = ['server', '-mcversion', '1.20.1', '-loader', '0.15.11', '-downloadMinecraft'] if loader == 'fabric' else ['--installServer']
subprocess.run(['java', '-jar', 'installer.jar', *args], cwd=work, check=True, timeout=240)
(work / 'mods').mkdir(exist_ok=True)
jar = next((root / loader / 'build' / 'libs').glob('playguard-*.jar'))
shutil.copy(jar, work / 'mods' / jar.name)
if loader == 'fabric':
    urllib.request.urlretrieve('https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.92.2+1.20.1/fabric-api-0.92.2+1.20.1.jar', work / 'mods' / 'fabric-api.jar')
    command = ['java', '-Xmx1G', '-jar', 'fabric-server-launch.jar', 'nogui']
else:
    args_file = next((work / 'libraries').glob('net/*/forge/*/unix_args.txt'))
    command = ['java', '-Xmx1G', '@' + str(args_file.relative_to(work)), 'nogui']
(work / 'eula.txt').write_text('eula=true\n')
(work / 'server.properties').write_text('online-mode=false\nlevel-type=minecraft:flat\nview-distance=2\nsimulation-distance=2\nmax-tick-time=-1\n')

def boot():
    process = subprocess.Popen(command, cwd=work, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                               stderr=subprocess.STDOUT, text=True)
    lines = queue.Queue()
    def consume():
        for line in process.stdout:
            print(line, end='', flush=True)
            lines.put(line)
    thread = threading.Thread(target=consume, daemon=True)
    thread.start()
    deadline = time.monotonic() + 180
    active = ready = False
    try:
        while time.monotonic() < deadline:
            try:
                line = lines.get(timeout=1)
            except queue.Empty:
                if process.poll() is not None:
                    raise RuntimeError('Server exited before startup')
                continue
            active |= 'PlayGuard active:' in line
            ready |= 'Done (' in line
            if active and ready:
                break
        assert active and ready, 'Server startup timed out or mod did not load'
        process.stdin.write('stop\n')
        process.stdin.flush()
        process.wait(timeout=60)
        assert process.returncode == 0, f'Server exit code {process.returncode}'
    finally:
        if process.poll() is None:
            process.kill()
            process.wait()
        thread.join(timeout=10)

boot()
own = '11111111-1111-4111-8111-111111111111'
manual = '22222222-2222-4222-8222-222222222222'
def ban(uuid, name, source, reason):
    return dict(uuid=uuid, name=name, created='2026-01-01 00:00:00 +0000', source=source,
                expires='forever', reason=reason)
bans = [ban(own, 'RestTest', '[playguard:temporary-rest]', '[PlayGuard] 游玩时间已满，请休息后再回来。'),
        ban(manual, 'ManualTest', 'Server', 'Manual moderation')]
(work / 'banned-players.json').write_text(json.dumps(bans, ensure_ascii=False), encoding='utf-8')
state_path = work / 'world' / 'playguard-state.json'
state_path.write_text(json.dumps({'players': {own: {'name': 'RestTest', 'clock': {'playedMillis': 10800000},
                                                  'bannedUntil': 4102444800000}}}), encoding='utf-8')
boot()
remaining = json.loads((work / 'banned-players.json').read_text())
assert [b['uuid'] for b in remaining] == [manual], remaining
state = json.loads(state_path.read_text())['players'][own]
assert state['bannedUntil'] == 0 and state['clock']['playedMillis'] == 0, state
print('PASS: production jar loads; restart clears only PlayGuard bans and resets their counters.')

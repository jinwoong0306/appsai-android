"""Destructive UI test: clears app data. Run on a disposable emulator only."""
import subprocess, os, time, xml.etree.ElementTree as ET, re
adb=os.path.join(os.environ['LOCALAPPDATA'],'Android','Sdk','platform-tools','adb.exe')
def run(*args): return subprocess.run([adb,*args],capture_output=True,text=True,encoding='utf-8',errors='replace',check=True).stdout
def shell(*args): return run('shell',*args)
def ui():
    shell('uiautomator','dump','/sdcard/check.xml')
    return ET.fromstring(shell('cat','/sdcard/check.xml'))
def click_text(text):
    root=ui(); nodes=[n for n in root.iter('node') if (n.get('text') or '').casefold()==text.casefold()]
    assert nodes, f'missing {text}; seen: {[n.get("text") for n in root.iter("node") if n.get("text")]}'
    a,b,c,d=map(int,re.findall(r'\d+',nodes[0].get('bounds'))); shell('input','tap',str((a+c)//2),str((b+d)//2))
def texts():return [n.get('text') for n in ui().iter('node') if n.get('text')]
pkg='kr.example.haliltalk'
shell('pm','clear',pkg);shell('appops','set',pkg,'GET_USAGE_STATS','ignore');shell('am','start','-n',pkg+'/.MainActivity');time.sleep(1)
click_text('＋ 대화 상대 추가'); ts=texts(); assert 'Chrome' in ts; print('PASS app list includes Chrome')
root=ui(); label=next(n for n in root.iter('node') if n.get('text')=='Chrome'); row=next(n for n in root.iter('node') if label in list(n)); plus=next(n for n in row if n.get('text')=='＋');a,b,c,d=map(int,re.findall(r'\d+',plus.get('bounds')));shell('input','tap',str((a+c)//2),str((b+d)//2))
click_text('장난기 많은 친구'); assert any('안녕!' in x for x in texts()); print('PASS friend created')
click_text('오늘 사용 시간 확인'); assert any('권한이 필요' in x for x in texts()); click_text('나중에'); print('PASS permission denied handled')
shell('appops','set',pkg,'GET_USAGE_STATS','allow');click_text('오늘 사용 시간 확인');assert any('사용 시간: 약' in x for x in texts());click_text('확인');print('PASS permission granted usage visible')
click_text('메시지 보내기');shell('input','text','hello');shell('input','keyevent','4');click_text('전송');ts=texts();assert '나: hello' in ts and any('고마워' in t for t in ts);print('PASS message and reply')
click_text('← 대화 목록');assert any('경험치 1' in t for t in texts());shell('am','force-stop',pkg);shell('am','start','-n',pkg+'/.MainActivity');time.sleep(1);assert any('경험치 1' in t for t in texts());click_text('💬 Chrome   ·   장난기 많은 친구   ·   경험치 1');assert '나: hello' in texts();print('PASS chat and XP persist after restart')
click_text('← 대화 목록');click_text('＋ 대화 상대 추가');root=ui();label=next(n for n in root.iter('node') if n.get('text')=='Chrome');row=next(n for n in root.iter('node') if label in list(n));plus=next(n for n in row if n.get('text')=='＋');a,b,c,d=map(int,re.findall(r'\d+',plus.get('bounds')));shell('input','tap',str((a+c)//2),str((b+d)//2));click_text('다정한 친구');click_text('← 대화 목록');assert len([t for t in texts() if 'chrome' in t.lower()])==1;print('PASS duplicate friend not created')

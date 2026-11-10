import tempfile, pathlib, subprocess, os
with tempfile.TemporaryDirectory() as tmp:
 p=pathlib.Path(tmp);settings=p/'settings';value=p/'value';marker=p/'active'
 settings.write_text('''#!/bin/sh
case "$1" in
 get) cat "$TEST_VALUE" ;;
 put) [ "$TEST_FAIL" != 1 ] || exit 1; [ "$TEST_IGNORE" = 1 ] || printf '%s' "$4" > "$TEST_VALUE" ;;
 delete) [ "$TEST_FAIL" != 1 ] || exit 1; printf null > "$TEST_VALUE" ;;
esac
''');settings.chmod(0o700)
 env=dict(os.environ,PATH=tmp+':'+os.environ['PATH'],TEST_VALUE=str(value))
 def run(old,current,fail=False,ignore=False):
  value.write_text(current);marker.write_text('pending')
  script=subprocess.check_output(['java','-cp',os.environ['B8_TEST_CLASSES'],'org.duofold.live.experimental.b8.DesktopRestoreHarness',str(marker),old],text=True)
  return subprocess.run(['sh'],input=script,text=True,env=dict(env,TEST_FAIL=str(int(fail)),TEST_IGNORE=str(int(ignore))),capture_output=True)
 assert run('0','1').returncode==0 and value.read_text()=='0' and not marker.exists()
 assert run('null','1').returncode==0 and value.read_text()=='null' and not marker.exists()
 assert run('null','0').returncode==0 and value.read_text()=='0' and not marker.exists()
 assert run('0','1',fail=True).returncode!=0 and marker.exists()
 assert run('0','1',ignore=True).returncode!=0 and marker.exists()
 assert run('1','1').returncode==0 and value.read_text()=='1'
 print('Desktop recovery: original disabled/enabled/unset, external change, failed write/readback passed')

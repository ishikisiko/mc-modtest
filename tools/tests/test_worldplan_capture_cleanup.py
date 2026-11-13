"""启动失败后，采集器必须清理另立进程组的自有子进程。不会启动Minecraft。"""
from pathlib import Path
import json,os,signal,subprocess,sys,tempfile,time,unittest
from unittest.mock import patch
from tools.worldplan import acceptance
from tools.combat_capture import procs

class CaptureCleanupTest(unittest.TestCase):
    def test_orphan_in_a_separate_process_group_is_owned_and_terminated(self):
        procs.set_child_subreaper()
        with tempfile.TemporaryDirectory(prefix='quest-capture-cleanup-') as directory:
            root=Path(directory);pidfile=root/'child.pid'
            program='import subprocess,sys,time;from pathlib import Path;p=subprocess.Popen([sys.executable,"-c","import time;time.sleep(60)"],start_new_session=True);Path(sys.argv[1]).write_text(str(p.pid));time.sleep(60)'
            server=acceptance.Server('capture_cleanup_test','fixture');server.backups={}
            parent=subprocess.Popen([sys.executable,'-c',program,str(pidfile)],start_new_session=True);server.proc=parent
            try:
                deadline=time.monotonic()+5
                while not pidfile.exists() and time.monotonic()<deadline:time.sleep(.02)
                self.assertTrue(pidfile.exists());child=int(pidfile.read_text());server.remember()
                self.assertIn(child,server.identities);self.assertNotEqual(os.getpgid(parent.pid),os.getpgid(child))
                os.kill(parent.pid,signal.SIGTERM);parent.wait(timeout=5)
                with patch.object(acceptance,'OUT',root):server.__exit__(None,None,None)
                proof=json.loads((root/'fixture-cleanup.json').read_text())
                self.assertTrue(proof['terminated']);self.assertEqual([],proof['owned_survivors']);self.assertFalse(procs.is_alive(child,server.identities[child]))
            finally:
                procs.kill_pids([(pid,start,'cleanup test owned process') for pid,start in server.identities.items()],grace=1)
                if parent.poll() is None:parent.terminate();parent.wait(timeout=5)

if __name__=='__main__':unittest.main()

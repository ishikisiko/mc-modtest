"""用轻量进程验证测试清理的目录归属，不运行 Java 或 Minecraft。"""
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from tools.travel_talisman_smoke.run import finish_game_jvms, game_jvms


class TravelCleanupTest(unittest.TestCase):
    def test_cleanup_stops_only_java_named_processes_in_its_own_game_directory(self):
        with tempfile.TemporaryDirectory() as root:
            own = Path(root) / 'own'; own.mkdir()
            foreign = Path(root) / 'foreign'; foreign.mkdir()
            processes = []
            try:
                for directory, name in [(own, 'java'), (foreign, 'java'), (own, 'python')]:
                    script = "import ctypes,os,sys,time;ctypes.CDLL(None).prctl(15,sys.argv[2].encode(),0,0,0);os.chdir(sys.argv[1]);print('ready',flush=True);time.sleep(300)"
                    process = subprocess.Popen([sys.executable, '-c', script, str(directory), name], stdout=subprocess.PIPE, text=True)
                    processes.append(process)
                    self.assertEqual('ready', process.stdout.readline().strip())
                self.assertEqual({processes[0].pid}, set(game_jvms(own)))
                result = finish_game_jvms(own)
                self.assertEqual([processes[0].pid], result['owned_jvms'])
                self.assertEqual([], result['remaining_jvms'])
                self.assertEqual(-15, processes[0].wait(timeout=5))
                self.assertIsNone(processes[1].poll())
                self.assertIsNone(processes[2].poll())
            finally:
                for process in processes:
                    if process.poll() is None:
                        process.terminate()
                    process.wait(timeout=5)
                    process.stdout.close()


if __name__ == '__main__':
    unittest.main()

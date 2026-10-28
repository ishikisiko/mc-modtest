"""通用风格加载器必须独立识别州城屋顶，不依赖导出器的导入顺序。"""
from pathlib import Path
import subprocess
import sys
import unittest


ROOT = Path(__file__).resolve().parents[2]


class ProvinceCapitalRoofRegistrationTest(unittest.TestCase):
    def test_generic_style_consumer_loads_ximo_in_a_fresh_process(self):
        # 独立解释器防止其他测试提前导入区域导出器，掩盖正式构建的失败。
        code = """
import sys
sys.path.insert(0, sys.argv[1])
from buildgen.style import load_style
from buildgen.ops import roof_handler
style = load_style("province_capital_ximo", available_namespaces={"minecraft"})
assert style.allowed_roof_types == ["province_oasis_terrace"]
assert callable(roof_handler(style.allowed_roof_types[0]))
"""
        result = subprocess.run(
            [sys.executable, "-I", "-c", code, str(ROOT / "tools")],
            cwd=ROOT, capture_output=True, text=True, check=False,
        )
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()

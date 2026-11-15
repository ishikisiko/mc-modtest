"""python3 -m tools.immortal_equipment build|check"""
import argparse
import sys

from .exporter import ROOT, write_or_check


def main():
    parser = argparse.ArgumentParser(description="从已确认五境衣甲作者网格生成游戏资源")
    parser.add_argument("command", choices=("build", "check"))
    parser.add_argument("--rename-namespace-from", help="显式重命名命名空间时，仍逐项核对原物品路径均被保留")
    args = parser.parse_args()
    try:
        changed = write_or_check(ROOT, check=args.command == "check", rename_namespace_from=args.rename_namespace_from)
    except (ValueError, RuntimeError) as error:
        parser.exit(1, str(error) + "\n")
    if args.command == "check" and changed:
        print("生成资源存在漂移：\n" + "\n".join(changed), file=sys.stderr)
        return 1
    print(f"五境十五套：75 个物品 ID；{'检查通过' if args.command == 'check' else f'更新 {len(changed)} 个文件'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

import argparse
import json
import sys
from pathlib import Path


if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")


MODULE_ROOT = Path(__file__).resolve().parent
PROJECT_ROOT = MODULE_ROOT.parent
LOCAL_PACKAGES = MODULE_ROOT / ".packages"
if LOCAL_PACKAGES.exists():
    sys.path.insert(0, str(LOCAL_PACKAGES))
sys.path.insert(0, str(MODULE_ROOT))

from cleaning.pipeline import analyze  # noqa: E402
from data_access.mysql_reader import DatabaseConfigurationError, ReadOnlyMySqlReader  # noqa: E402
from reporting import write_outputs  # noqa: E402


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="FitMind只读数据质量检查与清洗预览")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--check", action="store_true", help="只读检查并在控制台输出汇总")
    mode.add_argument("--clean-preview", action="store_true", help="生成清洗预览CSV，不写数据库")
    mode.add_argument("--report", action="store_true", help="生成Markdown、JSON、问题清单和清洗预览")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        data = ReadOnlyMySqlReader().load_all()
        result = analyze(PROJECT_ROOT, data)
        if args.clean_preview:
            output_dir = MODULE_ROOT / "reports"
            output_dir.mkdir(parents=True, exist_ok=True)
            preview_path = output_dir / "meal_clean_preview.csv"
            result["preview"].to_csv(preview_path, index=False, encoding="utf-8-sig")
            print("清洗预览已生成：{}".format(preview_path))
        elif args.report:
            paths = write_outputs(MODULE_ROOT / "reports", result, include_preview=True)
            for name, path in paths.items():
                print("{}: {}".format(name, path))
        else:
            print(json.dumps(result["summary"], ensure_ascii=False, indent=2, default=str))
        return 0
    except DatabaseConfigurationError as exception:
        print(str(exception), file=sys.stderr)
        return 2
    except Exception as exception:
        print("数据检查失败：{}".format(exception), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())

import json
from datetime import datetime
from pathlib import Path
from typing import Dict, Iterable

import pandas as pd


def _markdown_rows(headers: Iterable[str], rows: Iterable[Iterable[object]]) -> str:
    header_list = list(headers)
    lines = [
        "| " + " | ".join(header_list) + " |",
        "| " + " | ".join("---" for _ in header_list) + " |",
    ]
    for row in rows:
        lines.append("| " + " | ".join(str(value).replace("|", "\\|") for value in row) + " |")
    return "\n".join(lines)


def write_outputs(output_dir: Path, result: Dict[str, object], include_preview: bool = True) -> Dict[str, Path]:
    output_dir.mkdir(parents=True, exist_ok=True)
    summary = result["summary"]
    issues: pd.DataFrame = result["issues"]
    preview: pd.DataFrame = result["preview"]
    schema_findings = result["schema_findings"]

    json_path = output_dir / "data_quality_report.json"
    issues_path = output_dir / "data_quality_issues.csv"
    markdown_path = output_dir / "data_quality_report.md"
    preview_path = output_dir / "meal_clean_preview.csv"
    schema_path = output_dir / "schema_findings.csv"

    payload = {
        "generated_at": datetime.now().isoformat(timespec="seconds"),
        "summary": summary,
        "schema_findings": schema_findings,
        "issues": issues.to_dict(orient="records") if not issues.empty else [],
    }
    json_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2, default=str), encoding="utf-8")
    issues.to_csv(issues_path, index=False, encoding="utf-8-sig")
    pd.DataFrame(schema_findings).to_csv(schema_path, index=False, encoding="utf-8-sig")
    if include_preview:
        preview.to_csv(preview_path, index=False, encoding="utf-8-sig")

    quality = summary["quality"]
    schema_review = [item for item in schema_findings if item["status"] != "KEEP"]
    issue_counts = quality["issue_counts"]
    markdown = [
        "# FitMind 数据质量只读检查报告",
        "",
        "生成时间：{}".format(payload["generated_at"]),
        "",
        "> 本报告来自当前 fitmind 数据库。工具以只读事务运行，没有删除、更新或补造任何正式数据。",
        "",
        "## 1. 数据规模与质量",
        "",
        _markdown_rows(
            ["数据集", "总行数", "正常行", "异常行", "关键字段缺失行"],
            [
                ["体重", summary["database_rows"]["weight_record"], quality["weight"]["normal_rows"], quality["weight"]["abnormal_rows"], quality["weight"]["missing_required_rows"]],
                ["饮食", summary["database_rows"]["meal_record"], quality["meal"]["normal_rows"], quality["meal"]["abnormal_rows"], quality["meal"]["missing_required_rows"]],
            ],
        ),
        "",
        "日期覆盖缺口：体重{}天，饮食整日{}天；另有{}个早/中/晚餐次未记录。餐次缺口无法区分‘未进食’与‘未填写’，因此只作为信息提示。".format(
            quality["missing_weight_dates"], quality["missing_meal_dates"], quality["meal_slots_not_recorded"]
        ),
        "",
        "## 2. 异常类型统计",
        "",
        _markdown_rows(["异常或提示类型", "数量"], issue_counts.items()) if issue_counts else "当前未发现异常或覆盖提示。",
        "",
        "## 3. 清洗预览",
        "",
        "- 食物记录：{}条".format(summary["normalization_preview"]["source_rows"]),
        "- 标准化预览发生名称变化：{}条".format(summary["normalization_preview"]["food_names_changed"]),
        "- 份量中识别出明确质量单位：{}条".format(summary["normalization_preview"]["explicit_mass_values_detected"]),
        "- 正式数据库修改：0条",
        "",
        "原始食物名始终保留在 `food_name_original`，标准名只写入预览CSV。斤转kg使用2:1，kg转g使用1000:1，并使用Decimal避免二进制浮点误差。",
        "",
        "## 4. AI图片识别数据",
        "",
        "当前 `source_type=AI_IMAGE` 的已确认入库记录为{}条，其中异常记录{}条。样本为空时不计算准确率或异常率。".format(
            summary["ai_image_records"]["record_count"], summary["ai_image_records"]["abnormal_record_count"]
        ),
        "",
        "## 5. 数据库字段与Java Entity差异",
        "",
        "`meal_record.is_estimated` 与当前 `estimated` 同时非空但取值不一致的记录为{}条；旧 `calories` 与 `calories_kcal` 同时非空且冲突的记录为{}条。".format(
            summary["schema"]["meal_legacy_estimated_conflicts"], summary["schema"]["meal_legacy_calorie_conflicts"]
        ),
        "",
        _markdown_rows(
            ["表", "字段/索引", "状态", "非空数", "建议"],
            ([item["table"], item["column"], item["status"], item["populated_count"], item["suggestion"]] for item in schema_review),
        ) if schema_review else "数据库字段与Java Entity完全一致。",
        "",
        "## 6. 处理原则",
        "",
        "- 异常只标记原因和建议，不自动删除。",
        "- 缺失日期和字段不自动插值。",
        "- AI结果仍须用户确认后由Java后端入库。",
        "- 如需写回数据库，应另建带人工确认、审计记录和事务回滚的流程。",
        "",
    ]
    markdown_path.write_text("\n".join(markdown), encoding="utf-8")
    paths = {"json": json_path, "issues": issues_path, "schema": schema_path, "markdown": markdown_path}
    if include_preview:
        paths["preview"] = preview_path
    return paths

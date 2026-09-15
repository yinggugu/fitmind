from collections import Counter
from pathlib import Path
from typing import Dict, List, Tuple

import pandas as pd

from normalization.preview import build_meal_preview
from validation.issues import DataIssue
from validation.meal_validator import validate_meals
from validation.schema_validator import compare_schema
from validation.weight_validator import validate_weights


def _affected_row_count(issues: List[DataIssue], dataset: str, severities: Tuple[str, ...]) -> int:
    return len({
        issue.row_id for issue in issues
        if issue.dataset == dataset and issue.severity in severities and issue.row_id.isdigit()
    })


def analyze(project_root: Path, data: Dict[str, pd.DataFrame]) -> Dict[str, object]:
    weights = data["weight_record"]
    meals = data["meal_record"]
    weight_issues = validate_weights(weights)
    meal_issues = validate_meals(meals)
    issues = weight_issues + meal_issues
    issue_rows = [issue.to_dict() for issue in issues]
    issues_frame = pd.DataFrame(issue_rows)
    preview, normalization_changes = build_meal_preview(meals)
    schema_findings = compare_schema(
        project_root, data["schema_columns"], data["schema_indexes"], data
    )
    estimated_conflicts = 0
    calorie_conflicts = 0
    if {"is_estimated", "estimated"}.issubset(meals.columns):
        comparable = meals["is_estimated"].notna() & meals["estimated"].notna()
        estimated_conflicts = int(
            (meals.loc[comparable, "is_estimated"].astype(int)
             != meals.loc[comparable, "estimated"].astype(int)).sum()
        )
    if {"calories", "calories_kcal"}.issubset(meals.columns):
        comparable = meals["calories"].notna() & meals["calories_kcal"].notna()
        calorie_conflicts = int(
            (meals.loc[comparable, "calories"] != meals.loc[comparable, "calories_kcal"]).sum()
        )

    abnormal_weight_rows = _affected_row_count(issues, "weight_record", ("ERROR", "WARNING"))
    abnormal_meal_rows = _affected_row_count(issues, "meal_record", ("ERROR", "WARNING"))
    missing_weight_rows = len({
        issue.row_id for issue in issues
        if issue.dataset == "weight_record" and issue.issue_type == "MISSING_REQUIRED_FIELD"
        and issue.row_id.isdigit()
    })
    missing_meal_rows = len({
        issue.row_id for issue in issues
        if issue.dataset == "meal_record" and issue.issue_type == "MISSING_REQUIRED_FIELD"
        and issue.row_id.isdigit()
    })
    issue_counts = dict(sorted(Counter(issue.issue_type for issue in issues).items()))
    severity_counts = dict(sorted(Counter(issue.severity for issue in issues).items()))

    ai_meals = meals[meals["source_type"] == "AI_IMAGE"] if "source_type" in meals else meals.iloc[0:0]
    ai_ids = {str(value) for value in ai_meals.get("id", pd.Series(dtype=object)).tolist()}
    ai_abnormal_ids = {
        issue.row_id for issue in meal_issues
        if issue.row_id in ai_ids and issue.severity in ("ERROR", "WARNING")
    }

    summary = {
        "database_rows": {
            "user_profile": len(data["user_profile"]),
            "weight_record": len(weights),
            "meal_record": len(meals),
            "body_measurement": len(data["body_measurement"]),
            "daily_health_letter": len(data["daily_health_letter"]),
        },
        "quality": {
            "weight": {
                "normal_rows": len(weights) - abnormal_weight_rows,
                "abnormal_rows": abnormal_weight_rows,
                "missing_required_rows": missing_weight_rows,
            },
            "meal": {
                "normal_rows": len(meals) - abnormal_meal_rows,
                "abnormal_rows": abnormal_meal_rows,
                "missing_required_rows": missing_meal_rows,
            },
            "issue_counts": issue_counts,
            "severity_counts": severity_counts,
            "missing_weight_dates": issue_counts.get("MISSING_WEIGHT_DATE", 0),
            "missing_meal_dates": issue_counts.get("MISSING_MEAL_DATE", 0),
            "meal_slots_not_recorded": issue_counts.get("MEAL_SLOT_NOT_RECORDED", 0),
        },
        "normalization_preview": {
            "source_rows": len(meals),
            "food_names_changed": normalization_changes,
            "explicit_mass_values_detected": int(preview["normalized_mass_g"].notna().sum()) if not preview.empty else 0,
            "database_rows_modified": 0,
        },
        "ai_image_records": {
            "record_count": len(ai_meals),
            "abnormal_record_count": len(ai_abnormal_ids),
            "accuracy_rate": None,
            "note": "仅统计source_type=AI_IMAGE的已确认入库记录；无样本时不计算比例。",
        },
        "schema": {
            "keep_columns": sum(1 for item in schema_findings if item["status"] == "KEEP"),
            "db_only_review_columns": sum(1 for item in schema_findings if item["status"] == "DB_ONLY_REVIEW"),
            "historical_empty_candidates": sum(1 for item in schema_findings if item["status"] == "HISTORICAL_EMPTY_CANDIDATE"),
            "entity_only_missing_columns": sum(1 for item in schema_findings if item["status"] == "ENTITY_ONLY_MIGRATION_REQUIRED"),
            "type_review_columns": sum(1 for item in schema_findings if item["status"] == "TYPE_REVIEW"),
            "index_review_items": sum(1 for item in schema_findings if item["status"] == "INDEX_REVIEW"),
            "meal_legacy_estimated_conflicts": estimated_conflicts,
            "meal_legacy_calorie_conflicts": calorie_conflicts,
        },
        "safety": {
            "transaction_mode": "READ ONLY",
            "database_writes": 0,
            "automatic_imputation": False,
            "automatic_deletion": False,
        },
    }
    return {
        "summary": summary,
        "issues": issues_frame,
        "preview": preview,
        "schema_findings": schema_findings,
    }

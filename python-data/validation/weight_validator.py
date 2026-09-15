from datetime import date
from decimal import Decimal
from typing import List

import pandas as pd

from validation.common import is_missing, to_decimal
from validation.issues import DataIssue


def validate_weights(weights: pd.DataFrame) -> List[DataIssue]:
    issues: List[DataIssue] = []
    if weights.empty:
        issues.append(DataIssue(
            "weight_record", "dataset", "NO_WEIGHT_DATA", "WARNING", "record_date",
            "体重表没有记录。", "先由用户正常记录，不自动补造历史体重。",
        ))
        return issues

    required = ("user_id", "record_date", "weight_kg")
    for _, row in weights.iterrows():
        row_id = str(row.get("id", "unknown"))
        for field in required:
            if field not in weights.columns or is_missing(row.get(field)):
                issues.append(DataIssue(
                    "weight_record", row_id, "MISSING_REQUIRED_FIELD", "ERROR", field,
                    "体重记录关键字段为空。", "人工核对原始记录后再决定是否修正。",
                ))
        value = to_decimal(row.get("weight_kg"))
        if value is not None and value <= 0:
            issues.append(DataIssue(
                "weight_record", row_id, "NON_POSITIVE_WEIGHT", "ERROR", "weight_kg",
                "体重必须大于0kg。", "核对录入值和单位，禁止自动覆盖。",
            ))
        elif value is not None and (value < Decimal("25") or value > Decimal("250")):
            issues.append(DataIssue(
                "weight_record", row_id, "IMPLAUSIBLE_WEIGHT", "WARNING", "weight_kg",
                "体重超出当前 Java 身体画像表单采用的25—250kg宽范围。",
                "核对是否把斤误当成kg，确认后再处理。",
            ))
        record_date = pd.to_datetime(row.get("record_date"), errors="coerce")
        if pd.isna(record_date):
            issues.append(DataIssue(
                "weight_record", row_id, "INVALID_DATE", "ERROR", "record_date",
                "日期无法解析。", "按yyyy-MM-dd核对原始日期。",
            ))
        elif record_date.date() > date.today():
            issues.append(DataIssue(
                "weight_record", row_id, "FUTURE_DATE", "WARNING", "record_date",
                "记录日期晚于当前日期。", "确认系统时间与记录日期。",
            ))

    duplicate_columns = [c for c in ("user_id", "record_date") if c in weights.columns]
    if len(duplicate_columns) == 2:
        duplicates = weights[weights.duplicated(duplicate_columns, keep=False)]
        for _, row in duplicates.iterrows():
            issues.append(DataIssue(
                "weight_record", str(row.get("id")), "DUPLICATE_USER_DATE", "ERROR", "record_date",
                "同一用户同一天存在多条体重记录。", "人工保留可信记录；本工具不会删除。",
            ))

    ordered = weights.copy()
    ordered["_date"] = pd.to_datetime(ordered.get("record_date"), errors="coerce")
    ordered = ordered.dropna(subset=["_date"]).sort_values(["user_id", "_date"])
    for _, group in ordered.groupby("user_id", dropna=False):
        previous = None
        for _, row in group.iterrows():
            current = to_decimal(row.get("weight_kg"))
            if current is not None and previous is not None and previous > 0:
                change = abs(current - previous)
                ratio = change / previous
                if change > Decimal("3.00") or ratio > Decimal("0.05"):
                    issues.append(DataIssue(
                        "weight_record", str(row.get("id")), "SUDDEN_WEIGHT_CHANGE", "WARNING", "weight_kg",
                        "与上一条记录相比变化超过3kg或5%。", "核对称重条件、单位和录入值。",
                    ))
            if current is not None:
                previous = current

    valid_dates = pd.to_datetime(weights.get("record_date"), errors="coerce").dropna()
    if not valid_dates.empty:
        actual = set(valid_dates.dt.date)
        for missing in pd.date_range(valid_dates.min(), valid_dates.max(), freq="D"):
            if missing.date() not in actual:
                issues.append(DataIssue(
                    "weight_record", missing.date().isoformat(), "MISSING_WEIGHT_DATE", "INFO", "record_date",
                    "最早与最晚体重记录之间该日期没有记录。", "仅提示缺口，不自动填充。",
                ))
    return issues


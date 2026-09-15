from datetime import date
from decimal import Decimal
from typing import List

import pandas as pd

from normalization.food_name import likely_undelimited_compound, normalization_changed
from normalization.units import detect_mass
from validation.common import is_missing, to_decimal
from validation.issues import DataIssue


NUTRITION_LIMITS = {
    "calories_kcal": Decimal("3000"),
    "protein_g": Decimal("200"),
    "carbohydrate_g": Decimal("500"),
    "fat_g": Decimal("200"),
}


def validate_meals(meals: pd.DataFrame) -> List[DataIssue]:
    issues: List[DataIssue] = []
    if meals.empty:
        issues.append(DataIssue(
            "meal_record", "dataset", "NO_MEAL_DATA", "WARNING", "record_date",
            "饮食表没有记录。", "先由用户正常记录，不自动补造饮食。",
        ))
        return issues

    required = (
        "user_id", "record_date", "meal_type", "food_name", "calories_kcal",
        "protein_g", "carbohydrate_g", "fat_g", "source_type", "estimated",
    )
    for _, row in meals.iterrows():
        row_id = str(row.get("id", "unknown"))
        source_type = None if is_missing(row.get("source_type")) else str(row.get("source_type"))
        for field in required:
            if field not in meals.columns or is_missing(row.get(field)):
                issues.append(DataIssue(
                    "meal_record", row_id, "MISSING_REQUIRED_FIELD", "ERROR", field,
                    "饮食记录关键字段为空。", "人工核对识别候选或原始记录，不自动补值。", source_type,
                ))

        for field, maximum in NUTRITION_LIMITS.items():
            value = to_decimal(row.get(field))
            if value is None:
                continue
            if value < 0:
                issues.append(DataIssue(
                    "meal_record", row_id, "NEGATIVE_NUTRITION_VALUE", "ERROR", field,
                    "营养值不能为负数。", "核对AI候选或手工录入值，确认后再修改。", source_type,
                ))
            elif value > maximum:
                issues.append(DataIssue(
                    "meal_record", row_id, "EXTREME_NUTRITION_VALUE", "WARNING", field,
                    "单条食物记录超出宽松极值阈值{}。".format(maximum),
                    "核对单位、份量和小数点。", source_type,
                ))

        calories = to_decimal(row.get("calories_kcal"))
        protein = to_decimal(row.get("protein_g"))
        carbohydrate = to_decimal(row.get("carbohydrate_g"))
        fat = to_decimal(row.get("fat_g"))
        if None not in (calories, protein, carbohydrate, fat) and calories > 0:
            macro_calories = protein * 4 + carbohydrate * 4 + fat * 9
            difference = abs(calories - macro_calories)
            if difference > Decimal("200") and difference / calories > Decimal("0.60"):
                issues.append(DataIssue(
                    "meal_record", row_id, "MACRO_CALORIE_MISMATCH", "WARNING", "calories_kcal",
                    "热量与三大营养素换算值差异超过200kcal且超过记录热量的60%。",
                    "估算数据可人工复核，不自动重算正式值。", source_type,
                ))

        if likely_undelimited_compound(row.get("food_name")):
            issues.append(DataIssue(
                "meal_record", row_id, "UNDELIMITED_COMPOUND_FOOD_NAME", "WARNING", "food_name",
                "食物名称可能包含多个食物，但没有明确分隔符。",
                "在预览中拆分确认，保留原始名称。", source_type,
                None if is_missing(row.get("food_name")) else str(row.get("food_name")),
            ))
        elif normalization_changed(row.get("food_name")):
            issues.append(DataIssue(
                "meal_record", row_id, "FOOD_NAME_CAN_BE_NORMALIZED", "INFO", "food_name",
                "食物名称存在已知别名或分隔符格式差异。",
                "仅在清洗预览中展示标准名，正式值仍由用户确认。", source_type,
                None if is_missing(row.get("food_name")) else str(row.get("food_name")),
            ))

        detected_mass = detect_mass(row.get("portion_description"))
        if detected_mass and Decimal(detected_mass["normalized_grams"]) < 0:
            issues.append(DataIssue(
                "meal_record", row_id, "NEGATIVE_PORTION_MASS", "ERROR", "portion_description",
                "份量描述中识别到负质量。", "核对原始份量和单位，不自动取绝对值。", source_type,
                str(row.get("portion_description")),
            ))

        record_date = pd.to_datetime(row.get("record_date"), errors="coerce")
        if pd.isna(record_date):
            issues.append(DataIssue(
                "meal_record", row_id, "INVALID_DATE", "ERROR", "record_date",
                "日期无法解析。", "按yyyy-MM-dd核对日期。", source_type,
            ))
        elif record_date.date() > date.today():
            issues.append(DataIssue(
                "meal_record", row_id, "FUTURE_DATE", "WARNING", "record_date",
                "记录日期晚于当前日期。", "确认系统时间与记录日期。", source_type,
            ))

    duplicate_columns = [
        "user_id", "record_date", "meal_type", "food_name", "portion_description", "calories_kcal"
    ]
    if all(column in meals.columns for column in duplicate_columns):
        duplicates = meals[meals.duplicated(duplicate_columns, keep=False)]
        for _, row in duplicates.iterrows():
            issues.append(DataIssue(
                "meal_record", str(row.get("id")), "DUPLICATE_MEAL_RECORD", "ERROR", "food_name",
                "同日同餐次存在完全相同的食物、份量和热量记录。",
                "人工确认后决定保留哪条；本工具不会删除。", str(row.get("source_type")),
            ))

    valid_dates = pd.to_datetime(meals.get("record_date"), errors="coerce").dropna()
    if not valid_dates.empty:
        actual = set(valid_dates.dt.date)
        for missing in pd.date_range(valid_dates.min(), valid_dates.max(), freq="D"):
            if missing.date() not in actual:
                issues.append(DataIssue(
                    "meal_record", missing.date().isoformat(), "MISSING_MEAL_DATE", "INFO", "record_date",
                    "饮食记录日期范围内该日完全没有记录。",
                    "仅提示缺口；不能推断为没有进食。",
                ))

        expected_meals = {"BREAKFAST", "LUNCH", "DINNER"}
        grouped = meals.groupby(pd.to_datetime(meals["record_date"]).dt.date)
        for meal_date, group in grouped:
            present = set(str(value) for value in group["meal_type"].dropna())
            for meal_type in sorted(expected_meals - present):
                issues.append(DataIssue(
                    "meal_record", "{}:{}".format(meal_date, meal_type), "MEAL_SLOT_NOT_RECORDED", "INFO",
                    "meal_type", "该日期没有{}记录。".format(meal_type),
                    "无法区分未进食和未记录，仅作为覆盖率提示。",
                ))
    return issues

from typing import Tuple

import pandas as pd

from normalization.food_name import clean_text, normalize_food_name
from normalization.units import detect_mass


def build_meal_preview(meals: pd.DataFrame) -> Tuple[pd.DataFrame, int]:
    if meals.empty:
        columns = [
            "id", "record_date", "source_type", "food_name_original", "food_name_normalized",
            "portion_original", "portion_normalized", "detected_mass_value", "detected_mass_unit",
            "normalized_mass_g", "normalization_changed",
        ]
        return pd.DataFrame(columns=columns), 0

    rows = []
    changed_count = 0
    for _, row in meals.iterrows():
        original_name = clean_text(row.get("food_name"))
        normalized_name = normalize_food_name(original_name)
        original_portion = clean_text(row.get("portion_description"))
        detected = detect_mass(original_portion) or {}
        changed = original_name != normalized_name or str(row.get("food_name", "")) != original_name
        changed_count += int(changed)
        rows.append({
            "id": row.get("id"),
            "record_date": row.get("record_date"),
            "source_type": row.get("source_type"),
            "food_name_original": original_name,
            "food_name_normalized": normalized_name,
            "portion_original": original_portion,
            "portion_normalized": original_portion,
            "detected_mass_value": detected.get("detected_value"),
            "detected_mass_unit": detected.get("detected_unit"),
            "normalized_mass_g": detected.get("normalized_grams"),
            "normalization_changed": changed,
        })
    return pd.DataFrame(rows), changed_count

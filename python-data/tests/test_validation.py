import unittest
from datetime import date, timedelta

import pandas as pd

from validation.meal_validator import validate_meals
from validation.weight_validator import validate_weights


class ValidationTest(unittest.TestCase):
    def test_weight_duplicate_and_sudden_change_are_reported(self):
        today = date.today()
        frame = pd.DataFrame([
            {"id": 1, "user_id": 1, "record_date": today - timedelta(days=1), "weight_kg": 56},
            {"id": 2, "user_id": 1, "record_date": today, "weight_kg": 70},
            {"id": 3, "user_id": 1, "record_date": today, "weight_kg": 70},
        ])
        types = [issue.issue_type for issue in validate_weights(frame)]
        self.assertIn("SUDDEN_WEIGHT_CHANGE", types)
        self.assertEqual(2, types.count("DUPLICATE_USER_DATE"))

    def test_meal_negative_and_missing_fields_are_reported_without_mutation(self):
        frame = pd.DataFrame([{
            "id": 1, "user_id": 1, "record_date": date.today(), "meal_type": "LUNCH",
            "food_name": "鸡胸", "portion_description": "100g", "calories_kcal": -10,
            "protein_g": None, "carbohydrate_g": 10, "fat_g": 2,
            "source_type": "AI_IMAGE", "estimated": True,
        }])
        before = frame.copy(deep=True)
        types = [issue.issue_type for issue in validate_meals(frame)]
        self.assertIn("NEGATIVE_NUTRITION_VALUE", types)
        self.assertIn("MISSING_REQUIRED_FIELD", types)
        pd.testing.assert_frame_equal(before, frame)

    def test_missing_meal_slot_is_information_not_fabricated_record(self):
        frame = pd.DataFrame([{
            "id": 1, "user_id": 1, "record_date": date.today(), "meal_type": "LUNCH",
            "food_name": "米饭", "portion_description": "一碗", "calories_kcal": 200,
            "protein_g": 4, "carbohydrate_g": 45, "fat_g": 1,
            "source_type": "MANUAL", "estimated": False,
        }])
        issues = validate_meals(frame)
        gaps = [issue for issue in issues if issue.issue_type == "MEAL_SLOT_NOT_RECORDED"]
        self.assertEqual(2, len(gaps))
        self.assertTrue(all(issue.severity == "INFO" for issue in gaps))

    def test_negative_portion_mass_is_reported(self):
        frame = pd.DataFrame([{
            "id": 1, "user_id": 1, "record_date": date.today(), "meal_type": "LUNCH",
            "food_name": "鸡胸肉", "portion_description": "-100g", "calories_kcal": 150,
            "protein_g": 25, "carbohydrate_g": 1, "fat_g": 4,
            "source_type": "AI_IMAGE", "estimated": True,
        }])
        types = [issue.issue_type for issue in validate_meals(frame)]
        self.assertIn("NEGATIVE_PORTION_MASS", types)


if __name__ == "__main__":
    unittest.main()

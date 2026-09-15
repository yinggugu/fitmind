import unittest
from decimal import Decimal

from normalization.food_name import likely_undelimited_compound, normalize_food_name
from normalization.units import detect_mass, normalize_mass_to_g, normalize_weight_to_kg


class NormalizationTest(unittest.TestCase):
    def test_food_alias_and_separator_are_normalized(self):
        self.assertEqual("鸡胸肉", normalize_food_name(" 煎鸡胸肉 "))
        self.assertEqual("番茄、鸡胸肉", normalize_food_name("西红柿 + 鸡胸"))

    def test_weight_conversion_uses_decimal(self):
        self.assertEqual(Decimal("56.80"), normalize_weight_to_kg("113.6", "斤"))
        self.assertEqual(Decimal("500.00"), normalize_mass_to_g("1", "斤"))

    def test_detect_explicit_mass_only(self):
        self.assertEqual("60.00", detect_mass("地瓜60g")["normalized_grams"])
        self.assertIsNone(detect_mass("地瓜一拳"))

    def test_product_name_is_not_misclassified_as_multiple_foods(self):
        self.assertFalse(likely_undelimited_compound("牛肉午餐肉"))


if __name__ == "__main__":
    unittest.main()

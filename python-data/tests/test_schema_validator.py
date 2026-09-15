import unittest
from pathlib import Path

from validation.schema_validator import parse_entity_columns


class SchemaValidatorTest(unittest.TestCase):
    def test_current_java_entities_are_parsed(self):
        project_root = Path(__file__).resolve().parents[2]
        entities = parse_entity_columns(project_root)
        self.assertIn("weight_record", entities)
        self.assertIn("weight_kg", entities["weight_record"])
        self.assertIn("meal_record", entities)
        self.assertIn("calories_kcal", entities["meal_record"])
        self.assertNotIn("calories", entities["meal_record"])


if __name__ == "__main__":
    unittest.main()

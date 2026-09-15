import re
from decimal import Decimal, InvalidOperation
from typing import Dict, Optional


MASS_PATTERN = re.compile(r"(?P<value>[-+]?\d+(?:\.\d+)?)\s*(?P<unit>kg|千克|公斤|斤|g|克)", re.IGNORECASE)


def normalize_weight_to_kg(value: object, unit: str) -> Decimal:
    number = Decimal(str(value))
    normalized_unit = str(unit).strip().lower()
    if normalized_unit in ("kg", "千克", "公斤"):
        return number.quantize(Decimal("0.01"))
    if normalized_unit in ("jin", "斤"):
        return (number / Decimal("2")).quantize(Decimal("0.01"))
    raise ValueError("不支持的体重单位：{}".format(unit))


def normalize_mass_to_g(value: object, unit: str) -> Decimal:
    number = Decimal(str(value))
    normalized_unit = str(unit).strip().lower()
    if normalized_unit in ("g", "克"):
        result = number
    elif normalized_unit in ("kg", "千克", "公斤"):
        result = number * Decimal("1000")
    elif normalized_unit in ("jin", "斤"):
        result = number * Decimal("500")
    else:
        raise ValueError("不支持的质量单位：{}".format(unit))
    return result.quantize(Decimal("0.01"))


def detect_mass(value: object) -> Optional[Dict[str, object]]:
    text = "" if value is None else str(value).strip()
    match = MASS_PATTERN.search(text)
    if not match:
        return None
    try:
        number = Decimal(match.group("value"))
        unit = match.group("unit")
        return {
            "detected_value": str(number),
            "detected_unit": unit,
            "normalized_grams": str(normalize_mass_to_g(number, unit)),
        }
    except (InvalidOperation, ValueError):
        return None

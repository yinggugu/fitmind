from decimal import Decimal, InvalidOperation
from typing import Optional

import pandas as pd


def is_missing(value: object) -> bool:
    return value is None or pd.isna(value) or (isinstance(value, str) and not value.strip())


def to_decimal(value: object) -> Optional[Decimal]:
    if is_missing(value):
        return None
    try:
        return value if isinstance(value, Decimal) else Decimal(str(value))
    except (InvalidOperation, ValueError):
        return None


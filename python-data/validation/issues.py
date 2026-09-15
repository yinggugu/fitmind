from dataclasses import asdict, dataclass
from typing import Any, Dict, Optional


@dataclass(frozen=True)
class DataIssue:
    dataset: str
    row_id: str
    issue_type: str
    severity: str
    field: str
    reason: str
    suggestion: str
    source_type: Optional[str] = None
    observed_value: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)

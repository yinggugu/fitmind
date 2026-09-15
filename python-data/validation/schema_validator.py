import re
from pathlib import Path
from typing import Dict, List, Set

import pandas as pd


ENTITY_GLOB = "backend/src/main/java/com/fitmind/entity/*.java"


def camel_to_snake(name: str) -> str:
    return re.sub(r"(?<!^)(?=[A-Z])", "_", name).lower()


def parse_entity_fields(project_root: Path) -> Dict[str, Dict[str, str]]:
    result: Dict[str, Dict[str, str]] = {}
    for path in project_root.glob(ENTITY_GLOB):
        source = path.read_text(encoding="utf-8")
        table_match = re.search(r'@TableName\("([^"]+)"\)', source)
        if not table_match:
            continue
        fields = {}
        for match in re.finditer(r"private\s+(?!static\b)([\w<>?]+)\s+(\w+)\s*;", source):
            fields[camel_to_snake(match.group(2))] = match.group(1)
        result[table_match.group(1)] = fields
    return result


def parse_entity_columns(project_root: Path) -> Dict[str, Set[str]]:
    return {table: set(fields) for table, fields in parse_entity_fields(project_root).items()}


def _type_compatible(java_type: str, database_type: str) -> bool:
    expected = {
        "Long": {"bigint", "int"},
        "BigDecimal": {"decimal", "numeric"},
        "LocalDate": {"date"},
        "LocalTime": {"time"},
        "LocalDateTime": {"datetime", "timestamp"},
        "String": {"varchar", "char", "text", "longtext", "mediumtext"},
        "Boolean": {"tinyint", "bit", "boolean"},
        "WeightUnit": {"varchar", "char"},
        "MealType": {"varchar", "char"},
        "SourceType": {"varchar", "char"},
        "GenerationType": {"varchar", "char"},
    }
    return database_type.lower() in expected.get(java_type, {database_type.lower()})


def _populated_count(frame: pd.DataFrame, column: str) -> int:
    if frame.empty or column not in frame.columns:
        return 0
    values = frame[column]
    populated = values.notna()
    if values.dtype == object:
        populated &= values.astype(str).str.strip().ne("")
    return int(populated.sum())


def _extra_column_suggestion(table: str, column: str, populated: int) -> str:
    special = {
        ("meal_record", "calories"): "旧热量列当前为空；备份并确认无旧代码依赖后，可通过单独迁移删除。",
        ("meal_record", "is_estimated"): "旧估算标记仍有数据且与estimated语义冲突；先确定estimated为正式列，再迁移并删除旧列。",
        ("user_profile", "height_cm"): "当前Entity未映射，但身体画像表有身高；需决定保留用户基础身高还是统一到身体画像。",
        ("user_profile", "daily_calorie_target"): "当前Entity未映射；若页面仍使用目标值应补Entity，否则确认无依赖后迁移。",
        ("user_profile", "daily_protein_target_g"): "当前Entity未映射；若页面仍使用目标值应补Entity，否则确认无依赖后迁移。",
    }
    if (table, column) in special:
        return special[(table, column)]
    if populated:
        return "数据库中仍有{}条非空值；先查明旧功能依赖并设计迁移，不能直接删除。".format(populated)
    return "当前数据全部为空；可列为历史残留候选，但仍需备份和代码检索后再迁移。"


def compare_schema(
    project_root: Path,
    schema_columns: pd.DataFrame,
    schema_indexes: pd.DataFrame,
    data: Dict[str, pd.DataFrame],
) -> List[Dict[str, object]]:
    entity_fields = parse_entity_fields(project_root)
    entity_columns = {table: set(fields) for table, fields in entity_fields.items()}
    findings: List[Dict[str, object]] = []
    database_tables = set(schema_columns["TABLE_NAME"].unique()) if not schema_columns.empty else set()
    for table in sorted(database_tables | set(entity_columns)):
        db_columns = set(
            schema_columns.loc[schema_columns["TABLE_NAME"] == table, "COLUMN_NAME"].tolist()
        )
        java_columns = entity_columns.get(table, set())
        frame = data.get(table, pd.DataFrame())
        for column in sorted(db_columns & java_columns):
            database_type = schema_columns.loc[
                (schema_columns["TABLE_NAME"] == table) & (schema_columns["COLUMN_NAME"] == column),
                "DATA_TYPE",
            ].iloc[0]
            java_type = entity_fields[table][column]
            compatible = _type_compatible(java_type, str(database_type))
            findings.append({
                "table": table,
                "column": column,
                "status": "KEEP" if compatible else "TYPE_REVIEW",
                "populated_count": _populated_count(frame, column),
                "reason": "数据库字段与Java Entity名称一致；Java类型{}，数据库类型{}。".format(java_type, database_type),
                "suggestion": "保留。" if compatible else "检查类型映射并通过数据库迁移或Entity调整统一。",
            })
        for column in sorted(db_columns - java_columns):
            populated = _populated_count(frame, column)
            findings.append({
                "table": table,
                "column": column,
                "status": "DB_ONLY_REVIEW" if populated else "HISTORICAL_EMPTY_CANDIDATE",
                "populated_count": populated,
                "reason": "数据库存在该字段，但当前Java Entity未映射。",
                "suggestion": _extra_column_suggestion(table, column, populated),
            })
        for column in sorted(java_columns - db_columns):
            findings.append({
                "table": table,
                "column": column,
                "status": "ENTITY_ONLY_MIGRATION_REQUIRED",
                "populated_count": 0,
                "reason": "Java Entity声明了字段，但数据库中不存在。",
                "suggestion": "在部署前补充数据库迁移，当前不要直接运行依赖该字段的写入。",
            })

    if not schema_indexes.empty:
        for table in ("weight_record", "daily_health_letter"):
            rows = schema_indexes[
                (schema_indexes["TABLE_NAME"] == table)
                & (schema_indexes["NON_UNIQUE"] == 0)
            ]
            for index_name, group in rows.groupby("INDEX_NAME"):
                columns = group.sort_values("SEQ_IN_INDEX")["COLUMN_NAME"].tolist()
                date_column = "record_date" if table == "weight_record" else "report_date"
                if columns == [date_column]:
                    findings.append({
                        "table": table,
                        "column": date_column,
                        "status": "INDEX_REVIEW",
                        "populated_count": len(data.get(table, pd.DataFrame())),
                        "reason": "存在只按日期唯一的旧索引{}，同时又有user_id+日期复合唯一索引。".format(index_name),
                        "suggestion": "固定单用户阶段可保留；未来多用户前应通过迁移移除单列唯一约束。",
                    })
    return findings

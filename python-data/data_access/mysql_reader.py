import os
from contextlib import contextmanager
from decimal import Decimal
from typing import Dict, Iterator, List, Optional

import pandas as pd
import pymysql
from pymysql.cursors import DictCursor


class DatabaseConfigurationError(RuntimeError):
    pass


class ReadOnlyMySqlReader:
    """Loads FitMind data in a transaction that MySQL marks read-only."""

    TABLES = (
        "user_profile",
        "weight_record",
        "meal_record",
        "body_measurement",
        "daily_health_letter",
    )

    def __init__(self) -> None:
        password = os.getenv("FITMIND_DB_PASSWORD", "")
        if not password:
            raise DatabaseConfigurationError(
                "请先设置环境变量 FITMIND_DB_PASSWORD；程序不会把数据库密码写入项目文件。"
            )
        self.connection_options = {
            "host": os.getenv("FITMIND_DB_HOST", "127.0.0.1"),
            "port": int(os.getenv("FITMIND_DB_PORT", "3306")),
            "user": os.getenv("FITMIND_DB_USER", "root"),
            "password": password,
            "database": os.getenv("FITMIND_DB_NAME", "fitmind"),
            "charset": "utf8mb4",
            "cursorclass": DictCursor,
            "autocommit": False,
        }

    @contextmanager
    def connection(self) -> Iterator[pymysql.Connection]:
        connection = pymysql.connect(**self.connection_options)
        try:
            with connection.cursor() as cursor:
                cursor.execute("START TRANSACTION READ ONLY")
            yield connection
        finally:
            connection.rollback()
            connection.close()

    def load_all(self) -> Dict[str, pd.DataFrame]:
        with self.connection() as connection:
            result = {
                table: pd.read_sql("SELECT * FROM `{}`".format(table), connection)
                for table in self.TABLES
            }
            result["schema_columns"] = pd.read_sql(
                """
                SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, DATA_TYPE,
                       IS_NULLABLE, COLUMN_KEY, COLUMN_DEFAULT, EXTRA,
                       ORDINAL_POSITION
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                ORDER BY TABLE_NAME, ORDINAL_POSITION
                """,
                connection,
            )
            result["schema_indexes"] = pd.read_sql(
                """
                SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX
                """,
                connection,
            )
            return result


def decimal_or_none(value: object) -> Optional[Decimal]:
    if value is None or pd.isna(value):
        return None
    return value if isinstance(value, Decimal) else Decimal(str(value))


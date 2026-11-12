import json
import os
import sys

import pymysql


def main():
    connection = pymysql.connect(
        host=os.environ["MYSQL_HOST"],
        port=int(os.environ.get("MYSQL_PORT", "3306")),
        user=os.environ["MYSQL_USER"],
        password=os.environ["MYSQL_PASSWORD"],
        database=os.environ["MYSQL_DB"],
        charset="utf8mb4",
        connect_timeout=12,
        read_timeout=30,
        cursorclass=pymysql.cursors.DictCursor,
    )
    database = os.environ["MYSQL_DB"]
    try:
        with connection.cursor() as cursor:
            cursor.execute(
                """
                SELECT TABLE_NAME, TABLE_TYPE, ENGINE, TABLE_ROWS, TABLE_COMMENT,
                       CREATE_TIME, UPDATE_TIME
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA=%s
                ORDER BY TABLE_NAME
                """,
                (database,),
            )
            tables = cursor.fetchall()
            cursor.execute(
                """
                SELECT TABLE_NAME, ORDINAL_POSITION, COLUMN_NAME, COLUMN_TYPE,
                       IS_NULLABLE, COLUMN_DEFAULT, COLUMN_KEY, EXTRA, COLUMN_COMMENT
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA=%s
                ORDER BY TABLE_NAME, ORDINAL_POSITION
                """,
                (database,),
            )
            columns = cursor.fetchall()
            cursor.execute("SELECT VERSION() AS version, DATABASE() AS database_name")
            server = cursor.fetchone()
        payload = json.dumps({"server": server, "tables": tables, "columns": columns}, ensure_ascii=False, default=str)
        output_path = os.environ.get("METADATA_OUTPUT")
        if output_path:
            with open(output_path, "w", encoding="utf-8") as output:
                output.write(payload)
            print(json.dumps({"output": output_path, "tables": len(tables), "columns": len(columns)}, ensure_ascii=False))
        else:
            print(payload)
    finally:
        connection.close()


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"{type(exc).__name__}: {exc}", file=sys.stderr)
        raise

# FitMind Python 数据处理模块

该模块是 Java 后端的离线辅助工具，不提供Web接口，也不替代 Spring Boot。数据库连接以 `START TRANSACTION READ ONLY` 开始，所有命令只读取数据并在 `reports` 目录生成文件。

## 环境

- Python 3.7+
- pandas 1.3.5
- numpy 1.21.6
- PyMySQL 1.0.2

安装依赖到项目目录，避免写入系统Python目录：

```cmd
cd python-data
python -m pip install --target .packages -r requirements.txt
```

## 数据库配置

密码只通过环境变量传入，不写入代码或报告：

```cmd
set FITMIND_DB_HOST=127.0.0.1
set FITMIND_DB_PORT=3306
set FITMIND_DB_NAME=fitmind
set FITMIND_DB_USER=root
set FITMIND_DB_PASSWORD=你的MySQL密码
```

## 命令

```cmd
python main.py --check
python main.py --clean-preview
python main.py --report
python -m unittest discover -s tests -v
```

- `--check`：控制台输出真实数据库汇总，不生成写回操作。
- `--clean-preview`：生成食物名称、文本和显式质量单位标准化预览。
- `--report`：生成Markdown、JSON、问题CSV和清洗预览CSV。

报告中的异常仅是待人工复核项。缺失值不会插补，异常行不会删除，标准名不会写回 MySQL。未来若增加写回能力，必须另行实现人工确认、变更审计、事务回滚和明确的 `--apply` 开关。

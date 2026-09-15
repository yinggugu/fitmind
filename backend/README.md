# FitMind Backend

Java 8、Spring Boot 2.7.18、MyBatis-Plus、MySQL 与 LangChain4j 0.31.0 的单体后端。固定用户 `userId=1`，服务端统一保存和返回 kg 数值。

## AI 健康助手

`POST /api/assistant/chat` 接收自然语言问题。LangChain4j 负责选择只读 Tool，Tool 复用现有 Service 查询 MySQL；体重变化、平均值、营养汇总、日期覆盖和最高热量日期均由 Java 计算，模型只组织回答。

请求示例：

```json
{
  "question": "帮我总结最近两周的饮食和体重情况"
}
```

快捷问题：`GET /api/assistant/suggestions`。

助手优先复用 `fitmind.qwen.api-key`、`fitmind.qwen.base-url` 和 `fitmind.qwen.model`；Qwen 未配置时回退到现有 MiMo 配置。

复制 `ai-local.properties.example` 为 `ai-local.properties`，再填写本地数据库和模型配置。`application.yml` 会以可选外部配置的方式加载它；真实配置文件已加入 `.gitignore`，不会被打包进 Jar。

```properties
fitmind.qwen.api-key=你的API Key
fitmind.qwen.base-url=https://你的工作空间域名/compatible-mode/v1
fitmind.qwen.model=qwen3.7-plus

# 图片识别沿用同一套兼容接口
fitmind.mimo.api-key=你的API Key
fitmind.mimo.base-url=https://你的工作空间域名/compatible-mode/v1
fitmind.mimo.model=qwen3.7-plus
```

部署环境也可以通过环境变量设置支持 Tool Calling 的文本模型：

```cmd
set DASHSCOPE_API_KEY=你的文本模型Key
set DASHSCOPE_BASE_URL=OpenAI兼容接口的v1地址
set DASHSCOPE_TEXT_MODEL=支持Tool Calling的模型名
```

调用超时由 `fitmind.assistant.timeout-seconds` 控制，默认45秒。模型未配置、超时或调用失败时，接口返回 `degraded=true` 的友好兜底信息，不会修改健康数据。

## 数据库

配置位于 `src/main/resources/application.yml`。AI 健康助手只调用已有 Service 的查询方法，不接收 SQL，也没有新增、编辑或删除 Tool。

## 测试、构建、启动

使用 Maven 运行测试、构建和启动：

```cmd
cd backend
mvn.cmd clean test
mvn.cmd clean package
mvn.cmd spring-boot:run
```

需要连接本机 MySQL 验证全部九个只读 Tool 时，显式运行：

```cmd
mvn.cmd -Dfitmind.live-db-test=true -Dtest=HealthDataToolsDatabaseIT test
```

基础接口：`http://127.0.0.1:8080/api/profile`。

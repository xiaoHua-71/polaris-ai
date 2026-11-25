# Polaris 工具调用

## 工具注册

工具用 @Tool 注解声明，@ToolParam 描述参数；工具类用 @ToolGroup 分组，例如 time 和 calc 两组。ToolRegistry 按 polaris.tools.groups 配置加载指定分组，留空表示全部加载。

## 轮次护栏

工具循环的护栏在 application.yml 的 spring.ai.tools.limits 下配置：max-calls-per-tool-default 默认 3，表示单个工具最多调用 3 次；max-total-tool-calls 默认 5，表示一轮请求最多调用 5 次；on-limit-exceeded 默认 RETURN_ERROR_RESPONSE，超限时把错误作为工具结果回给模型，而不是抛异常。

## 结果截断

工具结果在进入 prompt 前会被 TruncatingToolCallback 截断，默认最多 2000 个字符，超出部分替换为“…(结果已截断)”。这样可以避免大段工具输出撑爆上下文。

## 现有工具

TimeTools 提供 getCurrentTime，按 IANA 时区返回当前时间；CalculatorTools 提供 calculate，用受限的 SpEL 表达式计算算术，只允许数字、括号、小数点、空格和加减乘除。

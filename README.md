# Browser-Agent

是一个 AI搜索 工具，通过 MCP 和 无头浏览器以获得与外界交互的能力。

## 接口列表

> 所有接口的响应时间预计在5分钟以内

### 1. 流式聊天接口

- **路径**: `/chat/sse`
- **方法**: GET
- **参数**:
    - `message` (String, 必填): 用户发送的消息内容
- **响应类型**: Server-Sent Events (SSE)
- **事件类型**:
    - `chat`: 正常聊天消息
    - `error`: 错误消息
- **超时时间**: 15分钟
- **描述**: 通过SSE流式返回聊天响应，适合需要实时显示的场景

### 2. 阻塞式聊天接口

- **路径**: `/chat/blocked`
- **方法**: POST
- **参数**:
    - 请求体: 原始消息字符串 (UTF-8编码)
- **响应类型**: String
- **描述**: 同步返回完整的聊天响应，适合不需要实时显示的场景
- **注意**: 请求体会自动进行URL解码



## 配置项

关于EdgeDriver的驱动表如图

| 属性名 | 类型 | 默认值 | 必填 | 描述 |
|--------|------|--------|------|------|
| edge.version | String | null | 是 | EdgeDriver的版本号 |
| edge.edgePath | String | null | 是 | Edge浏览器的可执行文件路径 |
| edge.headless | Boolean | false | 否 | 是否以无头模式运行浏览器 |
| edge.poolSize | String | "1" | 否 | 浏览器驱动池大小 |

关于Spring AI的配置表如图(以DeepSeek为例)

```properties
spring.ai.openai.base-url=https://api.deepseek.com
spring.ai.openai.api-key=sk-tokens
spring.ai.openai.chat.options.model=deepseek-chat
```


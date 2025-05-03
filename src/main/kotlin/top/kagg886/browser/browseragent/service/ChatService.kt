package top.kagg886.browser.browseragent.service

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import top.kagg886.browser.browseragent.util.asFlow


@Service
class ChatService(chatClientBuilder: ChatClient.Builder, mCPService: MCPService) {
    private val log = org.slf4j.LoggerFactory.getLogger(javaClass)

    private val chatClient = chatClientBuilder
        .defaultTools(mCPService)
        .defaultAdvisors(SimpleLoggerAdvisor())
        .build()

    private val CHAT_PROMPT =
        """
        你是一个具有搜索功能的助手，当我向你询问信息时，调用MCP工具并会回传结果。

        你可以调用的mcp工具如下：

        1. google-search
        Params:
        query (string, 必填): 搜索关键词（支持高级语法，如 site:、- 排除等）。
        limit (number, 可选): 返回结果数量（默认 10，范围 1-20）。
        timeout (number, 可选): 超时时间（毫秒，默认 30000）。
        2. fetch-url
        Params:
        url (string, 必填): 目标网页 URL。
        
        当用户要求你回答问题的时候，你需要遵循下列步骤：

        提取用户意图中的关键词，调用 name为 'google-search' 的MCP工具 获得 搜索结果。

        根据搜索结果中选择3个你认为最可能与问题相关的url，调用 name 为 'fetch-url' 的MCP工具，获取详细的HTML数据，进行总结后回馈给用户。
        """.trimIndent()

    fun chat(message: String): String = runBlocking {
        chatAsync(message)
            .asFlow()
            .toList()
            .joinToString("")
    }

    fun chatAsync(message: String): Flux<String> = run {
        log.info("start chatting with $message")
        chatClient
            .prompt(CHAT_PROMPT)
            .user(message)
            .stream()
            .content()
    }
}

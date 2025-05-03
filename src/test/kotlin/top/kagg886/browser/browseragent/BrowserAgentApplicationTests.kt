package top.kagg886.browser.browseragent

import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import top.kagg886.browser.browseragent.service.MCPService
import top.kagg886.cctr.driver.WebDriverDispatcher
import top.kagg886.cctr.driver.captchaHTML
import kotlin.time.Duration.Companion.seconds
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor
import reactor.core.publisher.Flux
import top.kagg886.browser.browseragent.util.asFlow
import java.util.concurrent.CountDownLatch
import java.time.Duration

@SpringBootTest
class BrowserAgentApplicationTests {

    private val log = LoggerFactory.getLogger(javaClass)

    @Autowired
    private lateinit var chatClientBuilder: ChatClient.Builder

    @Autowired
    private lateinit var mCPService: MCPService

    @Autowired
    private lateinit var webDriverDispatcher: WebDriverDispatcher

    @Test
    fun testHTMLCapture() {
        println(mCPService.requestWebPage("https://www.baidu.com"))
    }

    @Test
    fun testHTMLCaptureFailed() = webDriverDispatcher.useDriver {
        val html = it.captchaHTML("https://asdadsadsadsa.com")
        println(html)
    }

    @Test
    fun testSearchLuxirty() {
        println(mCPService.search("DeepSeek"))
    }

    @Test
    fun testAI() {
        val chat = chatClientBuilder
            .defaultTools(mCPService)
            .defaultAdvisors(SimpleLoggerAdvisor())
            .build()

        log.info("发送提示给AI")
        val req = chat.prompt(
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
        )
        val response = req.user("请帮我搜索陕西十大名吃").stream()
        log.info("开始接收流式响应")


        runBlocking {
            launch {
                response.content().asFlow().collect {
                    log.info(it)
                }
            }.invokeOnCompletion {
                log.info("流式响应结束")
                if (it != null) {
                    log.error("流式响应异常", it)
                }
            }
        }
    }
}

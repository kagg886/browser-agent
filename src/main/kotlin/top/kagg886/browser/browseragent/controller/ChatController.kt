package top.kagg886.browser.browseragent.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import reactor.core.publisher.Flux
import top.kagg886.browser.browseragent.service.ChatService
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@RestController
@RequestMapping("chat")
class ChatController(private val chatService: ChatService) {

    private val log = org.slf4j.LoggerFactory.getLogger(javaClass)

    @GetMapping("sse")
    fun chatAsync(@RequestParam message: String): SseEmitter = SseEmitter(15.minutes.inWholeMilliseconds).apply {
        val disposable = chatService.chatAsync(message).subscribe(
            {
                log.info("接收到消息：$it")
                send(SseEmitter.event().name("chat").data(it).build())
            },
            {
                send(SseEmitter.event().name("error").data(it.stackTraceToString()).build())
                completeWithError(it)
            },
            {
                complete()
            }
        )
        onTimeout {
            log.error("无法完成请求，因为客户端认为该SSE超时。")
            disposable.dispose()
        }

        onCompletion {
            log.info("请求已完成。")
            disposable.dispose()
        }

        onError {
            log.error("发生错误：$it")
            disposable.dispose()
        }
    }

    @PostMapping("blocked")
    fun chatBlocking(@RequestBody message: String): String {
        val decoded = java.net.URLDecoder.decode(message, "UTF-8")
        return chatService.chat(decoded)
    }
}

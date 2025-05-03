package top.kagg886.cctr.driver

import io.ktor.util.logging.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.count
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.openqa.selenium.edge.EdgeDriver
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class DispatcherConfig {
    var driverFile: File? = null
    var driverPoolSize = 10
    var executableFile: File? = null
    var headless: Boolean = true
}

private val log = KtorSimpleLogger("WebDriverDispatcher")

class WebDriverDispatcher private constructor() {
    private lateinit var queue: Channel<EdgeDriver>
    private val list = mutableListOf<EdgeDriver>()

    var available = 0
        private set

    private val muteX = Mutex()

    companion object {
        fun init(conf: DispatcherConfig.() -> Unit): WebDriverDispatcher {
            log.info("prepare init web-driver-dispatcher...")
            val config = DispatcherConfig().apply(conf)
            WebDriverProducer.init(config.driverFile!!, config.executableFile!!, config.headless)
            log.info("init web-driver-dispatcher success, now creating event-loop...")
            return WebDriverDispatcher().apply {
                queue = Channel(capacity = config.driverPoolSize);
                for (i in 1..config.driverPoolSize) {
                    WebDriverProducer.newHeadlessDriver().apply {
                        list.add(this)
                        queue.trySend(this)
                    }
                }
                available = list.size
                log.info("creating event-loop success")
                Runtime.getRuntime().addShutdownHook(Thread {
                    runBlocking(Dispatchers.IO) {
                        list.map {
                            async {
                                log.info("prepare shutdown $it")
                                it.close()
                                it.quit()
                                log.info("shutdown $it success")
                            }
                        }.awaitAll()
                        queue.close()
                        list.clear()
                        log.info("shutdown driver-queue success")
                    }
                })
            }
        }
    }

    fun <T> useDriver(timeout: Duration = 1.minutes, block: suspend (EdgeDriver) -> T): T = runBlocking {
        val driver = withTimeoutOrNull(timeout) {
            queue.receive()
        }
        if (driver == null) {
            throw IllegalStateException("timeout")
        }
        muteX.withLock { available-- }
        val rtn = block(driver)
        queue.send(driver)
        muteX.withLock { available++ }
        rtn
    }
}

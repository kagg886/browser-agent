package top.kagg886.browser.browseragent.config

import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import kotlinx.io.readByteArray
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import top.kagg886.browser.browseragent.util.unzip
import top.kagg886.cctr.driver.WebDriverDispatcher
import top.kagg886.cctr.driver.getEdgeDownloadURL
import top.kagg886.cctr.driver.getEdgeDriverFile
import top.kagg886.cctr.driver.msedgedriverName
import java.io.File
import kotlin.time.Duration.Companion.minutes

@Configuration
@ConfigurationProperties("edge")
data class EdgeDriverConfig(
    var version: String? = null,
    var edgePath: String? = null,
    var headless: Boolean = false,
    var poolSize: String = "1",
) {
    private val log = org.slf4j.LoggerFactory.getLogger(javaClass)
    private val client = HttpClient(Java) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15.minutes.inWholeMilliseconds
            connectTimeoutMillis = 15.minutes.inWholeMilliseconds
            socketTimeoutMillis = 15.minutes.inWholeMilliseconds
        }
    }

    @Bean
    fun prepare(): WebDriverDispatcher {
        checkNotNull(version) { "edge.version不得为空，请编辑application.properties！" }
        checkNotNull(edgePath) { "edge.path不得为空，请编辑application.properties！" }
        log.info("EdgeService initializing...")
        log.info("version: {}", version)

        val file = getEdgeDriverFile(version!!)
        if (file.exists()) {
            log.info("EdgeDriver exists, skip downloading")
        } else {
            val url = getEdgeDownloadURL(version!!)
            log.info("Downloading EdgeDriver from $url")
            val zip = File(file, "edge-driver.zip")
            if (zip.exists()) {
                zip.delete()
            }
            zip.parentFile.mkdirs()
            zip.createNewFile()

            runBlocking {
                client.prepareGet(url).execute {
                    val status = it.status
                    if (status.isSuccess().not()) {
                        log.error("url $url not exists")
                        throw IllegalStateException("url $url not exists")
                    }

                    val channel = it.bodyAsChannel()
                    while (!channel.isClosedForRead) {
                        val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
                        while (!packet.exhausted()) {
                            val bytes = packet.readByteArray()
                            zip.appendBytes(bytes)
                            it.contentLength()?.let { length ->
                                val buf = String.format(
                                    "%.2f",
                                    (zip.length().toFloat() / length.toFloat()) * 100
                                )
                                log.info("Downloading: $buf%")
                            }
                        }
                    }
                }
            }

            log.info("Unzipping EdgeDriver...")
            zip.unzip(file)
        }

        return WebDriverDispatcher.init {
            driverFile = File(file, msedgedriverName)
            executableFile = File(edgePath!!)
            this.headless = this@EdgeDriverConfig.headless
            driverPoolSize = poolSize.toInt()
        }
    }
}

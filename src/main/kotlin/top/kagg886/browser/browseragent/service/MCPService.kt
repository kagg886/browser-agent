package top.kagg886.browser.browseragent.service

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.select.Elements
import org.springframework.ai.tool.ToolCallbackProvider
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.method.MethodToolCallbackProvider
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Service
import top.kagg886.cctr.driver.WebDriverDispatcher
import top.kagg886.cctr.driver.captchaHTML
import org.openqa.selenium.JavascriptExecutor


@Service
class MCPService(private val webDriverDispatcher: WebDriverDispatcher) {
    private val log = org.slf4j.LoggerFactory.getLogger(javaClass)

    @Tool(name = "fetch-url", description = "使用GET请求，获取这个静态url对应的网页HTML代码。")
    fun requestWebPage(url: String): String = webDriverDispatcher.useDriver {
        log.info("requesting $url")
        val html = it.captchaHTML(url)
        if (html == null) {
            return@useDriver "请求失败"
        }
        val doc = Ksoup.parse(html)

        doc.select("h1,h2,h3,h4,h5,h6,p,span,strong,a").text()
    }

    @Tool(name = "google-search", description = "调用搜索引擎，返回搜索结果。")
    fun search(query: String): String = webDriverDispatcher.useDriver {
        log.info("searching $query")
        it.get("https://search.luxirty.com/search?q=$query")

        val html = runCatching {
            (it as JavascriptExecutor).executeAsyncScript(
                """
            const callback = arguments[arguments.length - 1];
            
            // 创建XHR监听器来捕获Google CSE请求
            const originalXHROpen = XMLHttpRequest.prototype.open;
            const originalXHRSend = XMLHttpRequest.prototype.send;
            
            XMLHttpRequest.prototype.open = function(method, url) {
                this._url = url;
                return originalXHROpen.apply(this, arguments);
            };
            
            XMLHttpRequest.prototype.send = function() {
                if (this._url && this._url.includes('https://cse.google.com/cse/element/v1')) {
                    const originalOnLoad = this.onload;
                    this.onload = function() {
                        if (originalOnLoad) {
                            originalOnLoad.apply(this, arguments);
                        }
                        
                        setTimeout(() => {
                            callback(document.documentElement.outerHTML);
                        }, 1000);
                    };
                }
                return originalXHRSend.apply(this, arguments);
            };
            
            setTimeout(() => {
                callback(document.documentElement.outerHTML);
            }, 10000);
        """
            ).toString()
        }.getOrElse { null }

        if (html == null) {
            return@useDriver "请求失败"
        }

        val document = Ksoup.parse(html)

        document.select(".gsc-webResult .gsc-result").joinToString("\n\n") { ele ->
            val title = ele.getElementsByTag("a").first()!!.text()
            val link = ele.getElementsByTag("a").first()!!.attr("href")
            val snippet = ele.selectFirst(".gs-bidi-start-align.gs-snippet")!!.text()
            """
            [$title]($link)
            $snippet
            """.trimIndent()
        }
    }

    @Bean
    fun injectMcp(): ToolCallbackProvider {
        return MethodToolCallbackProvider.builder().toolObjects(this).build()
    }
}

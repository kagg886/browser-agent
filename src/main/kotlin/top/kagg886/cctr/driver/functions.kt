package top.kagg886.cctr.driver

import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.edge.EdgeDriver

private const val CAPTCHA_HTML_SCRIPT: String =
    """
        const callback = arguments[arguments.length - 1];
        
        if (document.readyState === 'complete') {
            callback(document.documentElement.outerHTML);
            return;
        }
        
        document.addEventListener('readystatechange', () => {
            if (document.readyState === 'complete') {
                callback(document.documentElement.outerHTML);
            }
        });
    """

private val log = org.slf4j.LoggerFactory.getLogger(EdgeDriver::class.java)

fun EdgeDriver.captchaHTML(url: String): String? = runCatching {
    log.info("capturing html from $url")
    get(url)
    (this as JavascriptExecutor).executeAsyncScript(CAPTCHA_HTML_SCRIPT).toString()
}.getOrElse { null }

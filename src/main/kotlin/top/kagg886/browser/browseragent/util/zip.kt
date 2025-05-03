package top.kagg886.browser.browseragent.util

import java.io.File
import java.util.zip.ZipInputStream

fun File.unzip(file: File = this.parentFile.resolve(this.nameWithoutExtension)) {
    file.mkdirs()
    ZipInputStream(this.inputStream()).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            val file = file.resolve(entry.name)
            if (entry.isDirectory) {
                file.mkdirs()
            } else {
                file.outputStream().use {
                    it.write(zip.readBytes())
                }
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
    }
}

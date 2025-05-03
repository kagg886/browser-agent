import org.jetbrains.kotlin.daemon.common.toHexString
import java.io.FileInputStream
import java.security.MessageDigest

plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.spring") version "2.1.20"
    id("org.springframework.boot") version "3.4.5"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "top.kagg886.browser"
version = "1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

extra["springAiVersion"] = "1.0.0-M8"

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.ai:spring-ai-starter-model-openai")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("io.projectreactor:reactor-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    implementation("io.ktor:ktor-client-core:3.1.2")
    implementation("io.ktor:ktor-client-java:3.1.2")
    implementation("org.seleniumhq.selenium:selenium-edge-driver:4.20.0")
    implementation("com.fleeksoft.ksoup:ksoup:0.2.3")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}


// 清除现有的lib目录
tasks.register<Delete>("clearJar") {
    delete(layout.buildDirectory.dir("libs/lib"))
}
// 清除现有的res目录
tasks.register<Delete>("clearRes") {
    delete(layout.buildDirectory.dir("libs/res"))
}

// 将依赖包复制到lib目录
tasks.register<Copy>("copyJar") {
    dependsOn("clearJar")
    from(configurations.runtimeClasspath)
    into(layout.buildDirectory.dir("libs/lib"))
}

// 将资源文件复制到res目录
tasks.register<Copy>("copyRes") {
    dependsOn("clearRes")
    from("src/main/resources")
    into(layout.buildDirectory.dir("libs/res"))
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.bootJar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    // 排除所有的jar
    exclude("*.jar")
    exclude("assets/**")
    exclude("static/**")
    exclude("application.properties")

    // lib目录的清除和复制任务
    dependsOn("clearJar", "clearRes")
    dependsOn("copyJar", "copyRes")

    // 指定依赖包的路径
    manifest {
        attributes(
            "Manifest-Version" to "1.0",
            "Class-Path" to configurations.runtimeClasspath.get().files.joinToString(" ") {
                "lib/${it.name}"
            } + " res/"
        )
    }
    finalizedBy("generateLibHash")
}

// 确保processResources任务不会将资源文件复制到classes目录
tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.register("doPatchForLibHash") {
    inputs.files(layout.buildDirectory.file("build.hash"), fileTree("${layout.buildDirectory.get()}/libs"))
    doLast {
        //获取旧的md5值分组
        val oldFileMap = layout.buildDirectory.file("build.hash").get().asFile.bufferedReader()
            .lineSequence().chunked(2).associate {
                it[0] to it[1]
            }
        //遍历新的libs
        layout.buildDirectory.file("libs").get().asFile.walkTopDown().forEach { newFile ->
            //获取旧的MD5，key为新文件的相对路径
            val oldMD5 =
                oldFileMap[newFile.relativeTo(layout.buildDirectory.dir("libs").get().asFile).path] ?: return@forEach
            //比对，一样则删除
            if (oldMD5 == newFile.md5Digest().toHexString()) {
                newFile.delete()
            }
        }

        fun deleteEmptyDirectories(directory: File): Boolean {
            if (!directory.isDirectory) {
                return false
            }

            var success = true
            directory.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    success = deleteEmptyDirectories(file) && success
                }
            }

            if (success && directory.listFiles()?.isEmpty() == true) {
                if (!directory.delete()) {
                    success = false
                }
            }

            return success
        }

        deleteEmptyDirectories(layout.buildDirectory.file("libs").get().asFile)
    }
}

tasks.register("generateLibHash") {
    inputs.files(fileTree("${layout.buildDirectory.get()}/libs"))
    outputs.file(layout.buildDirectory.file("libs/build.hash"))

    doLast {
        val out = layout.buildDirectory.dir("libs/build.hash").get().asFile
        out.delete()
        out.parentFile.mkdirs()
        out.createNewFile()
        with(layout.buildDirectory.dir("libs").get().asFile) {
            walkTopDown().filter { it.isDirectory.not() }.forEach {
                val md5 = it.md5Digest().toHexString()
                val path = it.relativeTo(this).path
                out.appendText(
                    """
                    $path
                    $md5

                """.trimIndent()
                )
            }
        }
    }
}


fun File.md5Digest(): ByteArray {
    val md = MessageDigest.getInstance("MD5")
    val fis = FileInputStream(this)
    val buffer = ByteArray(1024)
    var numRead: Int
    while (fis.read(buffer).also { numRead = it } > 0) {
        md.update(buffer, 0, numRead)
    }
    fis.close()
    return md.digest()
}

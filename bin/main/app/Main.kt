package app

import agent.Agent
import config.Config
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val configPath = if (args.isNotEmpty()) args[0] else File("../config.json").absolutePath
    val cfg = try {
        Config.load(configPath)
    } catch (e: Exception) {
        System.err.println("Failed to load config: ${e.message}")
        exitProcess(1)
    }

    val agent = Agent(cfg)
    agent.start()

    Runtime.getRuntime().addShutdownHook(Thread { agent.stop() })
}


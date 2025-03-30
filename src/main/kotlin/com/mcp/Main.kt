package com.mcp

import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.utils.io.streams.*
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.Database

val POSTGRES_SERVER = Server(
    serverInfo = Implementation(
        name = "postgres-sse-server",
        version = "0.0.1"
    ),
    options = ServerOptions(
        capabilities = ServerCapabilities(
            prompts = ServerCapabilities.Prompts(
                listChanged = false
            ),
            resources = ServerCapabilities.Resources(
                subscribe = false,
                listChanged = false
            ),
            tools = ServerCapabilities.Tools(
                listChanged = false
            )
        )
    )
)

/**
 * @author violet
 * @since 2025/3/29
 */
fun main(args: Array<String>) {
    val parser = ArgParser("mcp-server")
    val databaseUrl by parser.option(
        ArgType.String,
        shortName = "db", description = "database url"
    ).required()
    val username by parser.option(
        ArgType.String,
        shortName = "u", description = "username"
    )
    val password by parser.option(
        ArgType.String,
        shortName = "p", description = "password"
    )
    val database by parser.option(
        ArgType.String,
        shortName = "d", description = "database name"
    )
    parser.parse(args)
    DATABASE_URL = databaseUrl
    USERNAME = username ?: "postgres"
    PASSWORD = password ?: ""
    DATABASE = database ?: "postgres"
    runSseServerUsingKtorPlugin(3000)
//    runStdio()
}

fun runStdio() {
    println("Starting sse server on stdin")
    println("Use inspector to connect to the stdin")
    val transport = StdioServerTransport(
        System.`in`.asInput(),
        System.out.asSink().buffered()
    )
    runBlocking {
        Database.connect(
            "jdbc:postgresql://${DATABASE_URL}/${DATABASE}",
            driver = "org.postgresql.Driver",
            user = USERNAME,
            password = PASSWORD
        )
        registerMCPServer()
        POSTGRES_SERVER.connect(transport)
        val done = Job()
        POSTGRES_SERVER.onClose {
            done.complete()
        }
        done.join()
    }
}

fun runSseServerUsingKtorPlugin(port: Int): Unit = runBlocking {
    println("Starting sse server on port $port")
    println("Use inspector to connect to the http://localhost:$port/sse")
    embeddedServer(CIO, port, host = "0.0.0.0") {
        configureDatabase()
        module()
    }.start(wait = true)
}

fun Application.configureDatabase() {
    println("Connecting to database at: $DATABASE_URL")
    try {
        Database.connect(
            "jdbc:postgresql://${DATABASE_URL}/${DATABASE}",
            driver = "org.postgresql.Driver",
            user = USERNAME,
            password = PASSWORD
        )
        println("Database connection successful")
    } catch (e: Exception) {
        println("Failed to connect to database: ${e.message}")
        throw e
    }
}

fun Application.module() {
    registerMCPServer()
    // 将 MCP 服务器注册在根路径
    mcp {
        POSTGRES_SERVER
    }
}

fun registerMCPServer() {
    // 注册 MCP 资源处理器
//    POSTGRES_SERVER.setRequestHandler(Method.Defined.ResourcesList) { _: ListResourcesRequest, _ ->
//        println("Received ResourcesList request")
//        val resources = table()
//        resources ?: ListResourcesResult(emptyList())
//    }
//    POSTGRES_SERVER.setRequestHandler(Method.Defined.ResourcesRead) { request: ReadResourceRequest, extra ->
//        println("Received ResourcesRead request")
//        ddl(request)
//    }
    POSTGRES_SERVER.addTool(
        name = "query",
        description = "Run a read-only query",
        inputSchema = Tool.Input(
            properties = JsonObject(
                mapOf(
                    "sql" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("The SQL query to run")
                        )
                    )
                )
            ),
            required = listOf("sql")
        )
    ) {
        val sql = it.arguments["sql"]?.jsonPrimitive?.content
        if (sql == null) {
            return@addTool CallToolResult(
                content = listOf(TextContent("The 'sql' argument is required")),
            )
        }
        query(sql)
    }
}
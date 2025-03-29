package com.mcp

import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.net.URI

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
    parser.parse(args)
    DATABASE_URL = databaseUrl
    USERNAME = username ?: "postgres"
    PASSWORD = password ?: ""
    runSseServerUsingKtorPlugin(3000)
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
            "jdbc:postgresql://${DATABASE_URL}",
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
    // 注册 MCP 资源处理器
    POSTGRES_SERVER.setRequestHandler(Method.Defined.ResourcesList) { _: ListResourcesRequest, _ ->
        println("Received ResourcesList request")
        val resources = transaction {
            try {
                exec("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'") { rs ->
                    val tables = mutableListOf<Resource>()
                    while (rs.next()) {
                        val tableName = rs.getString("table_name")
                        println("Found table: $tableName")
                        tables.add(
                            Resource(
                                uri = "postgres://$tableName/schema",
                                mimeType = "application/json",
                                name = "$tableName database schema",
                                description = "The public schema of the $tableName table"
                            )
                        )
                    }
                    println("Total tables found: ${tables.size}")
                    ListResourcesResult(resources = tables)
                }
            } catch (e: Exception) {
                println("Error executing query: ${e.message}")
                ListResourcesResult(emptyList())
            }
        }
        resources ?: ListResourcesResult(emptyList())
    }
    // 将 MCP 服务器注册在根路径
    mcp {
        POSTGRES_SERVER
    }
}
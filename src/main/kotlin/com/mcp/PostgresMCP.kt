package com.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import io.modelcontextprotocol.kotlin.sdk.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.ResultSet
import java.sql.SQLException


lateinit var DATABASE_URL: String
lateinit var USERNAME: String
lateinit var PASSWORD: String
lateinit var DATABASE: String

val objectMapper = ObjectMapper()

fun table() =
    transaction {
        try {
            exec("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'") { rs ->
                val tables = mutableListOf<Resource>()
                while (rs.next()) {
                    val tableName = rs.getString("table_name")
                    tables.add(
                        Resource(
                            uri = "postgres://$DATABASE_URL/$tableName/schema",
                            mimeType = "application/json",
                            name = "$tableName database schema",
                            description = "The public schema of the $tableName table"
                        )
                    )
                }
                ListResourcesResult(resources = tables)
            }
        } catch (_: Exception) {
            ListResourcesResult(emptyList())
        }
    }

fun ddl(request: ReadResourceRequest) =
    transaction {
        val ddlContexts = mutableListOf<TextResourceContents>()
        val tableName = request.uri.split("/").last()
        exec(
            "SELECT column_name, data_type FROM information_schema.columns WHERE table_name = '${tableName}'"
        ) {
            while (it.next()) {
                val dataMap = resultSetToMap(it)
                ddlContexts.add(
                    TextResourceContents(
                        uri = request.uri,
                        mimeType = "application/json",
                        text = objectMapper.writeValueAsString(dataMap)
                    )
                )
            }
        }
        ReadResourceResult(ddlContexts)
    }

fun query(sql: String) =
    transaction {
        val queryResult = mutableListOf<PromptMessageContent>()
        exec(sql) {
            while (it.next()) {
                val dataMap = resultSetToMap(it)
                queryResult.add(
                    TextContent(
                        text = objectMapper.writeValueAsString(dataMap)
                    )
                )
            }
        }
        rollback()
        CallToolResult(queryResult, false)
    }

// 辅助方法：将ResultSet当前行转换为Map
@Throws(SQLException::class)
private fun resultSetToMap(rs: ResultSet): MutableMap<String, Any> {
    val row = mutableMapOf<String, Any>()
    val metadata = rs.metaData
    val columnCount = metadata.columnCount

    for (i in 1..columnCount) {
        val columnName = metadata.getColumnName(i)
        val value = rs.getObject(i)
        row.put(columnName, value)
    }
    return row
}
package com.mcp

import io.modelcontextprotocol.kotlin.sdk.Resource
import org.jetbrains.exposed.sql.transactions.transaction

lateinit var DATABASE_URL: String
lateinit var USERNAME: String
lateinit var PASSWORD: String

//fun ddl(tableName: String): List<Resource> {
//    transaction {
//        exec(
//            "SELECT column_name, data_type FROM information_schema.columns WHERE table_name = '${tableName}'"
//        )
//    }
//}
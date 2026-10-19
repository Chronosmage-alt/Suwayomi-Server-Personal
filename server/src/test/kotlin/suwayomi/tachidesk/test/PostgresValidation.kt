package suwayomi.tachidesk.test

import de.neonew.exposed.migrations.loadMigrationsFrom
import de.neonew.exposed.migrations.runMigrations
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import suwayomi.tachidesk.server.ServerConfig
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.util.Properties
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Only the opt-in runner creates this manifest. Never accepts an external JDBC URL. */
object PostgresValidation {
    private val manifest: Properties? by lazy {
        System.getenv("SUWAYOMI_PG_MANIFEST")?.takeIf { System.getProperty("suwayomi.postgresValidation") == "true" }?.let { name ->
            Properties().apply { Files.newInputStream(Path.of(name)).use { load(it) } }
        }
    }
    val enabled get() = manifest != null
    val restarting get() = System.getenv("SUWAYOMI_PG_PHASE") == "restart"
    private val properties get() = requireNotNull(manifest)
    private val root get() = Path.of(properties.getProperty("root")).toRealPath()

    fun configure(): String? {
        if (!enabled) return null
        require(System.getenv("SUWAYOMI_PG_PHASE") in listOf("prepare", "restart"))
        blockExternalConnections()
        require(root.fileName.toString().matches(Regex("suwayomi-pg-[a-f0-9]{32}")))
        require(Files.readString(root.resolve("owner.token")).trim() == properties.getProperty("token"))
        val data = root.resolve("app").also { Files.createDirectories(it) }
        // Do not inherit an installation's paths, database configuration, or proxy settings.
        System.getProperties().keys.toList().filter { it.toString().startsWith("suwayomi.tachidesk.config.") }.forEach {
            System.clearProperty(it.toString())
        }
        mapOf(
            "rootDir" to data.toString(),
            "databaseType" to "POSTGRESQL",
            "databaseUrl" to url("upgrade").removePrefix("jdbc:"),
            "databaseUsername" to properties.getProperty("user"),
            "databasePassword" to properties.getProperty("password"),
            "databaseSchema" to "suwayomi",
            "downloadsPath" to data.resolve("downloads").toString(),
            "localSourcePath" to data.resolve("local").toString(),
            "socksProxyEnabled" to "false",
        ).forEach { (key, value) -> System.setProperty("suwayomi.tachidesk.config.server.$key", value) }
        // No mounted or ambient Komga secret is ever read by this harness.
        System.setProperty("suwayomi.komga.apiKeyFile", root.resolve("no-komga-secret").toString())
        connection("upgrade").close()
        return data.toString()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    private fun blockExternalConnections() {
        System.setSecurityManager(
            object : SecurityManager() {
                override fun checkPermission(permission: java.security.Permission) = Unit

                override fun checkConnect(
                    host: String,
                    port: Int,
                ) {
                    check(host in setOf("127.0.0.1", "localhost", "::1", "0:0:0:0:0:0:0:1")) {
                        "PostgreSQL validation blocks all non-loopback connections"
                    }
                }
            },
        )
    }

    private fun url(kind: String): String {
        require(kind in listOf("fresh", "upgrade"))
        val port = properties.getProperty("port").toInt().also { require(it in 1024..65535) }
        val name = properties.getProperty(kind)
        require(name.matches(Regex("suwayomi_pg_[a-f0-9]{32}_$kind")))
        require(properties.getProperty("user").matches(Regex("suwayomi_pg_[a-f0-9]{32}")))
        return "jdbc:postgresql://127.0.0.1:$port/$name?connectTimeout=5&socketTimeout=30"
    }

    fun connection(kind: String = "upgrade"): Connection =
        DriverManager.getConnection(url(kind), properties.getProperty("user"), properties.getProperty("password")).also { connection ->
            try {
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT token FROM public.harness_owner").use {
                        check(it.next() && it.getString(1) == properties.getProperty("token")) { "Not a harness-owned database" }
                    }
                    statement.execute("SET search_path TO suwayomi, public")
                }
            } catch (failure: Throwable) {
                connection.close()
                throw failure
            }
        }

    fun beforeMigration(db: Database) {
        if (!enabled || restarting) return
        connection().use { it.createStatement().use { statement -> statement.execute("CREATE SCHEMA suwayomi") } }
        runMigrations(
            loadMigrationsFrom("suwayomi.tachidesk.server.database.migration", ServerConfig::class.java).filter { it.version <= 69 },
        )
        connection().use { connection ->
            assertFalse(tableExists(connection, "komga_link"))
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT max(version) FROM migrations").use { result ->
                    check(result.next())
                    assertEquals(69, result.getInt(1))
                }
            }
            connection.createStatement().use { statement ->
                listOf(
                    "INSERT INTO manga (id,url,title,source) VALUES (900001,'/fixture','Preserved manga',0)",
                    "INSERT INTO chapter (id,manga,url,name,source_order) VALUES (900001,900001,'/chapter','Preserved chapter',0)",
                    "INSERT INTO mangauser (manga,user_id,in_library) VALUES (900001,1,true)",
                    "INSERT INTO chapteruser (chapter,user_id,read,bookmark,last_page_read,is_downloaded) VALUES (900001,1,true,true,17,true)",
                    "INSERT INTO category (id,name,user_id) VALUES (900001,'Completed fixture',1)",
                    "INSERT INTO adaptive_source_policy VALUES (987654,'fixture.provider',true)",
                    "INSERT INTO adaptive_manga_state VALUES (900001,'FOLLOW_SOURCE','{\"history\":[1,2,3]}')",
                    "INSERT INTO completed_category_rule VALUES (1,true,true,900001)",
                    "INSERT INTO completed_category_handled VALUES (1,900001,900001)",
                ).forEach { statement.execute(it) }
            }
            Files.writeString(root.resolve("before-70.snapshot"), snapshot(connection))
        }
        TransactionManager.defaultDatabase = db
    }

    fun afterMigration(db: Database) {
        if (!enabled) return
        connection().use { connection ->
            assertTrue(tableExists(connection, "komga_link"))
            assertTrue(tableExists(connection, "komga_connection"))
            if (!restarting) assertEquals(Files.readString(root.resolve("before-70.snapshot")), snapshot(connection))
        }
        TransactionManager.defaultDatabase = db
    }

    fun tableExists(
        connection: Connection,
        table: String,
    ): Boolean =
        connection
            .prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='suwayomi' AND table_name=?)",
            ).use {
                it.setString(1, table)
                it.executeQuery().use { result ->
                    result.next()
                    result.getBoolean(1)
                }
            }

    /** All legacy rows, including adaptive history, flags and manual-removal markers. */
    fun snapshot(connection: Connection): String {
        val tables =
            connection.createStatement().use { statement ->
                statement
                    .executeQuery(
                        "SELECT table_name FROM information_schema.tables WHERE table_schema='suwayomi' ORDER BY table_name",
                    ).use { result ->
                        buildList { while (result.next()) add(result.getString(1)) }
                    }
            }
        return tables.filter { !it.startsWith("komga_") && !it.contains("migration") }.joinToString("\n") { table ->
            require(table.matches(Regex("[a-z0-9_]+")))
            val rows =
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT to_jsonb(t)::text FROM suwayomi.\"$table\" t ORDER BY to_jsonb(t)::text").use { result ->
                        buildList { while (result.next()) add(result.getString(1)) }
                    }
                }
            "$table:$rows"
        }
    }

    fun freshDatabase(): Database =
        Database.connect(
            url("fresh"),
            "org.postgresql.Driver",
            properties.getProperty("user"),
            properties.getProperty("password"),
            databaseConfig =
                org.jetbrains.exposed.v1.core.DatabaseConfig {
                    @OptIn(org.jetbrains.exposed.v1.core.ExperimentalKeywordApi::class)
                    preserveKeywordCasing = false
                    defaultSchema =
                        org.jetbrains.exposed.v1.core
                            .Schema("suwayomi")
                },
        )
}

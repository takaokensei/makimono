package zechs.drive.stream.backend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PairingServerConfigurationTest {

    @Test
    fun `local development stays loopback only and uses the non routable preview origin`() {
        val config = PairingServerConfiguration.from(emptyMap())

        assertEquals("127.0.0.1", config.host)
        assertEquals(8097, config.port)
        assertEquals("https://pairing.invalid", config.publicBaseUrl)
        assertEquals(8, config.databasePoolSize)
    }

    @Test
    fun `cloud run uses the injected port public origin and a small postgres pool`() {
        val config = PairingServerConfiguration.from(
            cloudRunEnvironment() + mapOf(
                "PORT" to "8081",
                "MAKIMONO_PAIRING_PUBLIC_BASE_URL" to "https://makimono-12345.us-east1.run.app/"
            )
        )

        assertEquals("0.0.0.0", config.host)
        assertEquals(8081, config.port)
        assertEquals("https://makimono-12345.us-east1.run.app", config.publicBaseUrl)
        assertEquals(2, config.databasePoolSize)
    }

    @Test
    fun `cloud run refuses to start without an HTTPS origin and persistent database`() {
        assertFailsWith<IllegalArgumentException> {
            PairingServerConfiguration.from(cloudRunEnvironment().filterKeys { it != "MAKIMONO_PAIRING_PUBLIC_BASE_URL" })
        }
        assertFailsWith<IllegalArgumentException> {
            PairingServerConfiguration.from(
                cloudRunEnvironment().filterKeys { it != "MAKIMONO_DATABASE_PASSWORD" }
            )
        }
    }

    @Test
    fun `public origin must be HTTPS and cannot contain a path or credentials`() {
        listOf(
            "http://pair.example.com",
            "https://user:password@pair.example.com",
            "https://pair.example.com/callback",
            "https://pair.example.com?next=/"
        ).forEach { url ->
            assertFailsWith<IllegalArgumentException>(url) {
                PairingServerConfiguration.from(mapOf("MAKIMONO_PAIRING_PUBLIC_BASE_URL" to url))
            }
        }
    }

    @Test
    fun `partial local database configuration is rejected instead of silently using memory`() {
        assertFailsWith<IllegalArgumentException> {
            PairingServerConfiguration.from(mapOf("MAKIMONO_DATABASE_URL" to "jdbc:postgresql://localhost/test"))
        }
    }

    private fun cloudRunEnvironment(): Map<String, String> = mapOf(
        "K_SERVICE" to "makimono-pairing",
        "MAKIMONO_PAIRING_PUBLIC_BASE_URL" to "https://makimono-12345.us-east1.run.app",
        "MAKIMONO_DATABASE_URL" to "jdbc:postgresql://db.example.com/makimono?sslmode=require",
        "MAKIMONO_DATABASE_USER" to "makimono",
        "MAKIMONO_DATABASE_PASSWORD" to "test-only"
    )
}

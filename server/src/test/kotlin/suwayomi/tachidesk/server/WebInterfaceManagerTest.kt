package suwayomi.tachidesk.server

import kotlinx.coroutines.runBlocking
import suwayomi.tachidesk.graphql.types.WebUIFlavor
import suwayomi.tachidesk.server.util.WebInterfaceManager
import suwayomi.tachidesk.test.ApplicationTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WebInterfaceManagerTest : ApplicationTest() {
    @Test
    fun customWebUIUpdateCheckDoesNotFetchReleaseMapping() =
        runBlocking<Unit> {
            // With raiseError=true the previous path throws on Custom's relative
            // version-mapping URL. A custom UI must remain externally managed.
            assertEquals(
                "r3549" to false,
                WebInterfaceManager.isUpdateAvailable(WebUIFlavor.CUSTOM, currentVersion = "r3549", raiseError = true),
            )
        }
}

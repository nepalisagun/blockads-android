package app.pwhs.blockads.ui.config

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.pwhs.blockads.data.dao.ConfigDao
import app.pwhs.blockads.data.entities.ConfigProfile
import app.pwhs.blockads.ui.MainDispatcherRule
import app.pwhs.blockads.ui.keepHot
import io.ktor.client.HttpClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConfigViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val configDao: ConfigDao = mockk(relaxed = true)
    private val httpClient: HttpClient = mockk(relaxed = true)

    private val defaultConfig = ConfigProfile(
        id = 1,
        name = ConfigProfile.DEFAULT_NAME,
        isActive = true,
        isBuiltIn = true
    )

    @Before
    fun setUp() {
        coEvery { configDao.getCount() } returns 1
        coEvery { configDao.getAllFlow() } returns flowOf(listOf(defaultConfig))
        coEvery { configDao.getActiveFlow() } returns flowOf(defaultConfig)
    }

    private fun createViewModel() = ConfigViewModel(
        configDao = configDao,
        client = httpClient,
        application = app
    )

    @Test
    fun `init seeds default configuration when empty`() = runTest {
        coEvery { configDao.getCount() } returns 0

        createViewModel()

        coVerify(exactly = 1) {
            configDao.insert(match { it.name == ConfigProfile.DEFAULT_NAME && it.isBuiltIn })
        }
    }

    @Test
    fun `init does not seed when configs exist`() = runTest {
        coEvery { configDao.getCount() } returns 2

        createViewModel()

        coVerify(exactly = 0) {
            configDao.insert(any())
        }
    }

    @Test
    fun `SelectActive intent triggers setActive in dao`() = runTest {
        val vm = createViewModel()
        vm.onIntent(ConfigUiIntent.SelectActive(42L))

        coVerify(exactly = 1) {
            configDao.setActive(42L)
        }
    }

    @Test
    fun `AddLocalConfig intent inserts new config`() = runTest {
        val vm = createViewModel()
        vm.onIntent(ConfigUiIntent.AddLocalConfig("My Rules", "[filter_local]\ndirect\n"))

        coVerify(exactly = 1) {
            configDao.insert(match { it.name == "My Rules" && it.content.contains("direct") })
        }
    }

    @Test
    fun `DeleteConfig does not delete built-in config`() = runTest {
        val vm = createViewModel()
        vm.onIntent(ConfigUiIntent.DeleteConfig(defaultConfig))

        coVerify(exactly = 0) {
            configDao.delete(any())
        }
    }

    @Test
    fun `DeleteConfig deletes user config`() = runTest {
        val userConfig = ConfigProfile(id = 2, name = "Custom", isBuiltIn = false)
        val vm = createViewModel()
        vm.onIntent(ConfigUiIntent.DeleteConfig(userConfig))

        coVerify(exactly = 1) {
            configDao.delete(userConfig)
        }
    }

    @Test
    fun `ShowAddDialog and DismissAddDialog update uiState`() = runTest {
        val vm = createViewModel()
        assertFalse(vm.uiState.value.showAddDialog)

        vm.onIntent(ConfigUiIntent.ShowAddDialog)
        assertTrue(vm.uiState.value.showAddDialog)

        vm.onIntent(ConfigUiIntent.DismissAddDialog)
        assertFalse(vm.uiState.value.showAddDialog)
    }
}

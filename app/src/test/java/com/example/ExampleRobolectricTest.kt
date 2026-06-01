package com.example

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.MekanikRepository
import com.example.ui.MekanikViewModel
import com.example.ui.theme.MekanikAITheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Mekanik AI", appName)
  }

  @Test
  fun `test full app rendering and initialization`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val application = context as Application
    val database = AppDatabase.getDatabase(context)
    val repository = MekanikRepository(
        vehicleDao = database.vehicleDao(),
        diagnosticScanDao = database.diagnosticScanDao(),
        dtcRecordDao = database.dtcRecordDao()
    )
    val viewModel = MekanikViewModel(application, repository)

    composeTestRule.setContent {
      MekanikAITheme {
        MekanikAppShell(viewModel)
      }
    }
    composeTestRule.waitForIdle()
  }
}

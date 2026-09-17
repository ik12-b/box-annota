package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.ui.components.AnnotatorToolbar
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme {
        AnnotatorToolbar(
          currentPage = 1,
          totalPages = 3,
          canUndo = true,
          canRedo = false,
          onUndo = {},
          onRedo = {},
          onFirstPage = {},
          onPrevPage = {},
          onNextPage = {},
          onLastPage = {},
          onAutoAdvance = {},
          onRotateLeft = {},
          onRotateRight = {},
          onOpenPdf = {},
          onExportDataset = {},
          onSaveProject = {},
          onToggleLeftDrawer = {},
          onToggleRightDrawer = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}


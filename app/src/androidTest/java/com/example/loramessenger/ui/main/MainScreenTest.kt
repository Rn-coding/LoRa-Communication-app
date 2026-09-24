package com.example.loramessenger.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** UI tests for [com.example.loramessenger.ui.main.MainScreen]. */
class MainScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun setup() {
    composeTestRule.setContent { MainScreen(onItemClick = {}) }
  }

  @Test
  fun permissionGate_or_relayShell_exists() {
    composeTestRule.onNodeWithText("Grant Emergency Bluetooth Permissions").assertExists()
  }
}

package org.qbitx.wallet

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.*
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qbitx.wallet.ui.LanguagePreferences

@RunWith(AndroidJUnit4::class)
class FirstLaunchLanguageTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Test fun firstLaunchShowsLanguageChoiceBeforeWalletUnlock() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val previous=LanguagePreferences.selected(context)
        context.getSharedPreferences("qbx-interface",0).edit().clear().commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                compose.onNodeWithText(context.getString(R.string.language_title)).assertIsDisplayed()
                compose.onNodeWithText("English").assertIsDisplayed()
                compose.onNodeWithText("Русский").assertIsDisplayed()
            }
        } finally {
            if(previous!=null) LanguagePreferences.select(context,previous)
        }
    }
}

package org.qbitx.wallet

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.qbitx.wallet.ui.*
import org.qbitx.wallet.ui.theme.QBitXWalletTheme

@RunWith(AndroidJUnit4::class)
class LanguageChooserUiTest {
    @get:Rule val compose=createAndroidComposeRule<AuditPreviewActivity>()
    @Test fun koreanJapaneseAndThaiCanBeSelectedFromTheScrollablePicker() {
        val context=compose.activity
        val prefs=context.getSharedPreferences("qbx-interface",0)
        val previous=prefs.getString("language",null)
        var selected:String?=null
        try {
            compose.setContent { QBitXWalletTheme { LanguageScreen { tag ->
                LanguagePreferences.select(context,tag);selected=tag
            } } }
            for(tag in listOf("ko","ja","th")) {
                val index=LanguagePreferences.languages.indexOfFirst { it.first==tag }
                assertTrue(index>=0)
                compose.onNode(hasScrollToIndexAction()).performScrollToIndex(index)
                compose.onNodeWithText(LanguagePreferences.languages[index].second).assertIsDisplayed().performClick()
                compose.runOnIdle {
                    assertEquals(tag,selected)
                    assertEquals(tag,LanguagePreferences.selected(context))
                }
            }
        } finally { if(previous==null) prefs.edit().clear().commit() else prefs.edit().putString("language",previous).commit() }
    }
}

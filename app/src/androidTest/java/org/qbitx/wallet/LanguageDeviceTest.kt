package org.qbitx.wallet

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.qbitx.wallet.ui.LanguagePreferences

@RunWith(AndroidJUnit4::class)
class LanguageDeviceTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun allFifteenLocalesLoadSecurityTextAndFormatParameters() {
        assertEquals(15,LanguagePreferences.languages.size)
        val english=LanguagePreferences.localized(context,"en").getString(R.string.safety_explanation_1)
        LanguagePreferences.languages.forEach { (tag,_) ->
            val localized=LanguagePreferences.localized(context,tag)
            val safety=localized.getString(R.string.safety_explanation_1)
            assertTrue(safety.isNotBlank())
            if(tag!="en") assertNotEquals("Missing translation: $tag",english,safety)
            assertTrue(localized.getString(R.string.numbered_address,3,"witness").contains(java.text.NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag(tag)).format(3)))
            assertTrue(localized.getString(R.string.dat_warning_title).contains("wallet.dat"))
            if(tag in listOf("ar","ur")) assertEquals(android.view.View.LAYOUT_DIRECTION_RTL,localized.resources.configuration.layoutDirection)
        }
    }
    @Test fun selectedLanguagePersistsAcrossNewContexts() {
        val previous=LanguagePreferences.selected(context)
        try {
            LanguagePreferences.select(context,"hi")
            assertEquals("hi",LanguagePreferences.selected(context.createConfigurationContext(context.resources.configuration)))
            assertEquals(LanguagePreferences.localized(context,"hi").getString(R.string.language_title),LanguagePreferences.wrap(context).getString(R.string.language_title))
        } finally {
            if(previous==null) context.getSharedPreferences("qbx-interface",0).edit().clear().commit()
            else LanguagePreferences.select(context,previous)
        }
    }
}

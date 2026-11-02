package org.qbitx.wallet

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import android.graphics.*
import java.io.File

@RunWith(AndroidJUnit4::class)
class LauncherIconTest {
    @Test fun adaptiveIconHasVisibleInkAndSafePadding() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val icon=context.getDrawable(R.mipmap.ic_launcher)!!
        val bitmap=Bitmap.createBitmap(192,192,Bitmap.Config.ARGB_8888)
        icon.setBounds(0,0,192,192);icon.draw(Canvas(bitmap))
        val ink=mutableListOf<Pair<Int,Int>>()
        for(y in 0..191) for(x in 0..191) {
            val c=bitmap.getPixel(x,y)
            if(Color.alpha(c)>200 && (Color.green(c)>180 && Color.red(c)<80 || Color.red(c)>220 && Color.green(c)>220 && Color.blue(c)>220)) ink.add(x to y)
        }
        assertTrue(ink.size>100)
        assertTrue(ink.minOf { it.first }>8 && ink.maxOf { it.first }<183)
        assertTrue(ink.minOf { it.second }>8 && ink.maxOf { it.second }<183)
        File(context.getExternalFilesDir(null),"launcher-preview.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
}

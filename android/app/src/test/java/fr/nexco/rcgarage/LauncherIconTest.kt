package fr.nexco.rcgarage

import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.InsetDrawable
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class LauncherIconTest {
    @Test fun applicationAndRoundIconsLoadTheOfficialAdaptiveArtwork() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val applicationIcon = context.applicationInfo.loadIcon(context.packageManager)
        assertEquals(R.mipmap.ic_launcher, context.applicationInfo.icon)
        assertTrue(applicationIcon is AdaptiveIconDrawable)
        val roundIcon = context.getDrawable(R.mipmap.ic_launcher_round)
        assertTrue(roundIcon is AdaptiveIconDrawable)
        for (icon in listOf(applicationIcon, roundIcon)) {
            val foreground = (icon as AdaptiveIconDrawable).foreground
            assertTrue(foreground is InsetDrawable)
            val artwork = (foreground as InsetDrawable).drawable as BitmapDrawable
            // nodpi preserves the complete original; XML margins handle launcher masks.
            assertEquals(1254, artwork.bitmap.width)
            assertEquals(1254, artwork.bitmap.height)
        }
    }
}

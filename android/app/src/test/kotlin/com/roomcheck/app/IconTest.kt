package com.roomcheck.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

/**
 * The launcher icon as Android actually composites it: the background vector under the foreground,
 * cropped to the inner 72 of the 108 canvas and masked. Drawing it in a browser proves the SVG is
 * right; this proves the Android resources are - that the gradients parse and the layers line up.
 */
class IconTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    @Test
    fun launcherIcon() {
        paparazzi.snapshot(name = "launcher") {
            Row(Modifier.background(Color(0xFFE9E9EE)).padding(16.dp)) {
                listOf(
                    160.dp to RoundedCornerShape(percent = 22),
                    160.dp to CircleShape,
                    72.dp to RoundedCornerShape(percent = 22),
                    48.dp to CircleShape
                ).forEach { (size, shape) ->
                    Box(Modifier.padding(8.dp).size(size).clip(shape)) {
                        // scale 1.5x and centre-crop = showing the inner 72 of the 108 canvas
                        val layer = Modifier.fillMaxSize().scaleToSafeZone()
                        Image(painterResource(R.drawable.ic_launcher_background), null, layer, contentScale = ContentScale.Crop)
                        Image(painterResource(R.mipmap.ic_launcher_foreground), null, layer, contentScale = ContentScale.Crop)
                    }
                }
            }
        }
    }

    /** Themed icons tint the monochrome layer flat; it has to survive losing every colour. */
    @Test
    fun themedIcon() {
        paparazzi.snapshot(name = "launcher-themed") {
            Row(Modifier.background(Color(0xFFE9E9EE)).padding(16.dp)) {
                listOf(160.dp, 72.dp, 48.dp).forEach { size ->
                    Box(
                        Modifier.padding(8.dp).size(size).clip(CircleShape).background(Color(0xFFCBD3F2))
                    ) {
                        Image(
                            painterResource(R.drawable.ic_launcher_monochrome), null,
                            Modifier.fillMaxSize().scaleToSafeZone(),
                            contentScale = ContentScale.Crop,
                            colorFilter = ColorFilter.tint(Color(0xFF23305E))
                        )
                    }
                }
            }
        }
    }
}

/** 108/72 - the crop every launcher applies before masking. */
private fun Modifier.scaleToSafeZone() = this.then(
    Modifier.layout { measurable, constraints ->
        val k = 108f / 72f
        val placeable = measurable.measure(
            constraints.copy(
                minWidth = (constraints.maxWidth * k).toInt(), maxWidth = (constraints.maxWidth * k).toInt(),
                minHeight = (constraints.maxHeight * k).toInt(), maxHeight = (constraints.maxHeight * k).toInt()
            )
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.place(
                (constraints.maxWidth - placeable.width) / 2,
                (constraints.maxHeight - placeable.height) / 2
            )
        }
    }
)

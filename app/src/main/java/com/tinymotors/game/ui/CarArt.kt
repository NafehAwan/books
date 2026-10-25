package com.tinymotors.game.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.tinymotors.game.R
import com.tinymotors.game.engine.Cars

/** One car's art and colours. Tile colours are generated from the picture by tools/process_art.py. */
class CarLook(
    val level: Int,
    val image: ImageBitmap,
    val nameRes: Int,
    val main: Color,
    val tile: Color,
    val edge: Color,
)

/** Decodes the 11 car pictures once and keeps them for the whole app. */
class CarArt(context: Context) {
    private val looks: List<CarLook>

    init {
        val opts = BitmapFactory.Options().apply { inScaled = false }
        looks = (Cars.MIN_LEVEL..Cars.MAX_LEVEL).map { level ->
            val i = level - 1
            val main = ContextCompat.getColor(context, MAIN[i])
            val bmp = BitmapFactory.decodeResource(context.resources, IMAGES[i], opts) ?: placeholder(main)
            CarLook(
                level = level,
                image = bmp.asImageBitmap().also { it.prepareToDraw() },
                nameRes = NAMES[i],
                main = Color(main),
                tile = Color(ContextCompat.getColor(context, TILE[i])),
                edge = Color(ContextCompat.getColor(context, EDGE[i])),
            )
        }
    }

    operator fun get(level: Int): CarLook = looks[level.coerceIn(Cars.MIN_LEVEL, Cars.MAX_LEVEL) - 1]

    val all: List<CarLook> get() = looks

    companion object {
        /** Simple drawn car, used only if a picture cannot be decoded. */
        fun placeholder(color: Int): android.graphics.Bitmap {
            val size = 256
            val bmp = createBitmap(size, size)
            val c = android.graphics.Canvas(bmp)
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            p.color = color
            c.drawRoundRect(28f, 110f, 228f, 180f, 30f, 30f, p)
            c.drawRoundRect(70f, 70f, 180f, 130f, 26f, 26f, p)
            p.color = 0xFFBFE3FF.toInt()
            c.drawRoundRect(84f, 82f, 166f, 118f, 14f, 14f, p)
            p.color = 0xFF2B3A55.toInt()
            c.drawCircle(78f, 182f, 24f, p)
            c.drawCircle(178f, 182f, 24f, p)
            p.color = 0xFFDDE5EE.toInt()
            c.drawCircle(78f, 182f, 10f, p)
            c.drawCircle(178f, 182f, 10f, p)
            return bmp
        }

        val IMAGES = intArrayOf(
            R.drawable.car_01_kei, R.drawable.car_02_city_hatch, R.drawable.car_03_retro_bug,
            R.drawable.car_04_sedan, R.drawable.car_05_estate, R.drawable.car_06_suv,
            R.drawable.car_07_pickup, R.drawable.car_08_coupe, R.drawable.car_09_sports_car,
            R.drawable.car_10_supercar, R.drawable.car_11_hypercar,
        )
        val NAMES = intArrayOf(
            R.string.car_01, R.string.car_02, R.string.car_03, R.string.car_04, R.string.car_05,
            R.string.car_06, R.string.car_07, R.string.car_08, R.string.car_09, R.string.car_10,
            R.string.car_11,
        )
        private val MAIN = intArrayOf(
            R.color.car_01_main, R.color.car_02_main, R.color.car_03_main, R.color.car_04_main,
            R.color.car_05_main, R.color.car_06_main, R.color.car_07_main, R.color.car_08_main,
            R.color.car_09_main, R.color.car_10_main, R.color.car_11_main,
        )
        private val TILE = intArrayOf(
            R.color.car_01_tile, R.color.car_02_tile, R.color.car_03_tile, R.color.car_04_tile,
            R.color.car_05_tile, R.color.car_06_tile, R.color.car_07_tile, R.color.car_08_tile,
            R.color.car_09_tile, R.color.car_10_tile, R.color.car_11_tile,
        )
        private val EDGE = intArrayOf(
            R.color.car_01_edge, R.color.car_02_edge, R.color.car_03_edge, R.color.car_04_edge,
            R.color.car_05_edge, R.color.car_06_edge, R.color.car_07_edge, R.color.car_08_edge,
            R.color.car_09_edge, R.color.car_10_edge, R.color.car_11_edge,
        )
    }
}

val LocalCarArt = staticCompositionLocalOf<CarArt> { error("CarArt not provided") }

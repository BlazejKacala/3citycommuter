package pl.bkacala.threecitycommuter.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter

fun Bitmap.changeColor(newColor: Int): Bitmap {
    val paint = Paint()
    val filter: ColorFilter = PorterDuffColorFilter(
        newColor,
        PorterDuff.Mode.SRC_IN
    )
    paint.colorFilter = filter

    val canvas = Canvas(this)
    canvas.drawBitmap(this, 0f, 0f, paint)
    return this
}
package com.example.cinefiles.ui.diary

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.style.QuoteSpan

/**
 * The bar and the indent of a pull-quote paragraph. It IS a [QuoteSpan], so `HtmlCompat.toHtml` writes it as `<blockquote>` and
 * `HtmlCompat.fromHtml` reads it back (RichText swaps the plain blue QuoteSpan that fromHtml makes for this one, in the page's accent color),
 * but it draws a thicker bar and a wider indent than the framework's QuoteSpan, which is too thin to read as a quote.
 */
class PullQuoteSpan(color: Int, val barWidth: Int, val gap: Int) : QuoteSpan(color) {

    override fun getLeadingMargin(first: Boolean): Int = barWidth + gap

    override fun drawLeadingMargin(
        canvas: Canvas, paint: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout
    ) {
        val oldStyle = paint.style
        val oldColor = paint.color
        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawRect(x.toFloat(), top.toFloat(), (x + dir * barWidth).toFloat(), bottom.toFloat(), paint)
        paint.style = oldStyle
        paint.color = oldColor
    }
}

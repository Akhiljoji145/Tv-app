package com.akhil.tvdocumentviewer

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.*
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.OpenableColumns
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.util.zip.ZipInputStream
import java.io.InputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private var pdfRenderer: PdfRenderer? = null
    private var pdfFd: ParcelFileDescriptor? = null
    private var pdfPage = 0
    private var currentPdfPageCount = 0
    private val blue = Color.rgb(40, 115, 230)
    private val dark = Color.rgb(15, 20, 31)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildHome()
    }

    private fun buildHome() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(42, 28, 42, 28)
            setBackgroundColor(dark)
            isFocusable = true
            isFocusableInTouchMode = true
        }
        val heading = TextView(this).apply {
            text = "TV DOCUMENT VIEWER"
            textSize = 27f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        root.addView(heading)
        root.addView(TextView(this).apply {
            text = "Open and read documents using your TV remote"
            textSize = 16f
            setTextColor(Color.LTGRAY)
            setPadding(0, 6, 0, 20)
        })
        val open = makeButton("OPEN DOCUMENT  •  USB / DEVICE STORAGE") { pickDocument() }
        root.addView(open, buttonParams())
        root.addView(TextView(this).apply {
            text = "SUPPORTED IN THIS VERSION"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(125, 180, 255))
            setPadding(0, 26, 0, 8)
        })
        val formats = TextView(this).apply {
            text = "PDF  •  DOCX  •  PPTX  •  XLSX  •  TXT  •  JPG  •  PNG\n\nPDF: full page viewing and page navigation\nOffice: text/content preview (not exact original formatting)\nUSB: browse through Android's file picker"
            textSize = 17f
            setTextColor(Color.WHITE)
            setLineSpacing(7f, 1f)
        }
        root.addView(formats)
        status = TextView(this).apply {
            text = "Use the D-pad to focus OPEN DOCUMENT, then press OK."
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, 24, 0, 0)
        }
        root.addView(status)
        setContentView(root)
        open.requestFocus()
    }

    private fun makeButton(label: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 17f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = rounded(blue)
            setPadding(22, 14, 22, 14)
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener { action() }
        }
    }

    private fun rounded(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = 18f
    }

    private fun buttonParams() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = 8 }

    private fun pickDocument() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "text/plain", "image/*"
            ))
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_OPEN)
    }

    @Deprecated("Legacy activity result API used for broad Android TV compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OPEN && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                catch (_: Exception) {}
                openDocument(uri)
            }
        }
    }

    private fun displayShell(title: String) {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 18, 28, 18)
            setBackgroundColor(dark)
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(makeButton("← HOME") { closePdf(); buildHome() }, LinearLayout.LayoutParams(150, 58))
        top.addView(TextView(this).apply {
            text = title
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(18, 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(top)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 16, 0, 0) }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(content) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun openDocument(uri: Uri) {
        val name = displayName(uri) ?: "Document"
        val lower = name.lowercase()
        try {
            when {
                lower.endsWith(".pdf") -> openPdf(uri, name)
                lower.endsWith(".docx") -> showText(name, extractDocx(uri), "Word document text preview")
                lower.endsWith(".pptx") -> showText(name, extractPptx(uri), "PowerPoint slide text preview")
                lower.endsWith(".xlsx") -> showText(name, extractXlsx(uri), "Excel cell text preview")
                lower.endsWith(".txt") -> showText(name, contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }, "Text document")
                lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp") -> showImage(name, uri)
                else -> showText(name, "This file type is not supported by the current viewer.\n\nSupported: PDF, DOCX, PPTX, XLSX, TXT, JPG and PNG.", "Unsupported format")
            }
        } catch (e: Exception) {
            showText(name, "Could not open this file.\n\n${e.message ?: "Unknown error"}", "Unable to open")
        }
    }

    private fun displayName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) return it.getString(0)
        }
        return uri.lastPathSegment
    }

    private fun openPdf(uri: Uri, name: String) {
        closePdf()
        pdfFd = contentResolver.openFileDescriptor(uri, "r")
        pdfRenderer = PdfRenderer(pdfFd!!)
        currentPdfPageCount = pdfRenderer!!.pageCount
        pdfPage = 0
        displayShell(name)
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        controls.addView(makeButton("◀ PREVIOUS") { renderPdfPage(pdfPage - 1) })
        controls.addView(makeButton("NEXT ▶") { renderPdfPage(pdfPage + 1) }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = 12 })
        val counter = TextView(this).apply {
            text = "  Page 1 / $currentPdfPageCount"
            textSize = 17f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        }
        controls.addView(counter)
        content.addView(controls)
        val image = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            tag = counter
        }
        content.addView(image, LinearLayout.LayoutParams(-1, 0, 1f))
        renderPdfPage(0)
        status.text = "PDF opened"
    }

    private fun renderPdfPage(index: Int) {
        val renderer = pdfRenderer ?: return
        if (index !in 0 until renderer.pageCount) return
        pdfPage = index
        val page = renderer.openPage(index)
        val width = 1500
        val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        val image = findImageView(root)
        image?.setImageBitmap(bitmap)
        val counter = findCounter(root)
        counter?.text = "  Page ${index + 1} / ${renderer.pageCount}"
    }

    private fun findImageView(v: View): ImageView? {
        if (v is ImageView) return v
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) findImageView(v.getChildAt(i))?.let { return it }
        return null
    }
    private fun findCounter(v: View): TextView? {
        if (v is TextView && v.text.toString().startsWith("  Page ")) return v
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) findCounter(v.getChildAt(i))?.let { return it }
        return null
    }

    private fun showText(name: String, body: String, subtitle: String) {
        closePdf()
        displayShell(name)
        content.addView(TextView(this).apply {
            text = subtitle
            textSize = 15f; setTextColor(Color.rgb(125,180,255))
            setPadding(0, 0, 0, 12)
        })
        content.addView(TextView(this).apply {
            text = body.ifBlank { "No readable text was found in this document." }
            textSize = 19f
            setTextColor(Color.WHITE)
            setLineSpacing(5f, 1f)
            setTextIsSelectable(true)
        })
    }

    private fun showImage(name: String, uri: Uri) {
        closePdf()
        displayShell(name)
        val image = ImageView(this).apply {
            setImageURI(uri)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        content.addView(image, LinearLayout.LayoutParams(-1, -1))
    }

    private fun closePdf() {
        try { pdfRenderer?.close() } catch (_: Exception) {}
        try { pdfFd?.close() } catch (_: Exception) {}
        pdfRenderer = null; pdfFd = null
    }

    private fun zipEntry(uri: Uri, entryName: String): String {
        val input = contentResolver.openInputStream(uri)!!
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == entryName) {
                    val out = ByteArrayOutputStream()
                    zip.copyTo(out)
                    return out.toString("UTF-8")
                }
            }
        }
        return ""
    }

    private fun xmlTexts(xml: String): List<String> {
        val result = mutableListOf<String>()
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)), "UTF-8")
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.TEXT) {
                val t = parser.text?.trim().orEmpty()
                if (t.isNotEmpty()) result.add(t)
            }
            event = parser.next()
        }
        return result
    }

    private fun extractDocx(uri: Uri): String {
        val xml = zipEntry(uri, "word/document.xml")
        return xmlTexts(xml).joinToString(" ")
    }

    private fun extractPptx(uri: Uri): String {
        val names = mutableListOf<String>()
        val input = contentResolver.openInputStream(uri)!!
        ZipInputStream(input).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (Regex("""ppt/slides/slide\d+\.xml""").matches(e.name)) names.add(e.name)
            }
        }
        names.sortBy { Regex("""slide(\d+)""").find(it)?.groupValues?.get(1)?.toIntOrNull() ?: 0 }
        return names.mapIndexed { i, entry ->
            "SLIDE ${i + 1}\n" + xmlTexts(zipEntry(uri, entry)).joinToString(" ")
        }.joinToString("\n\n")
    }

    private fun extractXlsx(uri: Uri): String {
        val sharedXml = zipEntry(uri, "xl/sharedStrings.xml")
        val shared = if (sharedXml.isNotEmpty()) {
            Regex("""<t[^>]*>(.*?)</t>""", RegexOption.DOT_MATCHES_ALL).findAll(sharedXml)
                .map { android.text.Html.fromHtml(it.groupValues[1], android.text.Html.FROM_HTML_MODE_LEGACY).toString() }.toList()
        } else emptyList()
        val sheet = zipEntry(uri, "xl/worksheets/sheet1.xml")
        if (sheet.isEmpty()) return "No worksheet data found."
        val rows = Regex("""<row[^>]*>(.*?)</row>""", setOf(RegexOption.DOT_MATCHES_ALL)).findAll(sheet)
        return rows.map { row ->
            Regex("""<c([^>]*)>(.*?)</c>""", RegexOption.DOT_MATCHES_ALL).findAll(row.groupValues[1]).joinToString("   ") { cell ->
                val attrs = cell.groupValues[1]
                val value = Regex("""<v>(.*?)</v>""", RegexOption.DOT_MATCHES_ALL).find(cell.groupValues[2])?.groupValues?.get(1) ?: ""
                if (attrs.contains("""t="s"""")) shared.getOrNull(value.toIntOrNull() ?: -1) ?: "" else value
            }
        }.joinToString("\n")
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (pdfRenderer != null) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_PAGE_UP -> { renderPdfPage(pdfPage - 1); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_PAGE_DOWN -> { renderPdfPage(pdfPage + 1); return true }
                KeyEvent.KEYCODE_BACK -> { closePdf(); buildHome(); return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() { closePdf(); super.onDestroy() }

    companion object { private const val REQUEST_OPEN = 1001 }
}

package com.example.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.core.CryptoVaultHelper
import com.example.model.DateUtils
import com.example.model.VaultDocument
import java.io.File
import java.io.FileOutputStream

object PdfGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_H = 48f
    private const val CONTENT_WIDTH = 499 // 595 - 48*2

    data class GeneratedGrayscalePdf(
        val pdfFile: File,
        val grayscaleBitmaps: List<Bitmap>,
        val previewImageFiles: List<File>,
        val pageCount: Int,
        val fileSizeBytes: Long
    )

    fun convertBitmapToGrayscale(source: Bitmap, enhanceContrast: Boolean = true): Bitmap {
        val out = Bitmap.createBitmap(
            source.width.coerceAtLeast(1),
            source.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(out)
        val matrix = ColorMatrix().apply {
            setSaturation(0f)
            if (enhanceContrast) {
                val contrast = 1.24f
                val brightness = 10f
                val translate = (-0.5f * contrast + 0.5f) * 255f + brightness
                postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            contrast, 0f, 0f, 0f, translate,
                            0f, contrast, 0f, 0f, translate,
                            0f, 0f, contrast, 0f, translate,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
        }
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return out
    }

    fun generateGrayscaleScanPdf(
        context: Context,
        pageBitmaps: List<Bitmap>,
        filePrefix: String = "scan_nb",
        enhanceContrast: Boolean = true
    ): GeneratedGrayscalePdf {
        require(pageBitmaps.isNotEmpty()) { "Au moins une image est requise pour générer un PDF." }

        val scanDir = File(context.cacheDir, "scans").apply { mkdirs() }
        val ts = System.currentTimeMillis()
        val safePrefix = filePrefix.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(28).ifBlank { "scan_nb" }
        val pdfFile = File(scanDir, "${safePrefix}_${ts}.pdf")

        val grayscalePages = mutableListOf<Bitmap>()
        val previewFiles = mutableListOf<File>()
        val pdf = PdfDocument()

        val margin = 24f
        val maxContentW = PAGE_WIDTH - margin * 2f
        val maxContentH = PAGE_HEIGHT - margin * 2f - 18f

        val footerPaint = Paint().apply {
            color = Color.rgb(110, 110, 110)
            textSize = 8.5f
            isAntiAlias = true
        }

        pageBitmaps.forEachIndexed { index, rawBmp ->
            val grayBmp = convertBitmapToGrayscale(rawBmp, enhanceContrast = enhanceContrast)
            grayscalePages.add(grayBmp)

            val previewFile = File(scanDir, "${safePrefix}_${ts}_p${index + 1}.jpg")
            FileOutputStream(previewFile).use { fos ->
                grayBmp.compress(Bitmap.CompressFormat.JPEG, 88, fos)
            }
            previewFiles.add(previewFile)

            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create()
            val page = pdf.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            val scale = minOf(maxContentW / grayBmp.width.toFloat(), maxContentH / grayBmp.height.toFloat())
            val drawW = (grayBmp.width * scale).coerceAtLeast(1f)
            val drawH = (grayBmp.height * scale).coerceAtLeast(1f)
            val left = (PAGE_WIDTH - drawW) / 2f
            val top = margin + (maxContentH - drawH) / 2f
            canvas.drawBitmap(grayBmp, null, RectF(left, top, left + drawW, top + drawH), null)
            canvas.drawText(
                "VieSimple Scan N&B · Page ${index + 1}/${pageBitmaps.size}",
                margin,
                PAGE_HEIGHT - 14f,
                footerPaint
            )
            pdf.finishPage(page)
        }

        FileOutputStream(pdfFile).use { pdf.writeTo(it) }
        pdf.close()

        return GeneratedGrayscalePdf(
            pdfFile = pdfFile,
            grayscaleBitmaps = grayscalePages,
            previewImageFiles = previewFiles,
            pageCount = grayscalePages.size,
            fileSizeBytes = pdfFile.length()
        )
    }

    fun generateDocumentPdf(context: Context, doc: VaultDocument, imageBitmap: Bitmap? = null): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeTitle = doc.title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(32).ifBlank { "document" }
        val outFile = File(exportDir, "${safeTitle}_${System.currentTimeMillis()}.pdf")

        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = pdf.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.rgb(27, 77, 62)
            textSize = 20f
            isFakeBoldText = true
        }
        val bodyPaint = TextPaint().apply {
            color = Color.rgb(30, 35, 32)
            textSize = 11.5f
        }
        val mutedPaint = Paint().apply {
            color = Color.rgb(92, 102, 96)
            textSize = 10f
        }

        var y = 52f
        canvas.drawText("VieSimple — Fiche Document", 44f, y, mutedPaint)
        y += 28f
        canvas.drawText(doc.title.ifBlank { "Document sans titre" }, 44f, y, titlePaint)
        y += 24f

        val lines = listOf(
            "Catégorie : ${doc.category}",
            "Type : ${doc.docType.ifBlank { "Non précisé" }}",
            "Organisme : ${doc.issuer.ifBlank { "Non précisé" }}",
            "Référence : ${doc.referenceNumber.ifBlank { "Non précisée" }}",
            "Date du document : ${DateUtils.formatFrench(doc.docDate)}",
            "Date d’expiration : ${DateUtils.formatFrench(doc.expirationDate)}",
            "Montant : ${if (doc.amount > 0) "%.2f €".format(doc.amount) else "Non applicable"}",
            "Confidentialité : ${if (doc.confidentiality == "sensitive") "Sensible" else "Standard"} (${if (doc.visibility == "private") "Privé" else "Partagé avec le foyer"})"
        )
        lines.forEach { line ->
            canvas.drawText(line, 44f, y, bodyPaint)
            y += 18f
        }

        if (doc.notes.isNotBlank()) {
            y += 10f
            canvas.drawText("Notes :", 44f, y, titlePaint.apply { textSize = 14f })
            y += 18f
            val layout = StaticLayout.Builder.obtain(doc.notes, 0, doc.notes.length, bodyPaint, 505)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build()
            canvas.save()
            canvas.translate(44f, y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height + 16f
        }

        if (doc.ocrText.isNotBlank()) {
            y += 8f
            canvas.drawText("Texte extrait (OCR) :", 44f, y, titlePaint.apply { textSize = 14f })
            y += 18f
            val preview = doc.ocrText.take(1500)
            val layout = StaticLayout.Builder.obtain(preview, 0, preview.length, bodyPaint, 505).build()
            canvas.save()
            canvas.translate(44f, y)
            layout.draw(canvas)
            canvas.restore()
        }
        pdf.finishPage(page)

        if (imageBitmap != null) {
            val imgPageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 2).create()
            val imgPage = pdf.startPage(imgPageInfo)
            val scale = minOf(515f / imageBitmap.width, 760f / imageBitmap.height)
            val scaledW = (imageBitmap.width * scale).toInt().coerceAtLeast(1)
            val scaledH = (imageBitmap.height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(imageBitmap, scaledW, scaledH, true)
            imgPage.canvas.drawBitmap(scaled, 40f, 40f, null)
            pdf.finishPage(imgPage)
        }

        FileOutputStream(outFile).use { pdf.writeTo(it) }
        pdf.close()
        return outFile
    }

    fun generateLetterPdf(
        context: Context,
        subject: String,
        body: String,
        senderName: String = "",
        senderAddress: String = "",
        senderEmail: String = "",
        senderPhone: String = "",
        senderCity: String = "",
        recipientOrg: String = "",
        recipientAddress: String = "",
        dateLabel: String = DateUtils.todayNumeric(),
        filePrefix: String = "courrier_viesimple"
    ): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safePrefix = filePrefix.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(32).ifBlank { "courrier" }
        val outFile = File(exportDir, "${safePrefix}_${System.currentTimeMillis()}.pdf")

        val pdf = PdfDocument()
        var pageNum = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
        var canvas = page.canvas

        val accentPaint = Paint().apply {
            color = Color.rgb(27, 77, 62)
            isAntiAlias = true
        }
        val senderBoldPaint = TextPaint().apply {
            color = Color.rgb(24, 32, 28)
            textSize = 11.5f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val metaPaint = TextPaint().apply {
            color = Color.rgb(66, 74, 70)
            textSize = 10.5f
            isAntiAlias = true
        }
        val subjectBoxPaint = Paint().apply {
            color = Color.rgb(239, 245, 242)
            isAntiAlias = true
        }
        val subjectPaint = TextPaint().apply {
            color = Color.rgb(21, 61, 49)
            textSize = 11.5f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val bodyPaint = TextPaint().apply {
            color = Color.rgb(28, 33, 30)
            textSize = 11f
            isAntiAlias = true
        }
        val footerPaint = Paint().apply {
            color = Color.rgb(130, 140, 134)
            textSize = 8.5f
            isAntiAlias = true
        }

        // Top accent stripe
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 8f, accentPaint)

        var y = 44f

        // Left block: Sender details
        var senderY = y
        if (senderName.isNotBlank()) {
            canvas.drawText(senderName.trim(), MARGIN_H, senderY, senderBoldPaint)
            senderY += 15f
        }
        val senderLines = buildList {
            if (senderAddress.isNotBlank()) {
                senderAddress.trim().lines().forEach { if (it.isNotBlank()) add(it.trim()) }
            }
            if (senderCity.isNotBlank()) add(senderCity.trim())
            if (senderPhone.isNotBlank()) add("Tél. : ${senderPhone.trim()}")
            if (senderEmail.isNotBlank()) add(senderEmail.trim())
        }
        senderLines.forEach { line ->
            val layout = StaticLayout.Builder.obtain(line, 0, line.length, metaPaint, 240).build()
            canvas.save()
            canvas.translate(MARGIN_H, senderY - 10f)
            layout.draw(canvas)
            canvas.restore()
            senderY += layout.height + 2f
        }

        // Right block: Recipient & Date
        val rightX = 320f
        var recipientY = y + 26f
        if (recipientOrg.isNotBlank()) {
            val destTitle = "À l’attention de ${recipientOrg.trim()}"
            val destLayout = StaticLayout.Builder.obtain(destTitle, 0, destTitle.length, senderBoldPaint, 227).build()
            canvas.save()
            canvas.translate(rightX, recipientY - 10f)
            destLayout.draw(canvas)
            canvas.restore()
            recipientY += destLayout.height + 4f
        }
        if (recipientAddress.isNotBlank()) {
            val addrLayout = StaticLayout.Builder.obtain(recipientAddress.trim(), 0, recipientAddress.trim().length, metaPaint, 227).build()
            canvas.save()
            canvas.translate(rightX, recipientY - 10f)
            addrLayout.draw(canvas)
            canvas.restore()
            recipientY += addrLayout.height + 6f
        }

        val placeDate = if (senderCity.isNotBlank()) {
            "Fait à ${senderCity.trim()}, le $dateLabel"
        } else {
            "Le $dateLabel"
        }
        recipientY += 10f
        canvas.drawText(placeDate, rightX, recipientY, metaPaint)
        recipientY += 14f

        y = maxOf(senderY, recipientY) + 18f

        // Subject banner
        if (subject.isNotBlank()) {
            val subjectText = "Objet : ${subject.trim()}"
            val subjLayout = StaticLayout.Builder.obtain(subjectText, 0, subjectText.length, subjectPaint, CONTENT_WIDTH - 24)
                .setLineSpacing(2f, 1.1f)
                .build()
            val boxHeight = subjLayout.height + 16f
            canvas.drawRoundRect(
                RectF(MARGIN_H, y, MARGIN_H + CONTENT_WIDTH, y + boxHeight),
                6f,
                6f,
                subjectBoxPaint
            )
            canvas.drawRect(MARGIN_H, y, MARGIN_H + 3.5f, y + boxHeight, accentPaint)
            canvas.save()
            canvas.translate(MARGIN_H + 14f, y + 8f)
            subjLayout.draw(canvas)
            canvas.restore()
            y += boxHeight + 22f
        }

        // Paginated body text
        val cleanBody = body.trim()
        if (cleanBody.isNotEmpty()) {
            val bodyLayout = StaticLayout.Builder.obtain(cleanBody, 0, cleanBody.length, bodyPaint, CONTENT_WIDTH)
                .setLineSpacing(4f, 1.18f)
                .build()

            val maxContentY = PAGE_HEIGHT - 56f
            for (lineIdx in 0 until bodyLayout.lineCount) {
                val lineTop = bodyLayout.getLineTop(lineIdx)
                val lineBottom = bodyLayout.getLineBottom(lineIdx)
                val lineH = (lineBottom - lineTop).toFloat()

                if (y + lineH > maxContentY) {
                    drawLetterFooter(canvas, pageNum, footerPaint)
                    pdf.finishPage(page)
                    pageNum += 1
                    page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
                    canvas = page.canvas
                    canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 5f, accentPaint)
                    y = 52f
                }

                val startChar = bodyLayout.getLineStart(lineIdx)
                val endChar = bodyLayout.getLineEnd(lineIdx)
                val lineText = cleanBody.substring(startChar, endChar).trimEnd('\n', '\r')
                val baseline = y + bodyLayout.getLineBaseline(lineIdx) - lineTop
                canvas.drawText(lineText, MARGIN_H, baseline, bodyPaint)
                y += lineH
            }
        }

        drawLetterFooter(canvas, pageNum, footerPaint)
        pdf.finishPage(page)

        FileOutputStream(outFile).use { pdf.writeTo(it) }
        pdf.close()
        return outFile
    }

    private fun drawLetterFooter(canvas: Canvas, pageNum: Int, footerPaint: Paint) {
        canvas.drawText("Document généré via VieSimple · Page $pageNum", MARGIN_H, PAGE_HEIGHT - 26f, footerPaint)
    }

    fun renderPdfPagesToBitmaps(file: File, maxPages: Int = 4): List<Bitmap> {
        if (!file.exists()) return emptyList()
        val bitmaps = mutableListOf<Bitmap>()
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                val count = minOf(renderer.pageCount, maxPages)
                for (i in 0 until count) {
                    renderer.openPage(i).use { page ->
                        val scale = 2
                        val bmp = Bitmap.createBitmap(
                            (page.width * scale).coerceAtLeast(1),
                            (page.height * scale).coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888
                        )
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmaps.add(bmp)
                    }
                }
            }
        }
        return bitmaps
    }

    fun isPdfBytes(bytes: ByteArray): Boolean {
        return bytes.size >= 4 &&
            bytes[0] == 0x25.toByte() && // %
            bytes[1] == 0x50.toByte() && // P
            bytes[2] == 0x44.toByte() && // D
            bytes[3] == 0x46.toByte()    // F
    }

    fun decryptVaultFileToShareablePdf(context: Context, doc: VaultDocument): File? {
        val uriPath = doc.localFileUri
        if (uriPath.isBlank()) return null
        val file = File(uriPath)
        if (!file.exists()) return null

        val plainBytes = if (uriPath.endsWith(".enc")) {
            CryptoVaultHelper.decryptVaultFile(uriPath) ?: return null
        } else {
            file.readBytes()
        }

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeTitle = doc.title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(32).ifBlank { "document" }

        if (isPdfBytes(plainBytes)) {
            val outFile = File(exportDir, "${safeTitle}_${System.currentTimeMillis()}.pdf")
            FileOutputStream(outFile).use { it.write(plainBytes) }
            return outFile
        }

        val imageBmp = BitmapFactory.decodeByteArray(plainBytes, 0, plainBytes.size)
        return if (imageBmp != null) {
            generateDocumentPdf(context, doc, imageBmp)
        } else {
            null
        }
    }

    fun decryptVaultPdfPreviewBitmaps(context: Context, doc: VaultDocument, maxPages: Int = 3): List<Bitmap> {
        val uriPath = doc.localFileUri
        if (uriPath.isBlank()) return emptyList()
        val file = File(uriPath)
        if (!file.exists()) return emptyList()

        val plainBytes = if (uriPath.endsWith(".enc")) {
            CryptoVaultHelper.decryptVaultFile(uriPath) ?: return emptyList()
        } else {
            file.readBytes()
        }

        if (isPdfBytes(plainBytes)) {
            val tempDir = File(context.cacheDir, "preview").apply { mkdirs() }
            val tempPdf = File(tempDir, "preview_${doc.id.ifBlank { "tmp" }}.pdf")
            FileOutputStream(tempPdf).use { it.write(plainBytes) }
            return try {
                renderPdfPagesToBitmaps(tempPdf, maxPages)
            } finally {
                tempPdf.delete()
            }
        }

        val imageBmp = BitmapFactory.decodeByteArray(plainBytes, 0, plainBytes.size)
        return if (imageBmp != null) listOf(imageBmp) else emptyList()
    }

    fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, chooserTitle))
    }
}

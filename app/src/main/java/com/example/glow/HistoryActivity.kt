package com.example.glow

import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.glow.data.CheckInDatabase
import com.example.glow.data.CheckInEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    private lateinit var rvHistory: RecyclerView
    private lateinit var btnExportPDF: android.widget.Button
    private val db by lazy { CheckInDatabase.getDatabase(this) }
    private var checkIns = listOf<CheckInEntity>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        rvHistory = findViewById(R.id.rvHistory)
        btnExportPDF = findViewById(R.id.btnExportPDF)
        rvHistory.layoutManager = LinearLayoutManager(this)

        btnExportPDF.setOnClickListener {
            if (checkIns.isEmpty()) {
                Toast.makeText(this, "No data to export!", Toast.LENGTH_SHORT).show()
            } else {
                createPDF()
            }
        }

        loadHistory()
    }

    private fun loadHistory() {
        CoroutineScope(Dispatchers.IO).launch {
            checkIns = db.checkInDao().getAllCheckIns()

            withContext(Dispatchers.Main) {
                if (checkIns.isEmpty()) {
                    Toast.makeText(this@HistoryActivity, "No history yet. Do a check-in first!", Toast.LENGTH_LONG).show()
                } else {
                    rvHistory.adapter = HistoryAdapter(checkIns) { clickedCheckIn ->
                        showNoteDialog(clickedCheckIn)
                    }
                }
            }
        }
    }

    private fun showNoteDialog(checkIn: CheckInEntity) {
        val editText = EditText(this).apply {
            hint = "E.g. Before exam, Long drive..."
            setText(checkIn.note)
            setSelection(text.length)
        }

        AlertDialog.Builder(this)
            .setTitle("Add Private Note")
            .setView(editText)
            .setPositiveButton("Save") { dialog, _ ->
                val newNote = editText.text.toString()
                CoroutineScope(Dispatchers.IO).launch {
                    db.checkInDao().updateNote(checkIn.id, newNote)
                    withContext(Dispatchers.Main) {
                        loadHistory()
                        Toast.makeText(this@HistoryActivity, "Note Saved ✓", Toast.LENGTH_SHORT).show()
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun createPDF() {
        val pdfDocument = PdfDocument()
        val paint = Paint()
        val sdf = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())

        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        // Title
        paint.textSize = 24f
        paint.color = "#005662".toColorInt()
        paint.isFakeBoldText = true
        canvas.drawText("Glow - Mental Fatigue History", 40f, 50f, paint)

        // Subtitle
        paint.textSize = 14f
        paint.color = Color.GRAY
        paint.isFakeBoldText = false
        canvas.drawText("Confidential Medical Export", 40f, 70f, paint)

        // Line separator
        paint.color = "#00e5ff".toColorInt()
        paint.strokeWidth = 2f
        canvas.drawLine(40f, 80f, 555f, 80f, paint)

        var yPosition = 110f

        for (checkIn in checkIns) {
            if (yPosition > 780) {
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                yPosition = 50f
            }

            // Symbol & State
            paint.textSize = 16f
            paint.color = Color.BLACK
            paint.isFakeBoldText = true
            val symbol = when (checkIn.state) { "RESTED" -> "[OK]"; "FATIGUED" -> "[!]"; "STRESSED" -> "[X]"; else -> "[-]" }
            canvas.drawText("$symbol ${checkIn.state}", 40f, yPosition, paint)

            // Date
            paint.textSize = 12f
            paint.color = Color.DKGRAY
            paint.isFakeBoldText = false
            canvas.drawText(sdf.format(Date(checkIn.timestamp)), 40f, yPosition + 18f, paint)

            // Response Time
            paint.color = "#00838f".toColorInt()
            paint.isFakeBoldText = true
            canvas.drawText("Response: ${String.format(Locale.getDefault(), "%.2f", checkIn.responseTime)}s", 350f, yPosition + 18f, paint)

            // Note
            if (checkIn.note.isNotEmpty()) {
                paint.color = Color.GRAY
                paint.isFakeBoldText = false
                paint.textSize = 12f
                canvas.drawText("Note: ${checkIn.note}", 40f, yPosition + 36f, paint)
                yPosition += 60f
            } else {
                yPosition += 40f
            }
        }

        pdfDocument.finishPage(page)

        // Save to the app's hidden cache directory (No permissions needed!)
        val file = File(cacheDir, "Glow_History_${System.currentTimeMillis()}.pdf")

        try {
            pdfDocument.writeTo(FileOutputStream(file))
            sharePDF(file)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error generating PDF", Toast.LENGTH_SHORT).show()
        }

        pdfDocument.close()
    }

    private fun sharePDF(file: File) {
        // Get a secure URI for the file
        val uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.fileprovider", file)

        // Create the Share Intent
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // Show the Share Sheet
        startActivity(Intent.createChooser(shareIntent, "Share Glow History PDF"))
    }
}
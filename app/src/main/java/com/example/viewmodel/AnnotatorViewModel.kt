package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.AnnotationBox
import com.example.model.ExportFormat
import com.example.model.LabelClass
import com.example.model.LabelPresets
import com.example.pdf.PdfManager
import com.example.util.DatasetExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class AnnotatorUiState(
    val pdfFileName: String = "sample_invoice",
    val totalPages: Int = 0,
    val currentPage: Int = 1,
    val currentPageBitmap: Bitmap? = null,
    val isLoadingPage: Boolean = false,
    val classes: List<LabelClass> = LabelPresets.GENERAL,
    val activeClassId: Int = 0,
    val pageAnnotations: Map<Int, List<AnnotationBox>> = emptyMap(),
    val pageRotations: Map<Int, Float> = emptyMap(),
    val selectedBoxIds: Set<String> = emptySet(),
    val primarySelectedBoxId: String? = null,
    val isCrosshairEnabled: Boolean = false,
    val isSnappingEnabled: Boolean = true,
    val isAlignmentGridEnabled: Boolean = false,
    val showRotatePanel: Boolean = false,
    val renderScale: Float = 1.0f,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val hasOverlap: Boolean = false,
    val isExporting: Boolean = false,
    val exportProgress: Float = 0f,
    val exportStatus: String = "",
    val exportedZipFile: File? = null,
    val toastMessage: String? = null,
    val showAddClassDialog: Boolean = false,
    val showPresetDialog: Boolean = false,
    val showExportDialog: Boolean = false,
    val showProjectSessionDialog: Boolean = false
) {
    val currentBoxes: List<AnnotationBox>
        get() = pageAnnotations[currentPage] ?: emptyList()

    val currentRotation: Float
        get() = pageRotations[currentPage] ?: 0f

    val totalBoxesAllPages: Int
        get() = pageAnnotations.values.sumOf { it.size }

    val primarySelectedBox: AnnotationBox?
        get() = currentBoxes.find { it.id == primarySelectedBoxId }
}

class AnnotatorViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    val pdfManager = PdfManager(context)
    private val datasetExporter = DatasetExporter(context)

    private val _uiState = MutableStateFlow(AnnotatorUiState())
    val uiState: StateFlow<AnnotatorUiState> = _uiState.asStateFlow()

    private val undoStacks = mutableMapOf<Int, MutableList<List<AnnotationBox>>>()
    private val redoStacks = mutableMapOf<Int, MutableList<List<AnnotationBox>>>()
    private var copiedBoxes = listOf<AnnotationBox>()

    private val sessionFile: File
        get() = File(context.filesDir, "annotator_session.json")

    init {
        viewModelScope.launch {
            val restored = tryRestoreSession()
            if (!restored) {
                loadSampleDocument()
            }
        }
    }

    fun loadSampleDocument() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingPage = true) }
            try {
                val file = pdfManager.loadSamplePdf()
                val total = pdfManager.pageCount
                _uiState.update {
                    it.copy(
                        pdfFileName = file.nameWithoutExtension,
                        totalPages = total,
                        currentPage = 1,
                        classes = LabelPresets.GENERAL,
                        activeClassId = 0,
                        pageAnnotations = emptyMap(),
                        pageRotations = emptyMap(),
                        selectedBoxIds = emptySet(),
                        primarySelectedBoxId = null
                    )
                }
                renderCurrentPage()
                showToast("Dokumen Contoh Berhasil Dimuat (${total} Halaman).")
                saveSession()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingPage = false, toastMessage = "Gagal memuat PDF contoh: ${e.message}") }
            }
        }
    }

    fun openPdfFromUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingPage = true) }
            try {
                val file = pdfManager.openPdfFromUri(uri)
                val total = pdfManager.pageCount
                undoStacks.clear()
                redoStacks.clear()
                _uiState.update {
                    it.copy(
                        pdfFileName = file.nameWithoutExtension,
                        totalPages = total,
                        currentPage = 1,
                        pageAnnotations = emptyMap(),
                        pageRotations = emptyMap(),
                        selectedBoxIds = emptySet(),
                        primarySelectedBoxId = null,
                        canUndo = false,
                        canRedo = false
                    )
                }
                renderCurrentPage()
                showToast("PDF berhasil dibuka (${total} Halaman).")
                saveSession()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingPage = false, toastMessage = "Gagal membuka PDF: ${e.message}") }
            }
        }
    }

    private suspend fun renderCurrentPage() {
        val pageNum = _uiState.value.currentPage
        _uiState.update { it.copy(isLoadingPage = true) }
        val bitmap = pdfManager.renderPage(pageNum - 1, targetWidthPx = 1200)
        _uiState.update {
            it.copy(
                currentPageBitmap = bitmap,
                isLoadingPage = false
            )
        }
        checkOverlap()
    }

    fun goToPage(page: Int) {
        val total = _uiState.value.totalPages
        if (page in 1..total && page != _uiState.value.currentPage) {
            _uiState.update {
                it.copy(
                    currentPage = page,
                    selectedBoxIds = emptySet(),
                    primarySelectedBoxId = null,
                    canUndo = undoStacks[page]?.isNotEmpty() == true,
                    canRedo = redoStacks[page]?.isNotEmpty() == true
                )
            }
            viewModelScope.launch {
                renderCurrentPage()
            }
            saveSession()
        }
    }

    fun nextPage() = goToPage(_uiState.value.currentPage + 1)
    fun prevPage() = goToPage(_uiState.value.currentPage - 1)
    fun firstPage() = goToPage(1)
    fun lastPage() = goToPage(_uiState.value.totalPages)

    fun autoAdvance() {
        val curr = _uiState.value.currentPage
        val total = _uiState.value.totalPages
        if (curr < total) {
            showToast("Halaman $curr selesai. Berpindah ke hal. ${curr + 1}")
            goToPage(curr + 1)
        } else {
            showToast("Sudah di halaman terakhir dokumen.")
        }
    }

    fun rotatePageFine(delta: Float) {
        val pageNum = _uiState.value.currentPage
        val currentRot = _uiState.value.pageRotations[pageNum] ?: 0f
        var newRot = kotlin.math.round((currentRot + delta) * 10f) / 10f
        if (kotlin.math.abs(newRot) < 0.05f) newRot = 0f
        setPageRotation(newRot)
    }

    fun setPageRotation(angle: Float) {
        val pageNum = _uiState.value.currentPage
        val clamped = (kotlin.math.round(angle * 10f) / 10f).coerceIn(-180f, 180f)
        val finalRot = if (kotlin.math.abs(clamped) < 0.05f) 0f else clamped
        _uiState.update { state ->
            val newRotMap = state.pageRotations.toMutableMap()
            if (finalRot == 0f) {
                newRotMap.remove(pageNum)
            } else {
                newRotMap[pageNum] = finalRot
            }
            state.copy(pageRotations = newRotMap)
        }
        val displayStr = if (finalRot > 0f) "+${String.format(java.util.Locale.US, "%.1f", finalRot)}" else String.format(java.util.Locale.US, "%.1f", finalRot)
        showToast("Rotasi halus hal. $pageNum: ${displayStr}°")
        saveSession()
    }

    fun resetPageRotation() {
        val pageNum = _uiState.value.currentPage
        _uiState.update { state ->
            val newRotMap = state.pageRotations.toMutableMap()
            newRotMap.remove(pageNum)
            state.copy(pageRotations = newRotMap)
        }
        showToast("Rotasi hal. $pageNum direset (0.0°).")
        saveSession()
    }

    fun toggleRotatePanel() {
        _uiState.update { it.copy(showRotatePanel = !it.showRotatePanel) }
    }

    fun setShowRotatePanel(show: Boolean) {
        _uiState.update { it.copy(showRotatePanel = show) }
    }

    fun toggleAlignmentGrid() {
        _uiState.update { it.copy(isAlignmentGridEnabled = !it.isAlignmentGridEnabled) }
    }

    fun rotatePage(degree: Int) {
        rotatePageFine(degree.toFloat())
    }

    fun saveUndoState() {
        val page = _uiState.value.currentPage
        val currentBoxes = (_uiState.value.pageAnnotations[page] ?: emptyList()).map { it.copy() }
        val stack = undoStacks.getOrPut(page) { mutableListOf() }
        stack.add(currentBoxes)
        if (stack.size > 30) stack.removeAt(0)
        redoStacks[page]?.clear()

        _uiState.update {
            it.copy(canUndo = true, canRedo = false)
        }
    }

    fun undo() {
        val page = _uiState.value.currentPage
        val uStack = undoStacks[page] ?: return
        if (uStack.isEmpty()) return

        val currentBoxes = (_uiState.value.pageAnnotations[page] ?: emptyList()).map { it.copy() }
        val rStack = redoStacks.getOrPut(page) { mutableListOf() }
        rStack.add(currentBoxes)

        val previous = uStack.removeAt(uStack.size - 1)
        _uiState.update { state ->
            val newMap = state.pageAnnotations.toMutableMap()
            newMap[page] = previous
            state.copy(
                pageAnnotations = newMap,
                selectedBoxIds = emptySet(),
                primarySelectedBoxId = null,
                canUndo = uStack.isNotEmpty(),
                canRedo = true
            )
        }
        checkOverlap()
        showToast("Undo berhasil.")
        saveSession()
    }

    fun redo() {
        val page = _uiState.value.currentPage
        val rStack = redoStacks[page] ?: return
        if (rStack.isEmpty()) return

        val currentBoxes = (_uiState.value.pageAnnotations[page] ?: emptyList()).map { it.copy() }
        val uStack = undoStacks.getOrPut(page) { mutableListOf() }
        uStack.add(currentBoxes)

        val next = rStack.removeAt(rStack.size - 1)
        _uiState.update { state ->
            val newMap = state.pageAnnotations.toMutableMap()
            newMap[page] = next
            state.copy(
                pageAnnotations = newMap,
                selectedBoxIds = emptySet(),
                primarySelectedBoxId = null,
                canUndo = true,
                canRedo = rStack.isNotEmpty()
            )
        }
        checkOverlap()
        showToast("Redo berhasil.")
        saveSession()
    }

    fun addBox(box: AnnotationBox) {
        saveUndoState()
        val page = _uiState.value.currentPage
        val existing = (_uiState.value.pageAnnotations[page] ?: emptyList()).toMutableList()
        existing.add(box)
        _uiState.update { state ->
            val newMap = state.pageAnnotations.toMutableMap()
            newMap[page] = existing
            state.copy(
                pageAnnotations = newMap,
                selectedBoxIds = setOf(box.id),
                primarySelectedBoxId = box.id
            )
        }
        checkOverlap()
        saveSession()
    }

    fun updateBox(updatedBox: AnnotationBox) {
        val page = _uiState.value.currentPage
        val existing = (_uiState.value.pageAnnotations[page] ?: emptyList()).toMutableList()
        val index = existing.indexOfFirst { it.id == updatedBox.id }
        if (index != -1) {
            existing[index] = updatedBox
            _uiState.update { state ->
                val newMap = state.pageAnnotations.toMutableMap()
                newMap[page] = existing
                state.copy(pageAnnotations = newMap)
            }
            checkOverlap()
        }
    }

    fun deleteSelectedBox() {
        val selected = _uiState.value.selectedBoxIds
        if (selected.isEmpty()) return
        saveUndoState()
        val page = _uiState.value.currentPage
        val existing = (_uiState.value.pageAnnotations[page] ?: emptyList()).filter { it.id !in selected }

        _uiState.update { state ->
            val newMap = state.pageAnnotations.toMutableMap()
            newMap[page] = existing
            state.copy(
                pageAnnotations = newMap,
                selectedBoxIds = emptySet(),
                primarySelectedBoxId = null
            )
        }
        checkOverlap()
        showToast("Bounding box dihapus.")
        saveSession()
    }

    fun copySelectedBoxes() {
        val selected = _uiState.value.selectedBoxIds
        val current = _uiState.value.currentBoxes
        copiedBoxes = current.filter { it.id in selected }.map { it.copy() }
        if (copiedBoxes.isNotEmpty()) {
            showToast("${copiedBoxes.size} box disalin ke clipboard.")
        }
    }

    fun pasteCopiedBoxes() {
        if (copiedBoxes.isEmpty()) return
        saveUndoState()
        val page = _uiState.value.currentPage
        val existing = (_uiState.value.pageAnnotations[page] ?: emptyList()).toMutableList()
        val newPasted = copiedBoxes.map {
            it.copy(
                id = java.util.UUID.randomUUID().toString(),
                x = (it.x + 0.02f).coerceAtMost(0.9f),
                y = (it.y + 0.02f).coerceAtMost(0.9f)
            )
        }
        existing.addAll(newPasted)
        _uiState.update { state ->
            val newMap = state.pageAnnotations.toMutableMap()
            newMap[page] = existing
            state.copy(
                pageAnnotations = newMap,
                selectedBoxIds = newPasted.map { it.id }.toSet(),
                primarySelectedBoxId = newPasted.firstOrNull()?.id
            )
        }
        checkOverlap()
        showToast("${newPasted.size} box berhasil ditempel.")
        saveSession()
    }

    fun duplicateSelectedBoxes() {
        copySelectedBoxes()
        pasteCopiedBoxes()
    }

    fun selectBox(boxId: String, isMultiSelect: Boolean = false) {
        _uiState.update { state ->
            val currentSelected = state.selectedBoxIds
            if (isMultiSelect) {
                val newSelected = if (boxId in currentSelected) {
                    currentSelected - boxId
                } else {
                    currentSelected + boxId
                }
                state.copy(
                    selectedBoxIds = newSelected,
                    primarySelectedBoxId = if (boxId in newSelected) boxId else newSelected.firstOrNull()
                )
            } else {
                state.copy(
                    selectedBoxIds = setOf(boxId),
                    primarySelectedBoxId = boxId
                )
            }
        }
    }

    fun clearSelection() {
        _uiState.update {
            it.copy(selectedBoxIds = emptySet(), primarySelectedBoxId = null)
        }
    }

    fun addClass(name: String, colorValue: Long) {
        val currentClasses = _uiState.value.classes
        val nextId = if (currentClasses.isNotEmpty()) currentClasses.maxOf { it.id } + 1 else 0
        val newClass = LabelClass(nextId, name.trim(), colorValue, true)
        val updated = currentClasses + newClass
        _uiState.update {
            it.copy(classes = updated, activeClassId = nextId, showAddClassDialog = false)
        }
        showToast("Kelas '${newClass.name}' ditambahkan.")
        saveSession()
    }

    fun applyPreset(classes: List<LabelClass>) {
        _uiState.update {
            it.copy(
                classes = classes,
                activeClassId = classes.firstOrNull()?.id ?: 0,
                showPresetDialog = false
            )
        }
        showToast("Preset label diterapkan.")
        saveSession()
    }

    fun setActiveClass(classId: Int) {
        _uiState.update { it.copy(activeClassId = classId) }
        val selected = _uiState.value.selectedBoxIds
        if (selected.isNotEmpty()) {
            saveUndoState()
            val page = _uiState.value.currentPage
            val boxes = (_uiState.value.pageAnnotations[page] ?: emptyList()).map { b ->
                if (b.id in selected) b.copy(classId = classId) else b
            }
            _uiState.update { state ->
                val newMap = state.pageAnnotations.toMutableMap()
                newMap[page] = boxes
                state.copy(pageAnnotations = newMap)
            }
            saveSession()
        }
    }

    fun toggleClassVisibility(classId: Int) {
        _uiState.update { state ->
            val updated = state.classes.map {
                if (it.id == classId) it.copy(visible = !it.visible) else it
            }
            state.copy(classes = updated)
        }
        saveSession()
    }

    fun toggleCrosshair() {
        _uiState.update {
            val next = !it.isCrosshairEnabled
            it.copy(isCrosshairEnabled = next)
        }
    }

    fun toggleSnapping() {
        _uiState.update {
            val next = !it.isSnappingEnabled
            it.copy(isSnappingEnabled = next)
        }
    }

    fun setRenderScale(scale: Float) {
        _uiState.update { it.copy(renderScale = scale.coerceIn(0.5f, 3.5f)) }
    }

    private fun checkOverlap() {
        val boxes = _uiState.value.currentBoxes
        var overlap = false
        for (i in 0 until boxes.size) {
            for (j in i + 1 until boxes.size) {
                if (boxes[i].calculateIoU(boxes[j]) > 0.35f) {
                    overlap = true
                    break
                }
            }
            if (overlap) break
        }
        _uiState.update { it.copy(hasOverlap = overlap) }
    }

    fun showAddClassDialog(show: Boolean) = _uiState.update { it.copy(showAddClassDialog = show) }
    fun showPresetDialog(show: Boolean) = _uiState.update { it.copy(showPresetDialog = show) }
    fun showExportDialog(show: Boolean) = _uiState.update { it.copy(showExportDialog = show) }
    fun showProjectSessionDialog(show: Boolean) = _uiState.update { it.copy(showProjectSessionDialog = show) }

    fun startExport(
        format: ExportFormat,
        startPage: Int,
        endPage: Int,
        includeImages: Boolean,
        onlyAnnotated: Boolean,
        splitDataset: Boolean,
        trainRatio: Float
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isExporting = true,
                    exportProgress = 0f,
                    exportStatus = "Menyiapkan ekspor...",
                    exportedZipFile = null
                )
            }
            try {
                val file = datasetExporter.exportDataset(
                    pdfManager = pdfManager,
                    baseFileName = _uiState.value.pdfFileName,
                    format = format,
                    startPage = startPage,
                    endPage = endPage,
                    includeImages = includeImages,
                    onlyAnnotated = onlyAnnotated,
                    splitDataset = splitDataset,
                    trainRatio = trainRatio,
                    classes = _uiState.value.classes,
                    pageAnnotations = _uiState.value.pageAnnotations,
                    pageRotations = _uiState.value.pageRotations,
                    onProgress = { p, status ->
                        _uiState.update {
                            it.copy(exportProgress = p, exportStatus = status)
                        }
                    }
                )
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportedZipFile = file,
                        exportStatus = "Selesai! ZIP tersimpan di: ${file.name}"
                    )
                }
                showToast("Dataset ${format.displayName} berhasil diekspor!")
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportStatus = "Gagal: ${e.message}"
                    )
                }
                showToast("Gagal ekspor: ${e.message}")
            }
        }
    }

    fun clearToast() = _uiState.update { it.copy(toastMessage = null) }
    fun showToast(msg: String) = _uiState.update { it.copy(toastMessage = msg) }

    fun resetProject() {
        undoStacks.clear()
        redoStacks.clear()
        sessionFile.delete()
        loadSampleDocument()
    }

    private fun saveSession() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val state = _uiState.value
                val root = JSONObject()
                root.put("fileName", state.pdfFileName)
                root.put("totalPages", state.totalPages)
                root.put("currentPage", state.currentPage)

                val classesArr = JSONArray()
                state.classes.forEach { c ->
                    classesArr.put(JSONObject().apply {
                        put("id", c.id)
                        put("name", c.name)
                        put("color", c.colorValue)
                        put("visible", c.visible)
                    })
                }
                root.put("classes", classesArr)

                val annoObj = JSONObject()
                state.pageAnnotations.forEach { (pageNum, boxes) ->
                    val boxesArr = JSONArray()
                    boxes.forEach { b ->
                        boxesArr.put(JSONObject().apply {
                            put("id", b.id)
                            put("classId", b.classId)
                            put("x", b.x.toDouble())
                            put("y", b.y.toDouble())
                            put("width", b.width.toDouble())
                            put("height", b.height.toDouble())
                        })
                    }
                    annoObj.put(pageNum.toString(), boxesArr)
                }
                root.put("pageAnnotations", annoObj)

                val rotObj = JSONObject()
                state.pageRotations.forEach { (p, rot) -> rotObj.put(p.toString(), rot.toDouble()) }
                root.put("pageRotations", rotObj)

                sessionFile.writeText(root.toString(2))
            } catch (_: Exception) {}
        }
    }

    private suspend fun tryRestoreSession(): Boolean = withContext(Dispatchers.IO) {
        if (!sessionFile.exists()) return@withContext false
        try {
            val text = sessionFile.readText()
            val root = JSONObject(text)
            val fileName = root.optString("fileName", "sample_invoice")
            val total = root.optInt("totalPages", 1)
            val currentP = root.optInt("currentPage", 1)

            val classesList = mutableListOf<LabelClass>()
            val classesArr = root.optJSONArray("classes")
            if (classesArr != null) {
                for (i in 0 until classesArr.length()) {
                    val o = classesArr.getJSONObject(i)
                    classesList.add(
                        LabelClass(
                            id = o.getInt("id"),
                            name = o.getString("name"),
                            colorValue = o.getLong("color"),
                            visible = o.optBoolean("visible", true)
                        )
                    )
                }
            }

            val pageAnno = mutableMapOf<Int, List<AnnotationBox>>()
            val annoObj = root.optJSONObject("pageAnnotations")
            if (annoObj != null) {
                val keys = annoObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val pNum = k.toIntOrNull() ?: continue
                    val bArr = annoObj.getJSONArray(k)
                    val list = mutableListOf<AnnotationBox>()
                    for (i in 0 until bArr.length()) {
                        val b = bArr.getJSONObject(i)
                        list.add(
                            AnnotationBox(
                                id = b.getString("id"),
                                classId = b.getInt("classId"),
                                x = b.getDouble("x").toFloat(),
                                y = b.getDouble("y").toFloat(),
                                width = b.getDouble("width").toFloat(),
                                height = b.getDouble("height").toFloat()
                            )
                        )
                    }
                    pageAnno[pNum] = list
                }
            }

            val pageRot = mutableMapOf<Int, Float>()
            val rotObj = root.optJSONObject("pageRotations")
            if (rotObj != null) {
                val keys = rotObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val pNum = k.toIntOrNull() ?: continue
                    pageRot[pNum] = rotObj.optDouble(k, 0.0).toFloat()
                }
            }

            // Also reload the sample document
            val sampleFile = File(context.cacheDir, "sample_invoice_document.pdf")
            if (!sampleFile.exists()) {
                pdfManager.loadSamplePdf()
            } else {
                pdfManager.openPdfFile(sampleFile)
            }

            _uiState.update {
                it.copy(
                    pdfFileName = fileName,
                    totalPages = pdfManager.pageCount.coerceAtLeast(total),
                    currentPage = currentP.coerceIn(1, pdfManager.pageCount),
                    classes = if (classesList.isNotEmpty()) classesList else LabelPresets.GENERAL,
                    activeClassId = classesList.firstOrNull()?.id ?: 0,
                    pageAnnotations = pageAnno,
                    pageRotations = pageRot
                )
            }
            renderCurrentPage()
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun onCleared() {
        super.onCleared()
        pdfManager.close()
    }
}

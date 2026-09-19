package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.AppMode
import com.example.ui.components.AddClassDialog
import com.example.ui.components.AnnotationCanvasView
import com.example.ui.components.AnnotatorToolbar
import com.example.ui.components.BottomActionControls
import com.example.ui.components.ExportDatasetDialog
import com.example.ui.components.FineRotatePanel
import com.example.ui.components.GeminiSettingsDialog
import com.example.ui.components.LeftClassDrawer
import com.example.ui.components.PresetClassesDialog
import com.example.ui.components.RightBoxDrawer
import com.example.ui.components.TranscriptionExportDialog
import com.example.ui.components.TranscriptionModeScreen
import com.example.ui.theme.Indigo400
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import com.example.viewmodel.AnnotatorViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AnnotatorApp()
            }
        }
    }
}

@Composable
fun AnnotatorApp(viewModel: AnnotatorViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showLeftDrawer by remember { mutableStateOf(false) }
    var showRightDrawer by remember { mutableStateOf(false) }
    var zoomScale by remember { mutableFloatStateOf(1.0f) }

    val openPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.openPdfFromUri(uri)
        }
    }

    // Auto-dismiss toast
    LaunchedEffect(uiState.toastMessage) {
        if (uiState.toastMessage != null) {
            delay(2800)
            viewModel.clearToast()
        }
    }

    // Flush any pending autosave the moment the app leaves the foreground.
    // Android is free to kill the process any time after ON_STOP, so this is the
    // last reliable point to guarantee unsaved edits actually reach disk instead
    // of being lost with the process.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.flushSessionNow()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (uiState.appMode == AppMode.LABELING) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate950),
        topBar = {
            AnnotatorToolbar(
                currentPage = uiState.currentPage,
                totalPages = uiState.totalPages,
                canUndo = uiState.canUndo,
                canRedo = uiState.canRedo,
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                onFirstPage = { viewModel.firstPage() },
                onPrevPage = { viewModel.prevPage() },
                onNextPage = { viewModel.nextPage() },
                onLastPage = { viewModel.lastPage() },
                onAutoAdvance = { viewModel.autoAdvance() },
                onRotateLeft = { viewModel.rotatePageFine(-0.5f) },
                onRotateRight = { viewModel.rotatePageFine(0.5f) },
                currentRotation = uiState.currentRotation,
                onToggleRotatePanel = { viewModel.toggleRotatePanel() },
                onOpenPdf = { openPdfLauncher.launch(arrayOf("application/pdf")) },
                onExportDataset = { viewModel.showExportDialog(true) },
                onSaveProject = { viewModel.showToast("Proyek tersimpan otomatis di perangkat.") },
                onToggleLeftDrawer = {
                    showLeftDrawer = !showLeftDrawer
                    if (showLeftDrawer) showRightDrawer = false
                },
                onToggleRightDrawer = {
                    showRightDrawer = !showRightDrawer
                    if (showRightDrawer) showLeftDrawer = false
                },
                hasTranscriptionLines = uiState.transcriptionLines.isNotEmpty(),
                onSwitchToTranscription = { viewModel.switchToTranscriptionMode() },
                modifier = Modifier.statusBarsPadding()
            )
        },
        bottomBar = {
            BottomActionControls(
                currentPage = uiState.currentPage,
                totalPages = uiState.totalPages,
                canUndo = uiState.canUndo,
                canRedo = uiState.canRedo,
                hasSelectedBox = uiState.selectedBoxIds.isNotEmpty(),
                isCrosshairEnabled = uiState.isCrosshairEnabled,
                isSnappingEnabled = uiState.isSnappingEnabled,
                zoomScale = zoomScale,
                onPrevPage = { viewModel.prevPage() },
                onNextPage = { viewModel.nextPage() },
                onFirstPage = { viewModel.firstPage() },
                onLastPage = { viewModel.lastPage() },
                onAutoAdvance = { viewModel.autoAdvance() },
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                onCopy = { viewModel.copySelectedBoxes() },
                onPaste = { viewModel.pasteCopiedBoxes() },
                onDuplicate = { viewModel.duplicateSelectedBoxes() },
                onDelete = { viewModel.deleteSelectedBox() },
                currentRotation = uiState.currentRotation,
                onRotateFine = { viewModel.rotatePageFine(it) },
                onToggleRotatePanel = { viewModel.toggleRotatePanel() },
                onRotateLeft = { viewModel.rotatePageFine(-0.5f) },
                onRotateRight = { viewModel.rotatePageFine(0.5f) },
                onZoomIn = { zoomScale = (zoomScale + 0.25f).coerceAtMost(3.0f) },
                onZoomOut = { zoomScale = (zoomScale - 0.25f).coerceAtLeast(0.5f) },
                onZoomFit = { zoomScale = 1.0f },
                onToggleCrosshair = { viewModel.toggleCrosshair() },
                onToggleSnapping = { viewModel.toggleSnapping() },
                isDetectingBoxes = uiState.isDetectingBoxes,
                onAutoDetect = { viewModel.autoDetectBoxes() },
                hasAnyBoxes = uiState.totalBoxesAllPages > 0,
                onFinishLabeling = { viewModel.finishLabelingAndStartTranscription() },
                modifier = Modifier.navigationBarsPadding()
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main Annotation Viewport
            AnnotationCanvasView(
                bitmap = uiState.currentPageBitmap,
                isLoading = uiState.isLoadingPage,
                hasDocument = uiState.hasDocument,
                isRestoringSession = uiState.isRestoringSession,
                onOpenPdf = { openPdfLauncher.launch(arrayOf("application/pdf")) },
                boxes = uiState.currentBoxes,
                classes = uiState.classes,
                activeClassId = uiState.activeClassId,
                selectedBoxIds = uiState.selectedBoxIds,
                primarySelectedBoxId = uiState.primarySelectedBoxId,
                isCrosshairEnabled = uiState.isCrosshairEnabled,
                isSnappingEnabled = uiState.isSnappingEnabled,
                currentRotation = uiState.currentRotation,
                isAlignmentGridEnabled = uiState.isAlignmentGridEnabled,
                zoomScale = zoomScale,
                onBoxAdded = { viewModel.addBox(it) },
                onBoxUpdated = { viewModel.updateBox(it) },
                onBoxSelected = { viewModel.selectBox(it) },
                onClearSelection = { viewModel.clearSelection() }
            )

            // Fine Rotation & Deskew Panel
            AnimatedVisibility(
                visible = uiState.showRotatePanel,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            ) {
                FineRotatePanel(
                    currentRotation = uiState.currentRotation,
                    isAlignmentGridEnabled = uiState.isAlignmentGridEnabled,
                    onRotateFine = { viewModel.rotatePageFine(it) },
                    onSetRotation = { viewModel.setPageRotation(it) },
                    onResetRotation = { viewModel.resetPageRotation() },
                    onRotate90 = { viewModel.rotatePageFine(90f) },
                    onToggleAlignmentGrid = { viewModel.toggleAlignmentGrid() },
                    onClose = { viewModel.setShowRotatePanel(false) }
                )
            }

            // Backdrop Overlay when drawers are opened
            if (showLeftDrawer || showRightDrawer) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            showLeftDrawer = false
                            showRightDrawer = false
                        }
                )
            }

            // Left Drawer (Kelas Label)
            AnimatedVisibility(
                visible = showLeftDrawer,
                enter = slideInHorizontally { -it } + fadeIn(),
                exit = slideOutHorizontally { -it } + fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                LeftClassDrawer(
                    classes = uiState.classes,
                    activeClassId = uiState.activeClassId,
                    onClassSelected = { viewModel.setActiveClass(it) },
                    onToggleVisibility = { viewModel.toggleClassVisibility(it) },
                    onOpenAddClassDialog = { viewModel.showAddClassDialog(true) },
                    onOpenPresetDialog = { viewModel.showPresetDialog(true) },
                    onCloseDrawer = { showLeftDrawer = false },
                    onPrevPage = { viewModel.prevPage() },
                    onNextPage = { viewModel.nextPage() },
                    currentRotation = uiState.currentRotation,
                    onRotateFine = { viewModel.rotatePageFine(it) },
                    onResetRotation = { viewModel.resetPageRotation() },
                    onOpenRotatePanel = {
                        showLeftDrawer = false
                        viewModel.setShowRotatePanel(true)
                    },
                    onRotateLeft = { viewModel.rotatePageFine(-0.5f) },
                    onRotateRight = { viewModel.rotatePageFine(0.5f) },
                    onDeleteSelected = { viewModel.deleteSelectedBox() }
                )
            }

            // Right Drawer (Box List & Inspector)
            AnimatedVisibility(
                visible = showRightDrawer,
                enter = slideInHorizontally { it } + fadeIn(),
                exit = slideOutHorizontally { it } + fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                RightBoxDrawer(
                    currentPage = uiState.currentPage,
                    boxes = uiState.currentBoxes,
                    classes = uiState.classes,
                    selectedBoxIds = uiState.selectedBoxIds,
                    primarySelectedBox = uiState.primarySelectedBox,
                    hasOverlap = uiState.hasOverlap,
                    totalBoxesAllPages = uiState.totalBoxesAllPages,
                    onBoxSelected = { viewModel.selectBox(it) },
                    onDeleteBox = {
                        viewModel.selectBox(it)
                        viewModel.deleteSelectedBox()
                    },
                    onResetProject = {
                        viewModel.resetProject()
                        showRightDrawer = false
                    },
                    onCloseDrawer = { showRightDrawer = false }
                )
            }

            // Floating Toast notification
            AnimatedVisibility(
                visible = uiState.toastMessage != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            ) {
                uiState.toastMessage?.let { msg ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Slate900)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = msg,
                            color = Indigo400,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
    } else {
        val classNameForCurrentLine = uiState.currentTranscriptionLine?.let { line ->
            uiState.classes.find { it.id == line.classId }?.name ?: "Tanpa Kelas"
        } ?: ""

        Box(modifier = Modifier.fillMaxSize()) {
            TranscriptionModeScreen(
                currentIndex = uiState.currentTranscriptionIndex,
                totalLines = uiState.transcriptionLines.size,
                filledCount = uiState.transcriptionFilledCount,
                line = uiState.currentTranscriptionLine,
                className = classNameForCurrentLine,
                isAutoTranscribing = uiState.isAutoTranscribing,
                autoTranscribeCurrent = uiState.autoTranscribeCurrent,
                autoTranscribeTotal = uiState.autoTranscribeTotal,
                onBack = { viewModel.switchToLabelingMode() },
                onExport = { viewModel.showTranscriptionExportDialog(true) },
                onTextChange = { viewModel.updateTranscriptionText(it) },
                onPrev = { viewModel.prevTranscriptionLine() },
                onNext = { viewModel.nextTranscriptionLine() },
                onOpenGeminiSettings = { viewModel.showGeminiSettingsDialog(true) },
                onAutoTranscribeCurrent = { viewModel.autoTranscribeCurrentLine() },
                onAutoTranscribeAll = { viewModel.autoTranscribeAllRemaining() },
                onCancelAutoTranscribe = { viewModel.cancelAutoTranscribeBatch() },
                modifier = Modifier.fillMaxSize()
            )

            // Floating Toast notification (shared with Labeling mode)
            AnimatedVisibility(
                visible = uiState.toastMessage != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            ) {
                uiState.toastMessage?.let { msg ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Slate900)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(text = msg, color = Indigo400, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    // Full-screen busy overlay for background work that blocks interaction
    // (auto-detect inference, cropping boxes into transcription-ready images).
    AnimatedVisibility(
        visible = uiState.isDetectingBoxes || uiState.isPreparingTranscription,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Indigo400)
                Text(
                    text = if (uiState.isDetectingBoxes) "Mendeteksi baris teks..." else "Memotong gambar untuk transkripsi...",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }

    // Dialogs
    if (uiState.showAddClassDialog) {
        AddClassDialog(
            onDismiss = { viewModel.showAddClassDialog(false) },
            onSaveClass = { name, color -> viewModel.addClass(name, color) }
        )
    }

    if (uiState.showPresetDialog) {
        PresetClassesDialog(
            onDismiss = { viewModel.showPresetDialog(false) },
            onSelectPreset = { viewModel.applyPreset(it) }
        )
    }

    if (uiState.showExportDialog) {
        ExportDatasetDialog(
            totalPages = uiState.totalPages,
            isExporting = uiState.isExporting,
            exportProgress = uiState.exportProgress,
            exportStatus = uiState.exportStatus,
            exportedZipFile = uiState.exportedZipFile,
            onDismiss = { viewModel.showExportDialog(false) },
            onStartExport = { format, startP, endP, incImgs, onlyAnno, split, ratio ->
                viewModel.startExport(format, startP, endP, incImgs, onlyAnno, split, ratio)
            }
        )
    }

    if (uiState.showTranscriptionExportDialog) {
        TranscriptionExportDialog(
            totalLines = uiState.transcriptionLines.size,
            filledLines = uiState.transcriptionFilledCount,
            isExporting = uiState.isExporting,
            exportProgress = uiState.exportProgress,
            exportStatus = uiState.exportStatus,
            exportedZipFile = uiState.exportedZipFile,
            onDismiss = { viewModel.showTranscriptionExportDialog(false) },
            onStartExport = { onlyFilled -> viewModel.startTranscriptionExport(onlyFilled) }
        )
    }

    if (uiState.showGeminiSettingsDialog) {
        GeminiSettingsDialog(
            currentApiKey = uiState.geminiApiKey,
            currentModel = uiState.geminiModel,
            onDismiss = { viewModel.showGeminiSettingsDialog(false) },
            onSave = { apiKey, modelName -> viewModel.saveGeminiSettings(apiKey, modelName) }
        )
    }
}

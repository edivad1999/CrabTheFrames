package com.edivad1999.app

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edivad1999.frameextractor.Extraction
import com.edivad1999.frameextractor.FrameExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FrameExtractorViewModel : ViewModel() {

    private val _ui: MutableStateFlow<FrameExtractorUi> =
        MutableStateFlow(FrameExtractorUi.SelectVideo)
    val ui = _ui.asStateFlow()

    private var extraction: Job? = null
    fun extractFrames(context: Context, videoUri: Uri) {
        extraction?.cancel()
        extraction = viewModelScope.launch {
            FrameExtractor.extractFramesToFileAutoRate(
                context = context,
                videoUri = videoUri,
                outputDir = context.cacheDir
            ).map { extractionResult ->
                when (extractionResult) {
                    is Extraction.Error -> FrameExtractorUi.Error(extractionResult.message)

                    is Extraction.Extracting ->
                        FrameExtractorUi.Extracting(
                            extractionResult.frames.map { it.absolutePath },
                            extractionResult.totalFrames
                        )

                    is Extraction.Complete -> FrameExtractorUi.SelectFrames(
                        extractionResult.frames.map { it.absolutePath },
                        extractionResult.totalFrames,
                        emptySet(),
                        null
                    )
                }
            }.onEach {
                _ui.value = it
            }.launchIn(this)
        }
    }

    fun restart() {
        extraction?.cancel()
        extraction = null
        _ui.update { FrameExtractorUi.SelectVideo }
    }

    fun selectFrame(frame: String) {
        val selectFrames = _ui.value as? FrameExtractorUi.SelectFrames ?: return

        val pickedFrames = selectFrames.pickedFrames.let {
            if (it.contains(frame)) it - frame else it + frame
        }
        _ui.update {
            selectFrames.copy(pickedFrames = pickedFrames)
        }
    }

    fun clearSelecting() {
        val selectFrames = _ui.value as? FrameExtractorUi.SelectFrames ?: return
        _ui.update {
            selectFrames.copy(pickedFrames = emptySet())
        }
    }

    fun exportFrames(context: Context) {
        val selectFrames = _ui.value as? FrameExtractorUi.SelectFrames ?: return
        _ui.update {
            selectFrames.copy(
                exportingFrames = FrameExtractorUi.SelectFrames.ExportingFrames(0f)
            )
        }
        viewModelScope.launch(Dispatchers.IO) {

            if (selectFrames.pickedFrames.isNotEmpty()) {
                val target = Environment
                    .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    .resolve("frame extractor")
                    .also {
                        if (!it.exists()) it.mkdirs()
                    }
                selectFrames.pickedFrames.mapIndexed { index, it ->
                    val file = File(it)
                    withContext(Dispatchers.Main) {
                        _ui.update {
                            selectFrames.copy(
                                exportingFrames = FrameExtractorUi.SelectFrames.ExportingFrames(
                                    index.toFloat() / selectFrames.totalFrames.toFloat()
                                )
                            )
                        }
                    }
                    file.copyTo(File(target, file.name), overwrite = true)
                }
                MediaScannerConnection.scanFile(
                    context,
                    target.listFiles()?.map { it.absolutePath }?.toTypedArray(),
                    null,
                    null
                )

                delay(1000)
                withContext(Dispatchers.Main) {
                    _ui.update {
                        selectFrames.copy(exportingFrames = null, pickedFrames = emptySet())
                    }
                }
            }
        }

    }
}

@Stable
sealed interface FrameExtractorUi {
    data object SelectVideo : FrameExtractorUi
    data class Extracting(val frames: List<String>, val totalFrames: Int) : FrameExtractorUi
    data class SelectFrames(
        val frames: List<String>,
        val totalFrames: Int,
        val pickedFrames: Set<String>,
        val exportingFrames: ExportingFrames?
    ) : FrameExtractorUi {
        @Stable
        class ExportingFrames(val percentage: Float)
    }

    data class Error(val message: String) : FrameExtractorUi

}
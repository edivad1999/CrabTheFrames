package com.edivad1999.frameextractor


import FrameExtractorFFMPEG
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import java.io.File

interface FrameExtractor {
    fun extractFramesToFileAutoRate(
        context: Context,
        videoUri: Uri,
        outputDir: File,
    ): Flow<Extraction>

    companion object {


        fun getFrameExtractor(): FrameExtractor = FrameExtractorFFMPEG
    }
}

sealed interface Extraction {
    data class Extracting(val frames: List<File>, val totalFrames: Int) : Extraction
    data class Complete(val frames: List<File>, val totalFrames: Int) : Extraction
    data class Error(val message: String) : Extraction
}

data class ExtractedFrame(
    val bitmap: Bitmap,
    val frameIndex: Int,
    val totalFrames: Int,
) {
    val frameName = "%0${totalFrames.toString().length}d".format(frameIndex)
}


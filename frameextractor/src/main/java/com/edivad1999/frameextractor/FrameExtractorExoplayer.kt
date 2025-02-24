package com.edivad1999.frameextractor

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object FrameExtractorExoplayer : FrameExtractor {
    private const val TAG = "FrameExtractor"

    private fun retriever(context: Context, videoUri: Uri): MediaMetadataRetriever? =
        runCatching {
            MediaMetadataRetriever().apply {
                setDataSource(context, videoUri)
            }
        }.onFailure {
            Log.e(TAG, "Error setting data source: ${it.message}")
        }.getOrNull()

    private fun MediaMetadataRetriever.frameCount(): Int? {
        val frameCountString =
            extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
        val frameCount = frameCountString?.toIntOrNull() ?: run {
            Log.e(TAG, "Could not determine frame count or frame count is null.")
            return null
        }
        if (frameCount <= 0) {
            Log.e(TAG, "Frame count is zero or negative: $frameCount")
            return null
        }
        return frameCount
    }

    // Helper function to get the file name without extension
    private fun getFileNameWithoutExtension(context: Context, uri: Uri): String {
        return when (uri.scheme) {
            "content" -> {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndexOrThrow("_display_name")
                        val fileName = it.getString(nameIndex)
                        fileName.substringBeforeLast(".")
                    } else {
                        "unknown_file"
                    }
                } ?: "unknown_file"
            }

            "file" -> {
                val file = File(uri.path ?: "")
                file.nameWithoutExtension
            }

            else -> "unknown_file"
        }
    }

    override fun extractFramesToFileAutoRate(
        context: Context,
        videoUri: Uri,
        outputDir: File,
    ): Flow<Extraction> = channelFlow {
        val retriever: MediaMetadataRetriever =
            retriever(context, videoUri) ?: kotlin.run {
                send(Extraction.Error("Error setting data source"))
                return@channelFlow
            }

        retriever.use {
            val originalFileName = getFileNameWithoutExtension(context, videoUri)
            val sentFrames = mutableListOf<File>()
            val extractionDir = outputDir.resolve(originalFileName).also {
                it.deleteRecursively()
                it.mkdirs()
            }
            it.mapToFrames().forEach { frame ->
                val outputFileName = "${originalFileName}_frame_${frame.frameName}.png"
                val outputFile = extractionDir.resolve(outputFileName)

                FileOutputStream(outputFile).use { outputStream ->

                    frame.bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                    Log.d(TAG, "Saved frame: $outputFileName")
                }
                frame.bitmap.recycle()
                sentFrames += outputFile
                send(Extraction.Extracting(sentFrames, frame.totalFrames))
            }

            send(Extraction.Complete(sentFrames, sentFrames.size))

        }
    }.flowOn(Dispatchers.Default)


    @OptIn(DelicateCoroutinesApi::class)
    private fun MediaMetadataRetriever.mapToFrames(): Sequence<ExtractedFrame> = sequence {

        val frameCount = frameCount() ?: return@sequence
        val threads = newFixedThreadPoolContext(10, "frameExtractor")
        (0 until frameCount).forEach {
            val bitmap = getFrameAtIndex(it)
            if (bitmap == null) {
                Log.e(TAG, "Error getting frame at index: $it")
            } else {
                yield(ExtractedFrame(bitmap, it, frameCount))
            }
        }

    }
}
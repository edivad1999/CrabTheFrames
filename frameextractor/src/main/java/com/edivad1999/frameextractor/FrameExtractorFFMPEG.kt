import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import com.edivad1999.frameextractor.Extraction
import com.edivad1999.frameextractor.FrameExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import kotlin.math.log10
import kotlin.math.max

object FrameExtractorFFMPEG : FrameExtractor {
    private const val TAG = "FrameExtractorFFMPEG"

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
        outputDir: File
    ): Flow<Extraction> = channelFlow {
        val originalFileName = getFileNameWithoutExtension(context, videoUri)

        val extractionDir = outputDir.resolve(originalFileName).also {
            it.deleteRecursively()
            it.mkdirs()
        }

        val videoPath = getRealPathFromURI(context, videoUri)
            ?: kotlin.run {
                send(Extraction.Error("Failed to get video path from URI"))
                return@channelFlow
            }

        val totalFrames = getTotalFrameCount(videoPath)
        if (totalFrames <= 0) {
            send(Extraction.Error("Failed to determine total frame count"))
            return@channelFlow
        }

        val paddingWidth = max(4, log10(totalFrames.toDouble()).toInt() + 1)

        val outputPattern =
            File(extractionDir, "${originalFileName}_frame_%0${paddingWidth}d.png").absolutePath
        val ffmpegCommand = "-i \"$videoPath\" \"$outputPattern\""

        val frameFiles = {
            extractionDir.listFiles()?.sortedBy { it.name } ?: emptyList()
        }
        var complete = false
        FFmpegKit.executeAsync(ffmpegCommand,
            { session ->
                complete = true
                if (ReturnCode.isSuccess(session.returnCode)) {
                    channel.trySend(Extraction.Complete(frameFiles(), frameFiles().size))
                } else {
                    channel.trySend(Extraction.Error("FFmpeg failed: ${session.failStackTrace}"))
                }
            }, {}, {
                if (!complete) {
                    val frameFiles = frameFiles()
                    channel.trySend(Extraction.Extracting(frameFiles, totalFrames))

                }
            })

        awaitClose()
    }.flowOn(Dispatchers.IO)

    private fun getTotalFrameCount(videoPath: String): Int {
        val session =
            FFprobeKit.execute("-i \"$videoPath\" -select_streams v:0 -show_entries stream=nb_frames -of default=nokey=1:noprint_wrappers=1")
        val output = session.allLogs.last().message.trim()
        return output.toIntOrNull() ?: -1
    }


    private fun getRealPathFromURI(context: Context, uri: Uri): String? {
        val projection = arrayOf(MediaStore.Video.Media.DATA)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            return if (cursor.moveToFirst()) cursor.getString(columnIndex) else null
        }
        return null
    }
}

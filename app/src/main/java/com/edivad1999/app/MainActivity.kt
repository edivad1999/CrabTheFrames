package com.edivad1999.app

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.edivad1999.app.ui.theme.AppTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private val viewModel: FrameExtractorViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                Scaffold(modifier = Modifier.fillMaxSize(), floatingActionButton = {
                    ExtendedFloatingActionButton(
                        onClick = { viewModel.restart() }) {
                        Text(text = "Restart")
                    }

                }) { innerPadding ->
                    val state by viewModel.ui.collectAsStateWithLifecycle()
                    App(
                        modifier = Modifier
                            .padding(innerPadding)
                            .padding(top = 8.dp),
                        state = state,
                        onGalleryPicked = { context, uri ->
                            viewModel.extractFrames(context, uri)
                        },
                        onFrameSelected = { viewModel.selectFrame(it) },
                        onClear = { viewModel.clearSelecting() },
                        onExport = { viewModel.exportFrames(baseContext) }
                    )
                }
            }
        }
    }
}

@Composable
fun App(
    modifier: Modifier,
    state: FrameExtractorUi,
    onGalleryPicked: (Context, Uri) -> Unit,
    onFrameSelected: (String) -> Unit,
    onClear: () -> Unit,
    onExport: () -> Unit

) {
    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        when (state) {
            is FrameExtractorUi.Error -> {
                Text("Error: ${state.message}")
            }

            is FrameExtractorUi.Extracting -> {
                ExtractingFramesScreen(state)
            }

            is FrameExtractorUi.SelectFrames -> {
                SelectFrameScreen(
                    state = state,
                    onFrameSelected = onFrameSelected,
                    onClear = onClear,
                    onExport = onExport
                )

            }

            is FrameExtractorUi.SelectVideo -> VideoSelector(onGalleryPicked)
        }
    }
}

@Composable
fun VideoSelector(onVideoSelected: (Context, Uri) -> Unit) {
    val context = LocalContext.current
    val gallery = rememberGalleryLauncher {
        onVideoSelected(context, it)
    }
    Row(
        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center
    ) {
        Button(onClick = { gallery.launch() }) {
            Text("Pick Medias")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SelectFrameScreen(
    state: FrameExtractorUi.SelectFrames,
    onFrameSelected: (String) -> Unit,
    onClear: () -> Unit,
    onExport: () -> Unit
) {
    var displayFrameBig: String? by remember { mutableStateOf(null) }

    displayFrameBig?.let {
        Dialog(onDismissRequest = { displayFrameBig = null }) {
            Card {
                Box(Modifier.fillMaxWidth()) {
                    FrameImage(it, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
    if (state.exportingFrames != null) Dialog(
        {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card {
            Column {
                Text("Exporting...")
                LinearProgressIndicator(
                    progress = { state.exportingFrames.percentage },
                )
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                    text = "${(state.exportingFrames.percentage * 100).toInt()}%"
                )
            }
        }

    }

    SelectFramesControls(state.pickedFrames.size, onClear = onClear, onExport = onExport)
    FrameGrid(state.frames) {
        val selected = remember(state.pickedFrames, it) { state.pickedFrames.contains(it) }
        FrameImage(it, modifier = Modifier.combinedClickable(onLongClick = {
            displayFrameBig = it
        }) {
            onFrameSelected(it)

        })
        if (selected) Checkbox(true, {}, modifier = Modifier.align(Alignment.TopEnd))
    }
}

@Composable
fun SelectFramesControls(
    selected: Int,
    onClear: () -> Unit,
    onExport: () -> Unit,
) {
    if (selected > 0) Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onClear) {
            Text("Clear")
        }
        Spacer(Modifier.width(8.dp))
        Text("Selected: $selected")
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onExport) {
            Text("Export")
        }
    }
}

@Composable
fun ExtractingFramesScreen(state: FrameExtractorUi.Extracting) {
    Text(text = "Extracting frames... ${state.frames.size}/${state.totalFrames}")
    LinearProgressIndicator(
        progress = {
            state.frames.size.toFloat() / state.totalFrames.toFloat()
        },
        modifier = Modifier.fillMaxWidth(),
    )
    FrameGrid(state.frames)
}

@Composable
fun FrameGrid(
    frames: List<String>,
    frameCard: @Composable BoxScope.(String) -> Unit = { FrameImage(it) }
) {

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(frames) {
            Card(modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) {
                    frameCard(it)
                }

            }
        }
    }
}

@Composable
fun BoxScope.FrameImage(frame: String, modifier: Modifier = Modifier) {
    val file = remember(frame) {
        File(frame)
    }
    AsyncImage(
        file.path,
        file.name,
        modifier = modifier.sizeIn(minHeight = 200.dp, minWidth = 200.dp),
        contentScale = ContentScale.Fit
    )
    Text(
        text = file.name,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.align(Alignment.BottomCenter)
    )
}


@Composable
fun rememberGalleryLauncher(onPickPhotos: (Uri) -> Unit) = rememberLauncherForActivityResult(
    ActivityResultContracts.PickVisualMedia(),
) { uri ->
    if (uri != null) {
        onPickPhotos(uri)
    }
}

fun ManagedActivityResultLauncher<PickVisualMediaRequest, Uri?>.launch() {
    launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
}
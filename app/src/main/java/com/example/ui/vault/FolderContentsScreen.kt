package com.example.ui.vault

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertPhoto
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.database.FolderEntity
import com.example.data.database.VaultItemEntity
import com.example.data.security.StorageManager
import com.example.ui.theme.TextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderContentsScreen(
    viewModel: VaultViewModel,
    folderId: Int,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var folderEntity by remember { mutableStateOf<FolderEntity?>(null) }
    val folders by viewModel.foldersList.collectAsState()
    
    // Bind items inside directory
    val folderItems by viewModel.allItemsList.collectAsState()
    val filteredItems = remember(folderItems, folderId) {
        folderItems.filter { it.folderId == folderId }
    }

    var activeViewerMedia by remember { mutableStateOf<VaultItemEntity?>(null) }

    // Fetch folder info
    LaunchedEffect(folderId, folders) {
        folderEntity = folders.find { it.id == folderId }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.forEach { uri ->
            viewModel.importFileToFolder(context, uri, folderId)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = folderEntity?.name?.uppercase() ?: "DIRECTORY",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("btn_back")) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val fType = folderEntity?.folderType ?: "PHOTO"
                            val mimeType = if (fType == "PHOTO") "image/*" else "video/*"
                            filePickerLauncher.launch(mimeType)
                        },
                        modifier = Modifier.testTag("btn_import_to_folder")
                    ) {
                        Icon(imageVector = Icons.Default.FileUpload, contentDescription = "Import Here")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (filteredItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "This folder is empty.",
                            color = TextMuted,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap the upload icon to import files.",
                            fontSize = 12.sp,
                            color = TextMuted.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().testTag("folder_media_grid")
                ) {
                    items(filteredItems) { item ->
                        MediaGridItem(
                            item = item,
                            decryptedBytesProvider = { viewModel.getDecryptedBytes(context, item.secureFileName) },
                            onClick = { activeViewerMedia = item },
                            onLongClick = { viewModel.deleteItem(context, item) }
                        )
                    }
                }
            }
        }
    }

    // Shared Viewer Modal popup
    activeViewerMedia?.let { media ->
        Dialog(
            onDismissRequest = { activeViewerMedia = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
            ) {
                // Header details
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = media.fileName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = StorageManager.formatFileSize(media.fileSize),
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )
                    }
                    IconButton(
                        onClick = { activeViewerMedia = null },
                        modifier = Modifier.background(Color.DarkGray.copy(alpha = 0.7f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                // Main viewer
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (media.fileType == "PHOTO") {
                        val decryptedBytes = remember(media.id) {
                            viewModel.getDecryptedBytes(context, media.secureFileName)
                        }
                        if (decryptedBytes != null) {
                            AsyncImage(
                                model = decryptedBytes,
                                contentDescription = media.fileName,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Text("Decrypt error.", color = Color.Red)
                        }
                    } else if (media.fileType == "VIDEO") {
                        val decryptedTempFile = remember(media.id) {
                            viewModel.getDecryptedTempFile(context, media.secureFileName)
                        }

                        if (decryptedTempFile != null && decryptedTempFile.exists()) {
                            DisposableEffect(media.id) {
                                onDispose {
                                    decryptedTempFile.delete()
                                }
                            }

                            AndroidView(
                                factory = { ctx ->
                                    android.widget.VideoView(ctx).apply {
                                        setVideoPath(decryptedTempFile.absolutePath)
                                        val mediaController = android.widget.MediaController(ctx)
                                        mediaController.setAnchorView(this)
                                        setMediaController(mediaController)
                                        setOnPreparedListener { start() }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16 / 9f)
                            )
                        } else {
                            Text("Decrypting video streams...", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

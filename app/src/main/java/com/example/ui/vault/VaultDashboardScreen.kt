package com.example.ui.vault

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import com.example.data.security.StorageManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.database.FolderEntity
import com.example.data.database.VaultItemEntity
import com.example.ui.theme.TextMuted
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultDashboardScreen(
    viewModel: VaultViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToFolder: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // ViewModel state collections
    val activeTab by viewModel.activeTab.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val folders by viewModel.foldersList.collectAsState()
    val rootItems by viewModel.rootItemsList.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val storageText by viewModel.storageInfo.collectAsState()
    val totalVaultSize by viewModel.vaultSizeBytes.collectAsState()

    // Dialog state controllers
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showDeleteFolderConfirm by remember { mutableStateOf<FolderEntity?>(null) }
    var activeViewerMedia by remember { mutableStateOf<VaultItemEntity?>(null) }
    var showSearchOverlay by remember { mutableStateOf(false) }

    // Picker launch configurations to securely copy files into sandbox
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.forEach { uri ->
            viewModel.importFile(context, uri)
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.forEach { uri ->
            viewModel.importFile(context, uri)
        }
    }

    // Capture ViewModel toast effects
    LaunchedEffect(Unit) {
        viewModel.seedDemoContentIfEmpty(context)
        viewModel.effects.collect { effect ->
            when (effect) {
                is VaultEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                is VaultEffect.LockVault -> {
                    // Handled inside root navigation
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "SECURE VAULT",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontSize = 20.sp
                    ) 
                },
                actions = {
                    IconButton(
                        onClick = { showSearchOverlay = !showSearchOverlay },
                        modifier = Modifier.testTag("action_search")
                    ) {
                        Icon(
                            imageVector = if (showSearchOverlay) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search Files"
                        )
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("action_settings")
                    ) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
                    }
                    IconButton(
                        onClick = { viewModel.lockVault() },
                        modifier = Modifier.testTag("action_lock")
                    ) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = "Lock Vault", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    showCreateFolderDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("fab_add")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Create Folder or Import")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            verticalArrangement = Arrangement.Top
        ) {
            // Storage Meter Info Banner
            StorageGaugePanel(infoString = storageText, sizeBytes = totalVaultSize)

            // Live Search input line
            AnimatedVisibility(
                visible = showSearchOverlay,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Search files by name...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("search_field"),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            if (showSearchOverlay && searchQuery.trim().isNotEmpty()) {
                // Search Results Grid
                Text(
                    text = "SEARCH RESULTS",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                if (searchResults.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No matching files found.", color = TextMuted)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(searchResults) { item ->
                            MediaGridItem(
                                item = item,
                                decryptedBytesProvider = { viewModel.getDecryptedBytes(context, item.secureFileName) },
                                onClick = { activeViewerMedia = item },
                                onLongClick = { viewModel.deleteItem(context, item) }
                            )
                        }
                    }
                }
            } else {
                // Standard categories layout
                // 1. Photo / Video Tab Layout Row
                TabRow(
                    selectedTabIndex = if (activeTab == "PHOTOS") 0 else 1,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.tabIndicatorOffset(tabPositions[if (activeTab == "PHOTOS") 0 else 1])
                        )
                    }
                ) {
                    Tab(
                        selected = activeTab == "PHOTOS",
                        onClick = { viewModel.setActiveTab("PHOTOS") },
                        text = { Text("PHOTOS", fontWeight = FontWeight.Bold) },
                        modifier = Modifier.testTag("tab_photos")
                    )
                    Tab(
                        selected = activeTab == "VIDEOS",
                        onClick = { viewModel.setActiveTab("VIDEOS") },
                        text = { Text("VIDEOS", fontWeight = FontWeight.Bold) },
                        modifier = Modifier.testTag("tab_videos")
                    )
                }

                // 2. Folders Row area
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "FOLDERS",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (folders.isEmpty()) {
                    Text(
                        text = "No folders in this category. Click '+' below to create one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                } else {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(folders) { folder ->
                            FolderBubble(
                                folder = folder,
                                onClick = { onNavigateToFolder(folder.id) },
                                onDelete = { showDeleteFolderConfirm = folder }
                            )
                        }
                    }
                }

                Divider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // 3. Media file lists (Root files list - matching current type selection)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ROOT SECURED ITEMS",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Direct quick upload launchers
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                if (activeTab == "PHOTOS") {
                                    photoPickerLauncher.launch("image/*")
                                } else {
                                    videoPickerLauncher.launch("video/*")
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileUpload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Direct Import")
                        }
                    }
                }

                if (rootItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (activeTab == "PHOTOS") Icons.Default.InsertPhoto else Icons.Default.Movie,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Secure Vault folder is empty",
                                color = TextMuted,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tap 'Direct Import' above or hold item to delete",
                                fontSize = 12.sp,
                                color = TextMuted.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("media_grid")
                    ) {
                        items(rootItems) { item ->
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
    }

    // Modal Create Folder Action
    if (showCreateFolderDialog) {
        var folderNameInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("New Directory") },
            text = {
                OutlinedTextField(
                    value = folderNameInput,
                    onValueChange = { folderNameInput = it },
                    placeholder = { Text("e.g. Secret Trips, Taxes") },
                    modifier = Modifier.fillMaxWidth().testTag("folder_name_input"),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createNewFolder(folderNameInput)
                        showCreateFolderDialog = false
                    },
                    modifier = Modifier.testTag("folder_confirm")
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete folder confirmation popup
    showDeleteFolderConfirm?.let { folder ->
        AlertDialog(
            onDismissRequest = { showDeleteFolderConfirm = null },
            title = { Text("Delete folder?") },
            text = { Text("Are you sure you want to delete '${folder.name}' and all encrypted photo/video files inside? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteFolder(folder, context)
                        showDeleteFolderConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Cleanly")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteFolderConfirm = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Media Dialog Viewer
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

                // Main viewer engine
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
                            Text("Decrypt failure model corrupted", color = Color.Red)
                        }
                    } else if (media.fileType == "VIDEO") {
                        // Native streaming playback
                        val decryptedTempFile = remember(media.id) {
                            viewModel.getDecryptedTempFile(context, media.secureFileName)
                        }

                        if (decryptedTempFile != null && decryptedTempFile.exists()) {
                            // Dispose-safe Android Video Player wrapper
                            DisposableEffect(media.id) {
                                onDispose {
                                    // Make SURE to delete decrypted video cache on dismissal! Critical for vault boundaries
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
                            Text("Preparing playback streams...", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StorageGaugePanel(infoString: String, sizeBytes: Long) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = "Total Storage",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Encrypted Vault Storage",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            
            // Custom linear gauge
            val ratio = (sizeBytes.toFloat() / (500 * 1024 * 1024f)).coerceIn(0f, 1f) // relative to 500MB simulation
            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = infoString,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderBubble(
    folder: FolderEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(130.dp)
            .height(100.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onDelete
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = "Folder",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = folder.name,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaGridItem(
    item: VaultItemEntity,
    decryptedBytesProvider: () -> ByteArray?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            )
    ) {
        if (item.fileType == "PHOTO") {
            // Load decrypted bytes directly into AsyncImage
            val modelBytes = remember(item.id) { decryptedBytesProvider() }
            if (modelBytes != null) {
                AsyncImage(
                    model = modelBytes,
                    contentDescription = item.fileName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.BrokenImage, contentDescription = null, tint = TextMuted)
                }
            }
        } else {
            // Video placeholder icon representation
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF2C2C2C)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = "Video",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.fileName,
                        color = Color.White,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
            }
        }

        // Inline quick deletion helper
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Delete Securely", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    onLongClick()
                    showMenu = false
                }
            )
        }
    }
}


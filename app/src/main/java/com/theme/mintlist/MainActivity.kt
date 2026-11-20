package com.snhrnck.mintlist

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.snhrnck.mintlist.ui.theme.LocalAppColors
import com.snhrnck.mintlist.ui.theme.ShoppingPlannerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.get(this)
        val prefs = getSharedPreferences("planner_prefs", Context.MODE_PRIVATE)

        setContent {
            var isDark by remember { mutableStateOf(prefs.getBoolean("dark", false)) }
            var lang by remember {
                mutableStateOf(AppLanguage.valueOf(prefs.getString("lang", AppLanguage.RU.name) ?: AppLanguage.RU.name))
            }
            var confirmDelete by remember { mutableStateOf(prefs.getBoolean("confirm_delete", true)) }

            ShoppingPlannerTheme(isDark = isDark) {
                val colors = LocalAppColors.current
                Surface(modifier = Modifier.fillMaxSize(), color = colors.bgColor) {
                    MainApp(
                        db = db,
                        lang = lang,
                        isDark = isDark,
                        confirmDelete = confirmDelete,
                        onUpdateLang = {
                            lang = it
                            prefs.edit().putString("lang", it.name).apply()
                        },
                        onUpdateTheme = {
                            isDark = it
                            prefs.edit().putBoolean("dark", it).apply()
                        },
                        onUpdateConfirmDelete = {
                            confirmDelete = it
                            prefs.edit().putBoolean("confirm_delete", it).apply()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MainApp(
    db: AppDatabase,
    lang: AppLanguage,
    isDark: Boolean,
    confirmDelete: Boolean,
    onUpdateLang: (AppLanguage) -> Unit,
    onUpdateTheme: (Boolean) -> Unit,
    onUpdateConfirmDelete: (Boolean) -> Unit
) {
    var currentScreen by remember { mutableStateOf("HOME") }

    BackHandler(enabled = currentScreen != "HOME") {
        currentScreen = if (currentScreen == "GUIDE") "SETTINGS" else "HOME"
    }

    when (currentScreen) {
        "HOME" -> HomeScreen(
            db = db, lang = lang,
            onNavigateToHistory = { currentScreen = "HISTORY" },
            onNavigateToSettings = { currentScreen = "SETTINGS" }
        )
        "HISTORY" -> HistoryScreen(
            db = db, lang = lang, confirmDelete = confirmDelete,
            onBack = { currentScreen = "HOME" }
        )
        "SETTINGS" -> SettingsScreen(
            db = db, lang = lang, isDark = isDark, confirmDelete = confirmDelete,
            onUpdateLang = onUpdateLang, onUpdateTheme = onUpdateTheme,
            onUpdateConfirmDelete = onUpdateConfirmDelete,
            onNavigateToGuide = { currentScreen = "GUIDE" },
            onBack = { currentScreen = "HOME" }
        )
        "GUIDE" -> GuideScreen(
            lang = lang,
            onBack = { currentScreen = "SETTINGS" }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    db: AppDatabase,
    lang: AppLanguage,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val activeList by db.getActiveList().collectAsState(initial = null)
    val items by remember(activeList?.id) {
        activeList?.let { db.getItemsForList(it.id) } ?: flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        db.getActiveList().collect { list ->
            if (list == null) {
                db.insertList(ShoppingList(title = lang.defaultTitle))
            }
        }
    }

    var inputText by remember { mutableStateOf("") }
    var showNewListDialog by remember { mutableStateOf(false) }
    var showEditTitleDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<ShoppingItem?>(null) }

    val currentViewConfig = LocalViewConfiguration.current
    val oneSecConfig = remember(currentViewConfig) {
        object : ViewConfiguration by currentViewConfig {
            override val longPressTimeoutMillis: Long get() = 1000L
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { showNewListDialog = true },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp)
            ) { Text(lang.newList, maxLines = 1) }

            OutlinedButton(
                onClick = onNavigateToHistory,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp)
            ) { Text(lang.history, maxLines = 1) }

            IconButton(onClick = onNavigateToSettings) {
                Icon(Icons.Default.Settings, contentDescription = lang.settings, tint = colors.textColor)
            }
        }

        Text(
            text = activeList?.title ?: lang.defaultTitle,
            fontSize = 20.sp,
            color = colors.textColor,
            modifier = Modifier
                .padding(top = 16.dp, bottom = 8.dp, start = 4.dp)
                .combinedClickable(onClick = { showEditTitleDialog = true })
        )

        OutlinedTextField(
            value = inputText,
            onValueChange = { newValue ->
                if (newValue.contains("\n")) {
                    val cleanText = newValue.replace("\n", "").trim()
                    if (cleanText.isNotEmpty() && activeList != null) {
                        scope.launch {
                            db.insertItem(ShoppingItem(listId = activeList!!.id, text = cleanText))
                            db.updateList(activeList!!.copy(lastModified = System.currentTimeMillis()))
                        }
                    }
                    inputText = ""
                } else {
                    inputText = newValue
                }
            },
            placeholder = { Text(lang.inputHint, color = colors.textColor.copy(alpha = 0.5f)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                val cleanText = inputText.trim()
                if (cleanText.isNotEmpty() && activeList != null) {
                    scope.launch {
                        db.insertItem(ShoppingItem(listId = activeList!!.id, text = cleanText))
                        db.updateList(activeList!!.copy(lastModified = System.currentTimeMillis()))
                    }
                    inputText = ""
                }
            })
        )

        Spacer(modifier = Modifier.height(12.dp))

        CompositionLocalProvider(LocalViewConfiguration provides oneSecConfig) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items = items, key = { it.id }) { item ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        positionalThreshold = { totalDistance -> totalDistance * 0.4f },
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.StartToEnd) {
                                scope.launch {
                                    db.deleteItem(item)
                                    activeList?.let { db.updateList(it.copy(lastModified = System.currentTimeMillis())) }
                                }
                                true
                            } else false
                        }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = true,
                        enableDismissFromEndToStart = false,
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(colors.swipeDeleteColor, RoundedCornerShape(16.dp))
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = lang.deleteAction, tint = Color.White)
                            }
                        }
                    ) {
                        val borderColor by animateColorAsState(
                            if (item.isChecked) colors.checkedBorder else colors.defaultBorder, label = "border"
                        )
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        scope.launch { db.updateItem(item.copy(isChecked = !item.isChecked)) }
                                    },
                                    onLongClick = { editingItem = item }
                                ),
                            shape = RoundedCornerShape(16.dp),
                            color = colors.cardColor,
                            border = BorderStroke(if (item.isChecked) 2.5.dp else 1.dp, borderColor)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (item.isChecked) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = colors.checkedBorder,
                                        modifier = Modifier.size(20.dp).padding(end = 8.dp)
                                    )
                                }
                                Text(
                                    text = item.text,
                                    color = colors.textColor,
                                    fontSize = 16.sp,
                                    maxLines = Int.MAX_VALUE,
                                    overflow = TextOverflow.Clip
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                activeList?.let { current ->
                    scope.launch {
                        db.updateList(current.copy(isClosed = true, lastModified = System.currentTimeMillis()))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(lang.closeList, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }

    if (showEditTitleDialog) {
        var newTitle by remember { mutableStateOf(activeList?.title ?: "") }
        AlertDialog(
            onDismissRequest = { showEditTitleDialog = false },
            title = { Text(lang.editListTitle) },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    placeholder = { Text(lang.listNameHint) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    activeList?.let {
                        scope.launch { db.updateList(it.copy(title = newTitle.ifBlank { lang.defaultTitle })) }
                    }
                    showEditTitleDialog = false
                }) { Text(lang.save) }
            },
            dismissButton = {
                TextButton(onClick = { showEditTitleDialog = false }) { Text(lang.cancel) }
            }
        )
    }

    if (showNewListDialog) {
        var newTitle by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewListDialog = false },
            title = { Text(lang.newList) },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    placeholder = { Text(lang.listNameHint) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        activeList?.let {
                            if (items.isNotEmpty()) {
                                db.updateList(it.copy(isClosed = true, lastModified = System.currentTimeMillis()))
                                db.insertList(ShoppingList(title = newTitle.ifBlank { lang.defaultTitle }))
                            } else {
                                db.updateList(it.copy(title = newTitle.ifBlank { lang.defaultTitle }))
                            }
                        }
                        showNewListDialog = false
                    }
                }) { Text(lang.save) }
            },
            dismissButton = {
                TextButton(onClick = { showNewListDialog = false }) { Text(lang.cancel) }
            }
        )
    }

    editingItem?.let { itemToEdit ->
        var editedText by remember(itemToEdit) { mutableStateOf(itemToEdit.text) }
        AlertDialog(
            onDismissRequest = { editingItem = null },
            text = {
                OutlinedTextField(
                    value = editedText,
                    onValueChange = { editedText = it },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val clean = editedText.trim()
                    if (clean.isNotEmpty()) {
                        scope.launch {
                            db.updateItem(itemToEdit.copy(text = clean))
                            activeList?.let { db.updateList(it.copy(lastModified = System.currentTimeMillis())) }
                        }
                    }
                    editingItem = null
                }) { Text(lang.save) }
            },
            dismissButton = {
                TextButton(onClick = { editingItem = null }) { Text(lang.cancel) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    db: AppDatabase,
    lang: AppLanguage,
    confirmDelete: Boolean,
    onBack: () -> Unit
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val historyLists by db.getHistoryLists().collectAsState(initial = emptyList())
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    var selectedHistoryList by remember { mutableStateOf<ShoppingList?>(null) }
    var listToDelete by remember { mutableStateOf<ShoppingList?>(null) }

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (selectedHistoryList != null) selectedHistoryList = null else onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.textColor)
            }
            Text(
                text = selectedHistoryList?.title ?: lang.history,
                fontSize = 20.sp,
                color = colors.textColor,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (selectedHistoryList == null) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(historyLists, key = { it.id }) { oldList ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        positionalThreshold = { totalDistance -> totalDistance * 0.4f },
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.StartToEnd) {
                                if (confirmDelete) {
                                    listToDelete = oldList
                                    false
                                } else {
                                    scope.launch { db.deleteList(oldList) }
                                    true
                                }
                            } else false
                        }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = true,
                        enableDismissFromEndToStart = false,
                        backgroundContent = {
                            Box(
                                modifier = Modifier.fillMaxSize().background(colors.swipeDeleteColor, RoundedCornerShape(16.dp)).padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = lang.deleteAction, tint = Color.White)
                            }
                        }
                    ) {
                        Surface(
                            onClick = { selectedHistoryList = oldList },
                            shape = RoundedCornerShape(16.dp),
                            color = colors.cardColor,
                            border = BorderStroke(1.dp, colors.defaultBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(oldList.title, fontSize = 16.sp, color = colors.textColor)
                                    Text(
                                        dateFormat.format(Date(oldList.lastModified)),
                                        fontSize = 12.sp,
                                        color = colors.textColor.copy(alpha = 0.6f)
                                    )
                                }
                                IconButton(onClick = {
                                    scope.launch {
                                        db.cloneList(oldList)
                                        onBack()
                                    }
                                }) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = lang.cloneList, tint = colors.accentColor)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val oldItems by db.getItemsForList(selectedHistoryList!!.id).collectAsState(initial = emptyList())
            val activeList by db.getActiveList().collectAsState(initial = null)
            val copiedIds = remember { mutableStateListOf<Long>() }

            Text(lang.tapToCopyHint, fontSize = 12.sp, color = colors.textColor.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(oldItems, key = { it.id }) { oldItem ->
                    val isCopied = copiedIds.contains(oldItem.id)
                    Surface(
                        onClick = {
                            activeList?.let { current ->
                                scope.launch {
                                    db.insertItem(ShoppingItem(listId = current.id, text = oldItem.text))
                                    db.updateList(current.copy(lastModified = System.currentTimeMillis()))
                                    copiedIds.add(oldItem.id)
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = colors.cardColor,
                        border = BorderStroke(if (isCopied) 2.dp else 1.dp, if (isCopied) colors.checkedBorder else colors.defaultBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (isCopied) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = colors.checkedBorder, modifier = Modifier.size(18.dp).padding(end = 8.dp))
                            }
                            Text(text = oldItem.text, color = colors.textColor, maxLines = Int.MAX_VALUE, overflow = TextOverflow.Clip)
                        }
                    }
                }
            }
        }
    }

    listToDelete?.let { list ->
        AlertDialog(
            onDismissRequest = { listToDelete = null },
            title = { Text(lang.deleteListTitle) },
            text = { Text(list.title) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { db.deleteList(list) }
                    listToDelete = null
                }) { Text(lang.deleteAction, color = colors.swipeDeleteColor) }
            },
            dismissButton = {
                TextButton(onClick = { listToDelete = null }) { Text(lang.cancel) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    db: AppDatabase,
    lang: AppLanguage,
    isDark: Boolean,
    confirmDelete: Boolean,
    onUpdateLang: (AppLanguage) -> Unit,
    onUpdateTheme: (Boolean) -> Unit,
    onUpdateConfirmDelete: (Boolean) -> Unit,
    onNavigateToGuide: () -> Unit,
    onBack: () -> Unit
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var showLangDialog by remember { mutableStateOf(false) }
    var showSupportDialog by remember { mutableStateOf(false) }
    var showThanksImage by remember { mutableStateOf(false) }
    var showClearHistoryConfirmDialog by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                showThanksImage = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.textColor)
                }
                Text(lang.settings, fontSize = 20.sp, color = colors.textColor, modifier = Modifier.padding(start = 8.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    onClick = onNavigateToGuide,
                    shape = RoundedCornerShape(16.dp),
                    color = colors.cardColor,
                    border = BorderStroke(1.dp, colors.defaultBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = colors.textColor)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(lang.guideTitle, color = colors.textColor)
                    }
                }

                Surface(
                    onClick = { showLangDialog = true },
                    shape = RoundedCornerShape(16.dp),
                    color = colors.cardColor,
                    border = BorderStroke(1.dp, colors.defaultBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(lang.language, color = colors.textColor)
                        Text(lang.label, color = colors.accentColor)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(
                        selected = !isDark, onClick = { onUpdateTheme(false) },
                        label = { Text(lang.light) }, modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = isDark, onClick = { onUpdateTheme(true) },
                        label = { Text(lang.dark) }, modifier = Modifier.weight(1f)
                    )
                }

                HorizontalDivider(color = colors.defaultBorder)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(lang.confirmDelete, color = colors.textColor)
                    Switch(checked = confirmDelete, onCheckedChange = onUpdateConfirmDelete)
                }

                HorizontalDivider(color = colors.defaultBorder)

                Button(
                    onClick = { showSupportDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentColor),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(lang.supportAuthor, color = Color.White, textAlign = TextAlign.Center)
                }

                Button(
                    onClick = { showClearHistoryConfirmDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.swipeDeleteColor),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text(lang.clearHistory, color = Color.White, textAlign = TextAlign.Center)
                }
            }
        }

        if (showClearHistoryConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showClearHistoryConfirmDialog = false },
                title = { Text(lang.clearHistory) },
                text = { Text(lang.confirmDelete) },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            db.clearHistoryAndVacuum()
                            context.cacheDir.deleteRecursively()
                        }
                        showClearHistoryConfirmDialog = false
                    }) { Text(lang.deleteAction, color = colors.swipeDeleteColor) }
                },
                dismissButton = {
                    TextButton(onClick = { showClearHistoryConfirmDialog = false }) { Text(lang.cancel) }
                }
            )
        }

        if (showLangDialog) {
            AlertDialog(
                onDismissRequest = { showLangDialog = false },
                title = { Text(lang.language) },
                text = {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(AppLanguage.entries.toTypedArray()) { entry ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            ) {
                                RadioButton(selected = (lang == entry), onClick = {
                                    onUpdateLang(entry)
                                    showLangDialog = false
                                })
                                Text(entry.label, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLangDialog = false }) { Text(lang.cancel) }
                }
            )
        }

        if (showSupportDialog) {
            val walletPolygon = "0x0E9ECfe7eE9E0e2FEf6462A39e9B7a3e271BE7E9"
            val walletSolana = "67dMBxg5yXZEudrK2G1u5QYJJgQ7sp8jHDcWqZjQbsMH"
            val walletTron = "TUEZPrkvfcGHmaj6Uomjo1t5n35r1y3dy1"

            AlertDialog(
                onDismissRequest = { showSupportDialog = false },
                title = { Text(lang.supportAuthor) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        NetworkCopyRow("Polygon / Base / BNB", walletPolygon, lang.copyAddress) {
                            clipboardManager.setText(AnnotatedString(walletPolygon))
                            showSupportDialog = false
                            showThanksImage = true
                        }
                        NetworkCopyRow("Solana", walletSolana, lang.copyAddress) {
                            clipboardManager.setText(AnnotatedString(walletSolana))
                            showSupportDialog = false
                            showThanksImage = true
                        }
                        NetworkCopyRow("Tron", walletTron, lang.copyAddress) {
                            clipboardManager.setText(AnnotatedString(walletTron))
                            showSupportDialog = false
                            showThanksImage = true
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSupportDialog = false }) { Text("OK") }
                }
            )
        }

        if (showThanksImage) {
            DisposableEffect(Unit) {
                val mediaPlayer = android.media.MediaPlayer.create(context, R.raw.lofi_thanks)
                mediaPlayer?.setVolume(1f, 1f)
                mediaPlayer?.start()
                onDispose {
                    mediaPlayer?.let { player ->
                        try {
                            if (player.isPlaying) player.stop()
                            player.release()
                        } catch (e: Exception) { }
                    }
                }
            }

            val bpm = 72f
            val beatDurationMillis = (60000f / bpm).toInt()

            val infiniteTransition = rememberInfiniteTransition(label = "raccoon_pulse")
            val scale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.08f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = beatDurationMillis
                        1f at 0
                        1.08f at (beatDurationMillis / 2)
                    },
                    repeatMode = RepeatMode.Reverse
                ),
                label = "raccoon_scale"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showThanksImage = false },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.raccoon_thanks),
                    contentDescription = "Thank you",
                    modifier = Modifier
                        .size(300.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { },
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

@Composable
fun GuideScreen(
    lang: AppLanguage,
    onBack: () -> Unit
) {
    val colors = LocalAppColors.current

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.textColor)
            }
            Text(lang.guideTitle, fontSize = 20.sp, color = colors.textColor, modifier = Modifier.padding(start = 8.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = lang.guideText,
                color = colors.textColor,
                fontSize = 15.sp,
                lineHeight = 24.sp
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun NetworkCopyRow(networkName: String, address: String, copyText: String, onCopy: () -> Unit) {
    val colors = LocalAppColors.current
    Surface(
        color = colors.bgColor,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, colors.defaultBorder)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(networkName, color = colors.textColor, fontSize = 14.sp)
                Text(
                    address.take(6) + "..." + address.takeLast(4),
                    color = colors.textColor.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
            TextButton(onClick = onCopy) {
                Text(copyText, textAlign = TextAlign.Center)
            }
        }
    }
}
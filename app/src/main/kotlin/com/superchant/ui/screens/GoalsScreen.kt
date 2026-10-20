package com.superchant.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.superchant.R
import com.superchant.data.Goal
import com.superchant.data.MantraGoal
import com.superchant.data.PreferencesRepository
import com.superchant.data.newId
import com.superchant.ui.components.AppTopBar
import com.superchant.ui.components.BackButton
import com.superchant.ui.components.ConfirmDestructiveDialog
import com.superchant.ui.components.EmptyState
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** An open goal editor: [goal] is null when creating. [key] identifies this opening and is the new goal's ID. */
@Serializable
private data class EditorRequest(val key: String = newId(), val goal: Goal? = null)

private val editorRequestSaver = Saver<EditorRequest?, String>(
    save = { request -> request?.let { Json.encodeToString(EditorRequest.serializer(), it) } },
    restore = { Json.decodeFromString(EditorRequest.serializer(), it) }
)

private val mantraListSerializer = ListSerializer(MantraGoal.serializer())

private val mantraListSaver = Saver<SnapshotStateList<MantraGoal>, String>(
    save = { Json.encodeToString(mantraListSerializer, it.toList()) },
    restore = { Json.decodeFromString(mantraListSerializer, it).toMutableStateList() }
)

@Composable
fun GoalsScreen(
    preferencesRepository: PreferencesRepository,
    onBackClick: () -> Unit,
    onStartGoal: (Goal) -> Unit
) {
    // null until loaded, so the empty state doesn't flash before the data arrives
    val loadedGoals by preferencesRepository.goals.collectAsStateWithLifecycle()
    val goals = loadedGoals ?: emptyList()
    
    // Screen states (saveable, so rotation keeps an open editor and its draft)
    var editor by rememberSaveable(stateSaver = editorRequestSaver) { mutableStateOf<EditorRequest?>(null) }
    var goalToDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Main goals list
        Scaffold(
            topBar = {
                AppTopBar(title = stringResource(R.string.goals), navigationIcon = { BackButton(onBackClick) })
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { editor = EditorRequest() },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.goals_create_goal)
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        ) { paddingValues ->
            if (loadedGoals?.isEmpty() == true) {
                EmptyState(
                    icon = Icons.Outlined.Flag,
                    title = stringResource(R.string.goals_empty_title),
                    message = stringResource(R.string.goals_empty_message),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    action = {
                        Button(onClick = { editor = EditorRequest() }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.goals_create_first))
                        }
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(goals, key = { it.id }) { goal ->
                        GoalCard(
                            goal = goal,
                            onStart = { onStartGoal(goal) },
                            onEdit = { editor = EditorRequest(goal = goal) },
                            onDelete = { goalToDeleteId = goal.id }
                        )
                    }
                }
            }
        }
        
        // Create/Edit Goal full screen. Each opening keeps its own request while it animates out,
        // so the title and the goal being saved can't change mid-exit.
        AnimatedContent(
            targetState = editor,
            contentKey = { it?.key },
            transitionSpec = {
                if (targetState != null) {
                    (slideInVertically(initialOffsetY = { it }) + fadeIn()) togetherWith ExitTransition.None
                } else {
                    EnterTransition.None togetherWith (slideOutVertically(targetOffsetY = { it }) + fadeOut())
                }.using(null) // full screen either way; no size animation
            },
            modifier = Modifier.fillMaxSize(),
            label = "goalEditor"
        ) { request ->
            if (request != null) {
                GoalEditorScreen(
                    request = request,
                    isActive = editor?.key == request.key,
                    onSave = { goal ->
                        preferencesRepository.write { saveGoal(goal) }
                        editor = null
                    },
                    onClose = { editor = null }
                )
            }
        }
        
        // Delete Confirmation Dialog
        goals.find { it.id == goalToDeleteId }?.let { goal ->
            ConfirmDestructiveDialog(
                title = stringResource(R.string.goals_delete_title),
                text = stringResource(R.string.goals_delete_message, goal.name),
                confirmLabel = stringResource(R.string.common_delete),
                onConfirm = {
                    preferencesRepository.write { deleteGoal(goal.id) }
                    goalToDeleteId = null
                },
                onDismiss = { goalToDeleteId = null }
            )
        }
    }
}

@Composable
private fun GoalCard(
    goal: Goal,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onStart),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = goal.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = pluralStringResource(R.plurals.mantras_count, goal.mantras.size, goal.mantras.size) +
                            " • " + pluralStringResource(R.plurals.rounds_total, goal.totalRounds, goal.totalRounds),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.goals_options),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.common_edit)) },
                            onClick = {
                                showMenu = false
                                onEdit()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete, 
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Mantras preview (show first 3, then count)
            val previewMantras = goal.mantras.take(3)
            previewMantras.forEachIndexed { index, mantra ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = mantra.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${mantra.targetRounds}×",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            if (goal.mantras.size > 3) {
                Text(
                    text = (goal.mantras.size - 3).let { pluralStringResource(R.plurals.goals_more_mantras, it, it) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, start = 18.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Start button
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(12.dp),
                onClick = onStart
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.goals_start_session),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

/**
 * Full-screen goal editor. [isActive] is false while it animates out; it then ignores input.
 * Closing with unsaved changes (X or system back) asks before discarding them.
 */
@Composable
private fun GoalEditorScreen(
    request: EditorRequest,
    isActive: Boolean,
    onSave: (Goal) -> Unit,
    onClose: () -> Unit
) {
    val existingGoal = request.goal
    val isEditing = existingGoal != null
    val initialName = existingGoal?.name ?: ""
    val initialMantras = existingGoal?.mantras ?: emptyList()
    var goalName by rememberSaveable { mutableStateOf(initialName) }
    val mantras = rememberSaveable(saver = mantraListSaver) { initialMantras.toMutableStateList() }
    var editingMantraIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var showAddMantra by rememberSaveable { mutableStateOf(false) }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    
    val isValid = goalName.isNotBlank() && mantras.isNotEmpty()
    val isDirty = goalName != initialName || mantras.toList() != initialMantras
    val requestClose = {
        if (isDirty) showDiscardDialog = true else onClose()
    }
    
    // System back closes the editor instead of leaving the Goals screen.
    BackHandler(enabled = isActive) { requestClose() }
    
    // Reorderable state
    val listState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        mantras.add(to.index, mantras.removeAt(from.index))
    }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Scaffold(
            topBar = {
                AppTopBar(
                    title = stringResource(if (isEditing) R.string.goals_edit_goal else R.string.goals_create_goal),
                    navigationIcon = {
                        IconButton(onClick = requestClose, enabled = isActive) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.cancel)
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                if (isValid && isActive) {
                                    onSave(
                                        Goal(
                                            // Fixed per editor opening, so a repeated save updates instead of duplicating.
                                            id = existingGoal?.id ?: request.key,
                                            name = goalName.trim(),
                                            mantras = mantras.toList(),
                                            createdAt = existingGoal?.createdAt ?: System.currentTimeMillis()
                                        )
                                    )
                                }
                            },
                            enabled = isValid && isActive
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(R.string.common_save),
                                tint = if (isValid) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                }
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Goal Name Input
                OutlinedTextField(
                    value = goalName,
                    onValueChange = { goalName = it },
                    label = { Text(stringResource(R.string.goals_name_label)) },
                    placeholder = { Text(stringResource(R.string.goals_name_placeholder)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                
                // Mantras Section Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.goals_mantras),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (mantras.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.goals_reorder_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TextButton(onClick = { showAddMantra = true }) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.common_add))
                    }
                }
                
                if (mantras.isEmpty()) {
                    // Empty state for mantras
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.goals_no_mantras),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.goals_no_mantras_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                } else {
                    // Mantras list with drag reorder
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(mantras, key = { _, item -> item.id }) { index, mantra ->
                            ReorderableItem(reorderableState, key = mantra.id) { isDragging ->
                                val elevation by animateDpAsState(
                                    targetValue = if (isDragging) 8.dp else 0.dp,
                                    label = "elevation"
                                )
                                
                                MantraListItem(
                                    index = index,
                                    mantra = mantra,
                                    isDragging = isDragging,
                                    elevation = elevation,
                                    onEdit = { editingMantraIndex = index },
                                    onDelete = { mantras.remove(mantra) },
                                    // Dragging needs a long press and a drag; screen readers get these instead
                                    onMoveUp = if (index > 0) { { mantras.add(index - 1, mantras.removeAt(index)) } } else null,
                                    onMoveDown = if (index < mantras.lastIndex) { { mantras.add(index + 1, mantras.removeAt(index)) } } else null,
                                    modifier = Modifier.longPressDraggableHandle()
                                )
                            }
                        }
                    }
                }
                
                // Summary footer
                if (mantras.isNotEmpty()) {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.goals_total),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = mantras.sumOf { it.targetRounds }.let { pluralStringResource(R.plurals.rounds_count, it, it) },
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        
        // Add Mantra Dialog
        if (showAddMantra) {
            MantraEditorDialog(
                existingMantra = null,
                onSave = { mantra ->
                    mantras.add(mantra)
                    showAddMantra = false
                },
                onCancel = { showAddMantra = false }
            )
        }
        
        // Edit Mantra Dialog
        editingMantraIndex?.let { index ->
            MantraEditorDialog(
                existingMantra = mantras.getOrNull(index),
                onSave = { mantra ->
                    mantras[index] = mantra
                    editingMantraIndex = null
                },
                onCancel = { editingMantraIndex = null }
            )
        }
        
        if (showDiscardDialog) {
            ConfirmDestructiveDialog(
                title = stringResource(R.string.goals_discard_title),
                text = stringResource(R.string.goals_discard_message),
                confirmLabel = stringResource(R.string.goals_discard_confirm),
                onConfirm = {
                    showDiscardDialog = false
                    onClose()
                },
                onDismiss = { showDiscardDialog = false }
            )
        }
    }
}

@Composable
private fun MantraListItem(
    index: Int,
    mantra: MantraGoal,
    isDragging: Boolean,
    elevation: Dp,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val moveUpLabel = stringResource(R.string.goals_move_up)
    val moveDownLabel = stringResource(R.string.goals_move_down)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation, RoundedCornerShape(12.dp))
            .semantics {
                customActions = listOfNotNull(
                    onMoveUp?.let { move -> CustomAccessibilityAction(moveUpLabel) { move(); true } },
                    onMoveDown?.let { move -> CustomAccessibilityAction(moveDownLabel) { move(); true } }
                )
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag handle
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = stringResource(R.string.goals_drag_to_reorder),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Index badge
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Mantra info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mantra.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = pluralStringResource(R.plurals.rounds_count, mantra.targetRounds, mantra.targetRounds),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Edit button
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.common_edit),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            
            // Delete button
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.common_delete),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun MantraEditorDialog(
    existingMantra: MantraGoal?,
    onSave: (MantraGoal) -> Unit,
    onCancel: () -> Unit
) {
    val isEditing = existingMantra != null
    var mantraName by rememberSaveable { mutableStateOf(existingMantra?.name ?: "") }
    var targetRounds by rememberSaveable { mutableStateOf(existingMantra?.targetRounds?.toString() ?: "1") }
    
    val nameFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    
    val isValid = mantraName.isNotBlank() && (targetRounds.toIntOrNull() ?: 0) > 0
    val submit = {
        if (isValid) {
            onSave(
                MantraGoal(
                    id = existingMantra?.id ?: newId(),
                    name = mantraName.trim(),
                    targetRounds = targetRounds.toIntOrNull() ?: 1
                )
            )
        }
    }
    
    LaunchedEffect(Unit) {
        if (!isEditing) {
            nameFocusRequester.requestFocus()
        }
    }
    
    AlertDialog(
        onDismissRequest = onCancel,
        title = { 
            Text(stringResource(if (isEditing) R.string.goals_edit_mantra else R.string.goals_add_mantra)) 
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = mantraName,
                    onValueChange = { mantraName = it },
                    label = { Text(stringResource(R.string.goals_mantra_name_label)) },
                    placeholder = { Text(stringResource(R.string.goals_mantra_name_placeholder)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(nameFocusRequester),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(12.dp)
                )
                
                OutlinedTextField(
                    value = targetRounds,
                    onValueChange = { 
                        if (it.isEmpty() || (it.all { c -> c.isDigit() } && it.length <= 3)) {
                            targetRounds = it
                        }
                    },
                    label = { Text(stringResource(R.string.goals_target_rounds_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            submit()
                        }
                    ),
                    shape = RoundedCornerShape(12.dp),
                    supportingText = {
                        Text(stringResource(R.string.goals_round_is_108))
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = submit, enabled = isValid) {
                Text(stringResource(if (isEditing) R.string.common_save else R.string.common_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

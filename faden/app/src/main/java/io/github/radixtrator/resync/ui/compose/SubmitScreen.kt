@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.radixtrator.resync.ui.compose

import io.github.radixtrator.resync.ui.common.Symbols
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.radixtrator.resync.AppContainer
import io.github.radixtrator.resync.data.GalleryUpload
import io.github.radixtrator.resync.data.LinkFlair
import io.github.radixtrator.resync.SharedContent
import io.github.radixtrator.resync.i18n.t
import io.github.radixtrator.resync.i18n.tc
import io.github.radixtrator.resync.ui.feed.RulesSheet
import io.github.radixtrator.resync.ui.navigation.LocalNavigator
import io.github.radixtrator.resync.ui.navigation.PostRoute
import io.github.radixtrator.resync.ui.navigation.SubmitRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The draft key of a new post's body; there's one post draft at a time. */
private const val SUBMIT_DRAFT = "submit"

class SubmitViewModel(private val container: AppContainer) : ViewModel() {
    data class State(val sending: Boolean = false, val postId: String? = null, val error: String? = null)

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    fun submit(subreddit: String, title: String, isLink: Boolean, body: String, nsfw: Boolean, spoiler: Boolean, sendReplies: Boolean, flair: LinkFlair?) {
        if (_state.value.sending) return
        if (container.username.value == null) {
            _state.value = State(error = t("Log in to post"))
            return
        }
        _state.value = State(sending = true)
        viewModelScope.launch {
            runCatching {
                container.api.submit(subreddit.trim().removePrefix("r/").removePrefix("/r/"), title.trim(), isLink, body.trim(), nsfw, spoiler, sendReplies, flair)
            }.onSuccess {
                container.drafts.clear(SUBMIT_DRAFT)
                _state.value = State(postId = it)
            }.onFailure { e -> _state.value = State(error = e.message ?: t("Couldn't post")) }
        }
    }

    /**
     * A photo post from the picked image(s) at [uris]: one makes a photo post, more a gallery. Finishes with the post's
     * id, or goes back with a note if Reddit hasn't listed a photo post yet.
     */
    fun submitPhotos(context: android.content.Context, subreddit: String, title: String, uris: List<android.net.Uri>, nsfw: Boolean, spoiler: Boolean, sendReplies: Boolean, flair: LinkFlair?) {
        if (_state.value.sending || uris.isEmpty()) return
        val me = container.username.value
        if (me == null) {
            _state.value = State(error = t("Log in to post"))
            return
        }
        _state.value = State(sending = true)
        viewModelScope.launch {
            runCatching {
                val community = subreddit.trim().removePrefix("r/").removePrefix("/r/")
                val images = uris.mapIndexed { i, uri -> read(context, uri, if (uris.size == 1) "photo" else "photo${i + 1}") }
                if (images.size == 1) {
                    val image = images.single()
                    container.api.submitImage(community, title.trim(), me, image.filename, image.mime, image.bytes, nsfw, spoiler, sendReplies, flair)
                } else {
                    container.api.submitGallery(community, title.trim(), images, nsfw, spoiler, sendReplies, flair)
                }
            }.onSuccess { id ->
                container.drafts.clear(SUBMIT_DRAFT)
                _state.value = if (id != null) State(postId = id) else State(error = t("Posted. Reddit is still processing the photo; check your profile"))
            }
                .onFailure { e -> _state.value = State(error = e.message ?: t("Couldn't post")) }
        }
    }

    private suspend fun read(context: android.content.Context, uri: android.net.Uri, base: String): GalleryUpload {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "image/jpeg"
        val bytes = withContext(Dispatchers.IO) { resolver.openInputStream(uri)?.use { it.readBytes() } } ?: error(t("Couldn't read that image"))
        val name = "$base." + (mime.substringAfter('/', "jpg").substringBefore(';').let { if (it == "jpeg") "jpg" else it })
        return GalleryUpload(name, mime, bytes)
    }

    fun errorShown() = _state.update { it.copy(error = null) }
}

/** Text, link, photo and gallery posts; [route] can prefill one with something shared to Faden. */
@Composable
fun SubmitScreen(route: SubmitRoute, container: AppContainer) {
    val nav = LocalNavigator.current
    val vm = viewModel { SubmitViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    var subreddit by rememberSaveable { mutableStateOf(route.subreddit.orEmpty()) }
    var title by rememberSaveable { mutableStateOf(route.title) }
    var isLink by rememberSaveable { mutableStateOf(route.url.isNotEmpty() && route.photos.isEmpty()) }
    var isPhoto by rememberSaveable { mutableStateOf(route.photos.isNotEmpty()) }
    // One picture is a photo post, two or more a gallery (up to Reddit's 20).
    var photos by rememberSaveable { mutableStateOf(ArrayList(route.photos)) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) photos = ArrayList((photos + uris.map { it.toString() }).distinct().take(SharedContent.GALLERY_MAX))
    }
    var body by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        // Shared text starts the post; otherwise the unsent draft comes back.
        val draft = route.text.ifEmpty { container.drafts.get(SUBMIT_DRAFT)?.text.orEmpty() }
        mutableStateOf(TextFieldValue(draft, TextRange(draft.length)))
    }
    val editBody: (TextFieldValue) -> Unit = {
        body = it
        container.drafts.save(SUBMIT_DRAFT, it.text, tc("noun", "Post"))
    }
    val imageUpload = rememberComposerImageUpload(container) { uploaded -> editBody(body.insertImageLink(uploaded)) }
    var url by rememberSaveable { mutableStateOf(route.url) }
    var nsfw by rememberSaveable { mutableStateOf(false) }
    var spoiler by rememberSaveable { mutableStateOf(false) }
    var sendReplies by rememberSaveable { mutableStateOf(true) }
    var flairText by rememberSaveable { mutableStateOf<String?>(null) }
    var flairId by rememberSaveable { mutableStateOf<String?>(null) }
    var flairColor by rememberSaveable { mutableStateOf<Int?>(null) }
    var flairOpen by rememberSaveable { mutableStateOf(false) }
    var rulesOpen by rememberSaveable { mutableStateOf(false) }
    val community = subreddit.trim().removePrefix("r/").removePrefix("/r/")
    val snackbar = remember { SnackbarHostState() }
    val canPost = subreddit.isNotBlank() && title.isNotBlank() && (isPhoto && photos.isNotEmpty() || !isPhoto && (!isLink || url.startsWith("http"))) && !state.sending

    LaunchedEffect(state.postId) { state.postId?.let { nav.replaceTop(PostRoute(it)) } }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            vm.errorShown()
        }
    }

    if (flairOpen) {
        FlairPickerSheet(container, community, onDismiss = { flairOpen = false }) { picked ->
            flairText = picked?.text
            flairId = picked?.id
            flairColor = picked?.color
            flairOpen = false
        }
    }
    if (rulesOpen) RulesSheet(container, community, onDismiss = { rulesOpen = false })

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = nav::pop) { Icon(Symbols.Close, t("Discard")) } },
                title = { Text(t("Create post")) },
                actions = {
                    if (state.sending) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(
                            enabled = canPost,
                            onClick = {
                                val flair = flairText?.let { LinkFlair(it, flairColor, flairId) }
                                if (isPhoto) {
                                    vm.submitPhotos(context, subreddit, title, photos.map(android.net.Uri::parse), nsfw, spoiler, sendReplies, flair)
                                } else {
                                    vm.submit(subreddit, title, isLink, if (isLink) url else body.text, nsfw, spoiler, sendReplies, flair)
                                }
                            },
                        ) { Text(t("Post")) }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = subreddit,
                onValueChange = {
                    // A flair belongs to its community.
                    if (it.trim().removePrefix("r/") != subreddit) {
                        flairText = null
                        flairId = null
                    }
                    subreddit = it.trim().removePrefix("r/")
                },
                label = { Text(t("Community")) },
                prefix = { Text("r/") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (community.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { flairOpen = true }, label = { Text(flairText?.let { t("Flair: {0}", it) } ?: t("Add flair")) })
                    AssistChip(onClick = { rulesOpen = true }, label = { Text(t("Community rules")) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !isLink && !isPhoto, onClick = { isLink = false; isPhoto = false }, label = { Text(t("Text")) })
                FilterChip(selected = isLink && !isPhoto, onClick = { isLink = true; isPhoto = false }, label = { Text(t("Link")) })
                FilterChip(selected = isPhoto, onClick = { isPhoto = true }, label = { Text(if (photos.size > 1) t("Gallery") else t("Photo")) })
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(300) },
                label = { Text(t("Title")) },
                supportingText = { Text("${title.length}/300") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (isPhoto) {
                PhotoPicks(
                    photos = photos,
                    onAdd = { picker.launch("image/*") },
                    onRemove = { uri -> photos = ArrayList(photos - uri) },
                )
            } else if (isLink) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it.trim() },
                    label = { Text("URL") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                MarkdownToolbar(body, editBody, drafts = container.drafts, draftKey = SUBMIT_DRAFT, imageUpload = imageUpload)
                OutlinedTextField(
                    value = body,
                    onValueChange = editBody,
                    placeholder = { Text(t("Body (optional, markdown)")) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = nsfw, onCheckedChange = { nsfw = it })
                Text(t("NSFW"))
                Checkbox(checked = spoiler, onCheckedChange = { spoiler = it })
                Text(t("Spoiler"))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = sendReplies, onCheckedChange = { sendReplies = it })
                Text(t("Send replies to my inbox"))
            }
        }
    }
}

/** The picked pictures in order, each with a remove button, and Add while there's room for more. */
@Composable
private fun PhotoPicks(photos: List<String>, onAdd: () -> Unit, onRemove: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (photos.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(photos.size, key = { photos[it] }) { i ->
                    val uri = photos[i]
                    Box {
                        coil3.compose.AsyncImage(
                            model = uri,
                            contentDescription = t("Picture {0}", i + 1),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(if (photos.size == 1) 200.dp else 112.dp)
                                .clip(MaterialTheme.shapes.medium),
                        )
                        FilledTonalIconButton(
                            onClick = { onRemove(uri) },
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(32.dp),
                        ) { Icon(Symbols.Close, t("Remove picture {0}", i + 1), modifier = Modifier.size(18.dp)) }
                    }
                }
            }
        }
        if (photos.size < SharedContent.GALLERY_MAX) {
            TextButton(onClick = onAdd) {
                Text(if (photos.isEmpty()) t("Choose photos") else t("Add more photos ({0}/{1})", photos.size, SharedContent.GALLERY_MAX))
            }
        }
        if (photos.size > 1) {
            Text(t("{0} pictures make a gallery post", photos.size), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Flairs the community lets you pick on a new post (only those with a template id can be submitted). */
@Composable
private fun FlairPickerSheet(container: AppContainer, subreddit: String, onDismiss: () -> Unit, onPick: (LinkFlair?) -> Unit) {
    val flairs by androidx.compose.runtime.produceState<Result<List<LinkFlair>>?>(null, subreddit) {
        value = runCatching { container.api.linkFlairs(subreddit).filter { it.id != null } }
            .onFailure { if (it is kotlin.coroutines.cancellation.CancellationException) throw it }
    }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t("Flair"), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            when (val result = flairs) {
                null -> CircularProgressIndicator(Modifier.padding(16.dp))
                else -> result.fold(
                    onSuccess = { list ->
                        if (list.isEmpty()) Text(t("r/{0} has no flairs you can pick", subreddit))
                        TextButton(onClick = { onPick(null) }) { Text(t("No flair")) }
                        list.forEach { f -> AssistChip(onClick = { onPick(f) }, label = { Text(f.text) }) }
                    },
                    onFailure = { Text(t("Couldn't load flairs: {0}", it.message)) },
                )
            }
        }
    }
}

package io.github.radixtrator.resync.data

/** A comment or "more" row as shown, with how many rows are hidden under it when collapsed. */
data class VisibleNode(val node: CommentNode, val hiddenCount: Int)

object CommentTree {

    /** Drops descendants of collapsed comments. Relies on [all] being in depth-first order. */
    fun visible(all: List<CommentNode>, collapsed: Set<String>): List<VisibleNode> {
        val out = ArrayList<VisibleNode>(all.size)
        var hideDeeperThan = Int.MAX_VALUE
        var hidden = 0
        for (node in all) {
            if (node.depth > hideDeeperThan) {
                if (node is Comment) hidden++
                continue
            }
            if (hidden > 0) out[out.lastIndex] = out.last().copy(hiddenCount = hidden)
            hideDeeperThan = Int.MAX_VALUE
            hidden = 0
            out += VisibleNode(node, 0)
            if (node is Comment && node.id in collapsed) hideDeeperThan = node.depth
        }
        if (hidden > 0) out[out.lastIndex] = out.last().copy(hiddenCount = hidden)
        return out
    }

    /**
     * Replaces [more] in [all] with [loaded] (from [mergeThreads]), fixing up depths from the parent chain since
     * the loaded pages count depth from their own root. [remaining] child IDs that weren't loaded stay behind as a
     * smaller "more" row.
     */
    fun splice(
        all: List<CommentNode>,
        more: MoreComments,
        loaded: List<CommentNode>,
        remaining: List<String>,
    ): List<CommentNode> {
        val index = all.indexOfFirst { it is MoreComments && it.id == more.id }
        if (index < 0) return all
        val existing = all.filterIsInstance<Comment>().mapTo(HashSet()) { it.id }
        val depths = all.associateTo(HashMap()) { "t1_${it.id}" to it.depth }

        val fresh = loaded.filter { it !is Comment || it.id !in existing }.map { node ->
            val depth = depths[node.parentId]?.plus(1) ?: more.depth
            if (node is Comment) depths["t1_${node.id}"] = depth
            when (node) {
                is Comment -> node.copy(depth = depth)
                is MoreComments -> node.copy(depth = depth)
            }
        }
        val leftover = if (remaining.isEmpty()) emptyList() else listOf(more.copy(children = remaining, count = remaining.size))
        return all.subList(0, index) + fresh + leftover + all.subList(index + 1, all.size)
    }

    /** What loading a "more" row gave: comments in depth-first order, and the child IDs still to load. */
    data class MoreLoaded(val nodes: List<CommentNode>, val remaining: List<String>)

    /** [id]'s comment and everything under it, from a page focused on it; empty if it's gone (deleted, removed). */
    fun subtree(nodes: List<CommentNode>, id: String): List<CommentNode> {
        val start = nodes.indexOfFirst { it is Comment && it.id == id }
        if (start < 0) return emptyList()
        val depth = nodes[start].depth
        val end = (start + 1 until nodes.size).firstOrNull { nodes[it].depth <= depth } ?: nodes.size
        return nodes.subList(start, end)
    }

    /**
     * Joins the threads loaded for some of a "more" row's child IDs ([threads]: each ID with its [subtree], or null
     * if loading it failed) and works out which IDs are left, of those plus the unrequested [rest].
     *
     * A row's IDs include replies of other IDs in it, so a thread that turned up inside another (as a reply, or on
     * one of its own "more" rows) is dropped, and so are such IDs in [rest]. A thread whose parent is still to
     * load waits until after it, so it isn't shown out of place; failed IDs come first, to be tried again.
     */
    fun mergeThreads(threads: List<Pair<String, List<CommentNode>?>>, rest: List<String>): MoreLoaded {
        // Every comment some loaded thread brings below its root, or leaves on one of its own "more" rows.
        val within = HashSet<String>()
        for ((id, nodes) in threads) {
            for (node in nodes.orEmpty()) {
                if (node is MoreComments) within.addAll(node.children) else if (node.id != id) within.add(node.id)
            }
        }
        val failed = threads.filter { it.second == null }.map { it.first }
        val unrequested = rest.filter { it !in within }
        val pending = (failed + unrequested).toHashSet()
        val (shown, deferred) = threads
            .filter { (id, nodes) -> nodes != null && id !in within }
            .partition { (_, nodes) ->
                val parent = nodes!!.firstOrNull()?.parentId?.removePrefix("t1_")
                parent == null || parent !in pending
            }

        val seen = HashSet<String>()
        val nodes = shown.flatMap { it.second!! }.filter { seen.add(if (it is Comment) "t1_${it.id}" else "more_${it.id}") }
        return MoreLoaded(nodes, failed + unrequested + deferred.map { it.first })
    }
}

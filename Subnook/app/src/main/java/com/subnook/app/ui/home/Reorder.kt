package com.subnook.app.ui.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * LazyColumn 里长按拖动排序。手势挂在整个列表上（列表本身不会移动，坐标稳定），
 * 长按后按位置找到对应 item；只有 [canDrag] 认可的 key 可以拖，也只会和这些 key 交换位置。
 * 回调每次重组时更新，所以总是拿到最新的列表。
 */
@Stable
class ReorderState(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    /** 靠近顶部 / 底部多少像素开始自动滚动（底部有悬浮导航栏，要大一些） */
    private val edgeTopPx: Float,
    private val edgeBottomPx: Float,
) {
    var canDrag: (Any) -> Boolean = { false }
    var onStart: () -> Unit = {}
    var onMove: (from: Any, to: Any) -> Unit = { _, _ -> }
    var onEnd: () -> Unit = {}

    var draggingKey by mutableStateOf<Any?>(null)
        private set
    private var delta by mutableFloatStateOf(0f)
    private var initialOffset = 0
    /** 已交换但列表还没重新布局时，记下交换前的 index，避免同一个目标被连续交换两次 */
    private var pendingFromIndex: Int? = null
    private var autoScroll: Job? = null

    private val draggingInfo: LazyListItemInfo?
        get() = draggingKey?.let { k -> listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == k } }

    /** 被拖动的 item 相对它当前布局位置的位移 */
    val draggingOffset: Float
        get() = draggingInfo?.let { initialOffset + delta - it.offset } ?: 0f

    fun isDragging(key: Any) = draggingKey == key

    internal fun start(y: Float): Boolean {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { y.toInt() in it.offset..(it.offset + it.size) && canDrag(it.key) }
            ?: return false
        draggingKey = item.key
        initialOffset = item.offset
        delta = 0f
        pendingFromIndex = null
        onStart()
        autoScroll = scope.launch { autoScrollLoop() }
        return true
    }

    internal fun drag(dy: Float) {
        delta += dy
        checkSwap()
    }

    internal fun end() {
        if (draggingKey == null) return
        autoScroll?.cancel()
        onEnd()
        draggingKey = null
        delta = 0f
    }

    private fun checkSwap() {
        val cur = draggingInfo ?: return
        pendingFromIndex?.let { if (cur.index == it) return else pendingFromIndex = null }
        val mid = cur.offset + draggingOffset + cur.size / 2f
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            it.key != cur.key && canDrag(it.key) && mid.toInt() in it.offset..(it.offset + it.size)
        } ?: return
        // 和第一个可见项交换时，LazyColumn 会以它为锚点跟着滚动，先固定住当前滚动位置
        if (cur.index == listState.firstVisibleItemIndex || target.index == listState.firstVisibleItemIndex) {
            listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }
        pendingFromIndex = cur.index
        onMove(cur.key, target.key)
    }

    private suspend fun CoroutineScope.autoScrollLoop() {
        while (isActive && draggingKey != null) {
            val info = draggingInfo
            if (info != null) {
                val layout = listState.layoutInfo
                val top = info.offset + draggingOffset
                val bottom = top + info.size
                val speed = when {
                    top < layout.viewportStartOffset + edgeTopPx -> -((layout.viewportStartOffset + edgeTopPx - top) / edgeTopPx).coerceIn(0f, 1f)
                    bottom > layout.viewportEndOffset - edgeBottomPx -> ((bottom - layout.viewportEndOffset + edgeBottomPx) / edgeBottomPx).coerceIn(0f, 1f)
                    else -> 0f
                }
                if (speed != 0f) {
                    listState.scrollBy(speed * MAX_SCROLL_PX_PER_FRAME)
                    checkSwap()
                }
            }
            withFrameNanos { }
        }
    }

    private companion object {
        const val MAX_SCROLL_PX_PER_FRAME = 24f
    }
}

/**
 * 挂在 LazyColumn 上：长按开始拖动。开始拖动后在 Initial 阶段吃掉后续事件，
 * 这样列表不会跟着滚，松手时卡片也不会被当成一次点击打开。
 */
fun Modifier.reorderable(state: ReorderState): Modifier = pointerInput(state) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val longPress = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
        if (!state.start(longPress.position.y)) return@awaitEachGesture
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == longPress.id } ?: break
                event.changes.forEach { it.consume() }
                if (!change.pressed) break
                val dy = change.position.y - change.previousPosition.y
                if (dy != 0f) state.drag(dy)
            }
        } finally {
            state.end()
        }
    }
}

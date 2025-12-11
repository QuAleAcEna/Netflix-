package com.example.cms_app.view

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// Custom pull-to-refresh mirrored from the streaming app.
private val RefreshDistance = 100.dp
private const val DragMultiplier = 1.2f

@Composable
fun PullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val state = rememberPullToRefreshState(onRefresh, isRefreshing)
    val nestedScrollConnection = remember { PullToRefreshNestedScrollConnection(state) }
    val density = LocalDensity.current
    val refreshDistancePx = with(density) { RefreshDistance.toPx() }

    Box(modifier = modifier.nestedScroll(nestedScrollConnection)) {
        content()

        val indicatorSize = 40.dp
        val indicatorAlpha = (state.position / refreshDistancePx).coerceIn(0f, 1f)

        Surface(
            modifier = Modifier
                .size(indicatorSize)
                .align(Alignment.TopCenter)
                .offset(y = with(density) { (state.position * 0.5f).toDp() } - (indicatorSize / 2))
                .alpha(indicatorAlpha),
            shape = CircleShape,
            shadowElevation = 6.dp
        ) {
            if (state.isRefreshing) {
                CircularProgressIndicator(modifier = Modifier.padding(8.dp))
            } else {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Pull to refresh",
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}

@Composable
private fun rememberPullToRefreshState(onRefresh: () -> Unit, isRefreshing: Boolean): PullToRefreshState {
    val coroutineScope = rememberCoroutineScope()
    val state = remember(onRefresh) {
        PullToRefreshState(onRefresh, coroutineScope)
    }
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            state.finishRefresh()
        }
    }
    return state
}

private class PullToRefreshState(
    private val onRefresh: () -> Unit,
    private val coroutineScope: CoroutineScope
) {
    private val _position = mutableStateOf(0f)
    val position: Float get() = _position.value

    private val maxPullDistance = RefreshDistance.value * 10.0f

    private var _isRefreshing by mutableStateOf(false)
    val isRefreshing: Boolean get() = _isRefreshing

    fun onPull(delta: Float): Float {
        if (_isRefreshing) return 0f

        val newPosition = (position + delta).coerceIn(0f, maxPullDistance)
        val consumed = newPosition - position
        _position.value = newPosition
        return consumed
    }

    fun onRelease() {
        if (_isRefreshing) return

        if (position > RefreshDistance.value * 1.5f) {
            _isRefreshing = true
            onRefresh()
        }
        animateTo(0f)
    }

    fun finishRefresh() {
        _isRefreshing = false
        animateTo(0f)
    }

    private fun animateTo(target: Float) {
        coroutineScope.launch {
            animate(initialValue = position, targetValue = target) { value, _ ->
                _position.value = value
            }
        }
    }
}

private class PullToRefreshNestedScrollConnection(private val state: PullToRefreshState) :
    NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset = when {
        source == NestedScrollSource.UserInput && available.y < 0 && state.position > 0 ->
            Offset(0f, state.onPull(available.y))
        else -> Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        when {
            source == NestedScrollSource.UserInput && available.y > 0 ->
                Offset(0f, state.onPull(available.y * DragMultiplier))
            else -> Offset.Zero
        }

    override suspend fun onPreFling(available: Velocity): Velocity {
        state.onRelease()
        return Velocity.Zero
    }
}

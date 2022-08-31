/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.constraintlayout.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.MotionLayoutScope.MotionProperties

/**
 * Implements an horizontal Carousel of n elements, driven by drag gestures and customizable
 * through a provided MotionScene.
 *
 * Usage
 * -----
 *
 * val cardsExample = arrayListOf(...)
 *
 * MotionCarousel(motionScene...) {
 *     items(cardsExample) { card ->
 *        SomeCardComponent(card)
 *     }
 * }
 *
 * or if wanting to use parameters in your components that are defined in the MotionScene:
 *
 * MotionCarousel(motionScene...) {
 *     itemsWithProperties(cardsExample) { card, properties ->
 *        SomeCardComponent(card, properties)
 *     }
 * }
 *
 * Note
 * ----
 *
 * It is recommended to encapsulate the usage of MotionCarousel:
 *
 * fun MyCarousel(content: MotionCarouselScope.() -> Unit) {
 *   val motionScene = ...
 *   MotionCarousel(motionScene..., content)
 * }
 *
 * Mechanism overview and MotionScene architecture
 * -----------------------------------------------
 *
 * We use 3 different states to represent the Carousel: "backward", "start", and "forward".
 * A horizontal swipe gesture will transition from one state to the other, e.g. a right to left swipe
 * will transition from "start" to "forward".
 *
 * We consider a scene containing several "slots" for the elements we want to display in the Carousel.
 * In an horizontal carousel, the easiest way to think of them is as an horizontal list of slots.
 *
 * The overall mechanism thus works by moving those "slots" according to the gesture, and then
 * mapping the Carousel's elements to the corresponding slots as we progress through the
 * list of elements.
 *
 * For example, let's consider using a Carousel with 3 slots [0] [1] and [2],  with [1] the
 * center slot being the only visible one during the initial state "start" (| and | representing
 * the screen borders) and [0] being outside of the screen on the left and [2] outside of the screen
 * on the right:
 *
 * start          [0] | [1] | [2]
 *
 * We can setup the backward state in the following way:
 *
 * backward           | [0] | [1] [3]
 *
 * And the forward state like:
 *
 * forward    [0] [1] | [2] |
 *
 * All three states together allowing to implement the Carousel motion we are looking for:
 *
 * backward           | [0] | [1] [3]
 * start          [0] | [1] | [2]
 * forward    [0] [1] | [2] |
 *
 * At the end of the swipe gesture, we instantly move back to the start state:
 *
 * start          [0] | [1] | [2]       -> gesture starts
 * forward    [0] [1] | [2] |           -> gesture ends
 * start          [0] | [1] | [2]       -> instant snap back to start state
 *
 * After the instant snap, we update the elements actually displayed in the slots.
 * For example, we can start with the elements {a}, {b} and {c} assigned respectively
 * to the slots [0], [1] and [2]. After the swipe the slots will be reassigned to {b}, {c} and {d}:
 *
 * start              [0]:{a} | [1]:{b} | [2]:{d}       -> gesture starts
 * forward    [0]:{a} [1]:{b} | [2]:{c} |               -> gesture ends
 * start              [0]:{a} | [1]:{b} | [2]:{c}       -> instant snap back to start state
 * start              [0]:{b} | [1]:{c} | [2]:{d}       -> repaint with reassigned elements
 *
 * In this manner, the overall effect emulate an horizontal scroll of a list of elements.
 *
 * A similar mechanism is applied the left to right gesture going through the backward state.
 *
 * Starting slot
 * -------------
 *
 * In order to operate, we need a list of slots. We retrieve them from the motionScene by adding
 * to the slotPrefix an index number. As the starting slot may not be the first one in the scene,
 * we also need to be able to specify a startIndex.
 *
 * Note that at the beginning of the Carousel, we will not populate the slots that have a lower
 * index than startIndex, and at the end of the Carousel, we will not populate the slots that have
 * a higher index than startIndex.
 *
 * @param initialSlotIndex the slot index that holds the current element
 * @param numSlots the number of slots in the scene
 * @param startState the name of the start state (default "start")
 * @param backwardState the name of the backward state (default "backward")
 * @param forwardState the name of the forward state (default "forward")
 * @param slotPrefix the prefix used for the slots widgets in the scene (default "card")
 * @param showSlots a debug flag to display the slots in the scene regardless if they are populated
 * @param content the MotionCarouselScope we use to map the elements to the slots
 */
@OptIn(ExperimentalMotionApi::class, ExperimentalMaterialApi::class)
@Composable
fun MotionCarousel(
    motionScene: MotionScene,
    initialSlotIndex: Int,
    numSlots: Int,
    startState: String = "start",
    backwardState: String = "backward",
    forwardState: String = "forward",
    slotPrefix: String = "card",
    showSlots: Boolean = false,
    content: MotionCarouselScope.() -> Unit
) {

    val SwipeStateStart = startState
    val SwipeStateForward = forwardState
    val SwipeStateBackward = backwardState

    val provider = rememberStateOfItemsProvider(content)

    var componentWidth by remember { mutableStateOf(1000f) }
    val swipeableState = rememberSwipeableState(SwipeStateStart)
    var mprogress = (swipeableState.offset.value / componentWidth)

    var state by remember {
        mutableStateOf(
            CarouselState(
                MotionCarouselDirection.FORWARD,
                0,
                0,
                false,
                false
            )
        )
    }
    var currentIndex = remember { mutableStateOf(0) }

    val anchors = if (currentIndex.value == 0) {
        mapOf(0f to SwipeStateStart, componentWidth to SwipeStateForward)
    } else if (currentIndex.value == provider.value.count() - 1) {
        mapOf(-componentWidth to SwipeStateBackward, 0f to SwipeStateStart)
    } else {
        mapOf(
            -componentWidth to SwipeStateBackward,
            0f to SwipeStateStart,
            componentWidth to SwipeStateForward
        )
    }

    val transitionName = remember {
        mutableStateOf(forwardState)
    }

    if (mprogress < 0 && state.index > 0) {
        state.direction = MotionCarouselDirection.BACKWARD
        transitionName.value = backwardState
        mprogress *= -1
    } else {
        state.direction = MotionCarouselDirection.FORWARD
        transitionName.value = forwardState
    }

    if (!swipeableState.isAnimationRunning) {
        if (state.direction == MotionCarouselDirection.FORWARD
            && swipeableState.currentValue.equals(SwipeStateForward)
        ) {
            LaunchedEffect(true) {
                if (state.index + 1 < provider.value.count()) {
                    state.index++
                    swipeableState.snapTo(SwipeStateStart)
                    state.direction = MotionCarouselDirection.FORWARD
                }
            }
        } else if (state.direction == MotionCarouselDirection.BACKWARD
            && swipeableState.currentValue.equals(SwipeStateBackward)
        ) {
            LaunchedEffect(true) {
                if (state.index > 0) {
                    state.index--
                }
                swipeableState.snapTo(SwipeStateStart)
                state.direction = MotionCarouselDirection.FORWARD
            }
        }
        currentIndex.value = state.index
    }

    MotionLayout(motionScene = motionScene,
        transitionName = transitionName.value,
        progress = mprogress,
        measureFlags = MotionLayoutMeasureFlags.FULL_MEASURE, // TODO: only apply as needed
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .swipeable(
                state = swipeableState,
                anchors = anchors,
                reverseDirection = true,
                thresholds = { _, _ -> FractionalThreshold(0.3f) },
                orientation = Orientation.Horizontal
            )
            .onSizeChanged { size ->
                componentWidth = size.width.toFloat()
            }
    ) {
        for (i in 0 until numSlots) {
            val idx = i + currentIndex.value - initialSlotIndex
            val visible = idx in 0 until provider.value.count()
            ItemHolder(i, slotPrefix, showSlots) {
                if (visible) {
                    if (provider.value.hasItemsWithProperties()) {
                        val properties = motionProperties("$slotPrefix$i")
                        provider.value.getContent(idx, properties).invoke()
                    } else {
                        provider.value.getContent(idx).invoke()
                    }
                }
            }
        }
    }
}

@Composable
fun ItemHolder(i: Int, slotPrefix: String, showSlot: Boolean, function: @Composable () -> Unit) {
    var modifier = Modifier
        .layoutId("$slotPrefix$i")

    if (showSlot) {
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .border(
                width = 2.dp,
                color = Color(0, 0, 0, 60),
                shape = RoundedCornerShape(20.dp)
            )
    }
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        function.invoke()
    }
}


private enum class MotionCarouselDirection {
    FORWARD,
    BACKWARD
}

private data class CarouselState(
    var direction: MotionCarouselDirection,
    var index: Int,
    var targetIndex: Int,
    var snapping: Boolean,
    var animating: Boolean
)

inline fun <T> MotionCarouselScope.items(
    items: List<T>,
    crossinline itemContent: @Composable (item: T) -> Unit
) = items(items.size) { index ->
    itemContent(items[index])
}

interface MotionCarouselScope {
    fun items(
        count: Int,
        itemContent: @Composable (index: Int) -> Unit
    )

    @OptIn(ExperimentalMotionApi::class)
    fun itemsWithProperties(
        count: Int,
        itemContent: @Composable (index: Int, properties: androidx.compose.runtime.State<MotionProperties>) -> Unit
    )
}

@OptIn(ExperimentalMotionApi::class)
inline fun <T> MotionCarouselScope.itemsWithProperties(
    items: List<T>,
    crossinline itemContent: @Composable (item: T, properties: androidx.compose.runtime.State<MotionProperties>) -> Unit
) = itemsWithProperties(items.size) { index, properties ->
    itemContent(items[index], properties)
}

@Composable
private fun rememberStateOfItemsProvider(
    content: MotionCarouselScope.() -> Unit
): androidx.compose.runtime.State<MotionItemsProvider> {
    val latestContent = rememberUpdatedState(content)
    return remember {
        derivedStateOf { MotionCarouselScopeImpl().apply(latestContent.value) }
    }
}

@OptIn(ExperimentalMotionApi::class)
interface MotionItemsProvider {
    fun getContent(index: Int): @Composable() () -> Unit
    fun getContent(
        index: Int,
        properties: androidx.compose.runtime.State<MotionProperties>
    ): @Composable() () -> Unit

    fun count(): Int
    fun hasItemsWithProperties(): Boolean
}

@OptIn(ExperimentalMotionApi::class)
private class MotionCarouselScopeImpl() : MotionCarouselScope, MotionItemsProvider {

    var itemsCount = 0
    var itemsProvider: @Composable ((index: Int) -> Unit)? = null
    var itemsProviderWithProperties: @Composable ((index: Int, properties: androidx.compose.runtime.State<MotionProperties>) -> Unit)? =
        null

    override fun items(
        count: Int,
        itemContent: @Composable (index: Int) -> Unit
    ) {
        itemsCount = count
        itemsProvider = itemContent
    }

    override fun itemsWithProperties(
        count: Int,
        itemContent: @Composable (index: Int, properties: androidx.compose.runtime.State<MotionProperties>) -> Unit
    ) {
        itemsCount = count
        itemsProviderWithProperties = itemContent
    }

    override fun getContent(index: Int): @Composable () -> Unit {
        return {
            println("blah getContent $index")
            itemsProvider?.invoke(index)
        }
    }

    override fun getContent(
        index: Int,
        properties: androidx.compose.runtime.State<MotionProperties>
    ): @Composable () -> Unit {
        return {
            itemsProviderWithProperties?.invoke(index, properties)
        }
    }

    override fun count(): Int {
        return itemsCount
    }

    override fun hasItemsWithProperties(): Boolean {
        return itemsProviderWithProperties != null
    }
}
package com.kanvas.fx.game

class GameStore<S, A>(
    initialState: S,
    private val reducer: (state: S, action: A) -> S,
) {
    var state: S = initialState
        private set

    private val actionQueue = ArrayDeque<A>()

    fun dispatch(action: A) {
        actionQueue.addLast(action)
    }

    fun setState(state: S) {
        this.state = state
    }

    fun reducePendingActions() {
        var current = state
        while (actionQueue.isNotEmpty()) {
            val action = actionQueue.removeFirst()
            current = reducer(current, action)
        }
        state = current
    }
}

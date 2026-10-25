package com.tinymotors.game.engine

/** Things that happened during an action; the UI turns these into animation, sound and haptics. */
sealed interface GameEvent {
    data class CarAdded(val car: Car, val slot: Int) : GameEvent
    data object BoardFull : GameEvent
    data class Moved(val car: Car, val from: Int, val to: Int) : GameEvent
    data class Swapped(val first: Car, val second: Car, val firstSlot: Int, val secondSlot: Int) : GameEvent
    data class Merged(val car: Car, val slot: Int, val fromSlot: Int, val points: Int) : GameEvent
    data class NewCarUnlocked(val level: Int, val towGranted: Boolean) : GameEvent
    data object HypercarBuilt : GameEvent
    data class Towed(val car: Car, val slot: Int) : GameEvent
    data object NoTowsLeft : GameEvent
    data class HelperTowed(val slots: List<Int>) : GameEvent
    data object Undone : GameEvent
    data object NoUndo : GameEvent
    data object Stuck : GameEvent
    data object NewGame : GameEvent
    data class TowGifted(val added: Boolean) : GameEvent
}

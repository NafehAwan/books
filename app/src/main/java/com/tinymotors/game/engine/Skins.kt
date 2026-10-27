package com.tinymotors.game.engine

/** How special a skin is; sets its price in coins. */
enum class Rarity(val price: Int) {
    Common(750),
    Rare(1000),
    Epic(1250),
    Legendary(1500),
}

/** A new look for one car level. The UI owns the pictures and names; the engine only knows ids. */
data class Skin(val id: Int, val key: String, val level: Int, val rarity: Rarity) {
    val price: Int get() = rarity.price
}

/** Car skins sold for coins. Ids are stable (they are saved); add new skins at the end. */
object Skins {
    val ALL = listOf(
        Skin(0, "racing_red", 1, Rarity.Common),
        Skin(1, "cow", 1, Rarity.Rare),
        Skin(2, "police", 2, Rarity.Common),
        Skin(3, "bunny", 2, Rarity.Rare),
        Skin(4, "taxi", 3, Rarity.Common),
        Skin(5, "kitty", 3, Rarity.Rare),
        Skin(6, "mint", 4, Rarity.Common),
        Skin(7, "dino", 4, Rarity.Rare),
        Skin(8, "stars", 5, Rarity.Common),
        Skin(9, "flower_van", 5, Rarity.Epic),
        Skin(10, "santa", 6, Rarity.Epic),
        Skin(11, "crown", 6, Rarity.Legendary),
        Skin(12, "rescue", 7, Rarity.Epic),
        Skin(13, "pumpkin", 7, Rarity.Epic),
        Skin(14, "pink_bow", 8, Rarity.Rare),
        Skin(15, "unicorn", 8, Rarity.Epic),
        Skin(16, "rally", 9, Rarity.Rare),
        Skin(17, "rainbow", 9, Rarity.Legendary),
        Skin(18, "night", 10, Rarity.Epic),
        Skin(19, "neon", 10, Rarity.Legendary),
        Skin(20, "galaxy", 11, Rarity.Legendary),
        Skin(21, "champion", 11, Rarity.Legendary),
    )

    val COUNT: Int get() = ALL.size

    /** Bits of every skin id that exists (old or damaged saves are masked with this). */
    val ALL_BITS: Int get() = (1 shl COUNT) - 1

    operator fun get(id: Int): Skin? = ALL.getOrNull(id)

    /** Keeps at most one worn skin per car level (the lowest id wins), and only owned ones. */
    fun cleanWorn(worn: Int, owned: Int): Int {
        var result = 0
        val levelsUsed = HashSet<Int>()
        for (s in ALL) {
            val bit = 1 shl s.id
            if (worn and owned and bit != 0 && levelsUsed.add(s.level)) result = result or bit
        }
        return result
    }
}

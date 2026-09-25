package com.blacklab.buddybounce.data

/**
 * The seam between the game and the two Google SDKs that would sell things in it: rewarded ads
 * and in-app purchases.
 *
 * Neither SDK is in this build. The whole game is the Android framework and the Kotlin standard
 * library, which is why the APK is tiny and the build has nothing to resolve, and adding
 * play-services-ads and the Play Billing library is a decision with real consequences - size,
 * privacy disclosures, Play policy - not a detail to slip in. So the game talks to THIS, the
 * screens ask it what it can do, and [NoStore] answers "nothing" until something real is
 * plugged in. Nothing in the UI appears while it answers that way.
 *
 * docs/ads-and-purchases.md has the wiring, step by step.
 */
interface Store {

    /** True once a rewarded ad is loaded and ready to play right now. */
    val adsReady: Boolean

    /** True once billing is connected and the coin pack's details have come back. */
    val purchasesReady: Boolean

    /**
     * The coin pack's price as the store itself formats it - "$0.25", "0,25 €", "¥40".
     *
     * Never hard-code a price in the app. Google Play converts and displays the buyer's own
     * currency, and what it charges is what the Play Console says, not what the code says.
     */
    val coinPackPrice: String

    /**
     * Play a rewarded ad. [onEarned] runs only when the SDK says the reward was genuinely
     * earned - that is the whole contract of a rewarded ad, and paying out on anything else
     * (the ad merely opening, say) is both a policy problem and an easy way to be defrauded.
     */
    fun showRewardedAd(onEarned: () -> Unit, onFailed: () -> Unit)

    /** Buy the coin pack. [onGranted] runs with the number of coins to add, once, per purchase. */
    fun buyCoinPack(onGranted: (Int) -> Unit, onFailed: () -> Unit)
}

/** The store in a build with no SDKs in it: everything off, nothing shown. */
object NoStore : Store {
    override val adsReady = false
    override val purchasesReady = false
    override val coinPackPrice = ""
    override fun showRewardedAd(onEarned: () -> Unit, onFailed: () -> Unit) = onFailed()
    override fun buyCoinPack(onGranted: (Int) -> Unit, onFailed: () -> Unit) = onFailed()
}

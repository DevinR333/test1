# Ads and in-app purchases

The game is built with the Android framework and the Kotlin standard library and nothing else.
That is why the APK is small and the build has nothing to resolve. Selling things breaks that:
both halves need a Google SDK pulled in through Gradle.

So the game talks to an interface, `data/Store.kt`, and the prize machine asks it two questions:
can you show an ad, and can you sell a coin pack. Until something real is plugged in the answer
to both is no, and the two buttons simply are not drawn. Nothing in this build changes.

Everything below is the work of turning them on.

---

## How rewarded ads actually work

**Who runs them.** Google's ad network for apps is **AdMob**. You sign up at admob.google.com
with the same Google account as your Play Console, link the two, and register the app. AdMob
creates an **ad unit** - an ID string like `ca-app-pub-…/…` - for each placement. Ours is one
rewarded ad unit.

**What a rewarded ad is.** A full-screen video the player chooses to watch, usually 15-30
seconds, with a skip after 5 seconds that forfeits the reward. The SDK tells you when the reward
is genuinely earned. You pay out **only** on that callback - not when the ad opens, not when it
closes. Paying out on anything else is both a policy problem and an easy way to be defrauded.

**Who fills them.** Advertisers bid; AdMob picks the winner. You do not choose the adverts, and
you cannot guarantee one is available - `isLoaded` is false surprisingly often in small markets.
The button has to cope with "no ad right now".

**How many, and can it be unlimited?** Nothing in the SDK stops a player watching all day.
Two things do in practice:

- **Fill.** The network runs out of inventory for one person long before they run out of
  patience. After a handful of views the next load simply fails.
- **Your own economics.** An unlimited free supply of pulls is an unlimited reason never to
  spend anything.

The usual shape is a small daily allowance. This build uses **5 a day**, in
`Tuning.AD_SPINS_PER_DAY`, counted in `Save.adsLeftToday()` and reset by the calendar day. Change
the number there; nothing else needs touching.

**What it pays.** Rewarded video is the best-paying format, and it still is not much: very
roughly one to two US cents a completed view in the US and Western Europe, a fraction of that
elsewhere. Rule of thumb - a thousand completed views is somewhere between $2 and $20. Treat ad
revenue as a rounding error until the game has real numbers of players.

---

## How in-app purchases actually work

**Who runs them.** Google Play, through the **Play Billing Library**. You never handle a card,
and you must not use any other payment method for digital goods inside the app - that is a
straight policy violation.

**Setting up the product.** In the Play Console, under Monetise → Products → In-app products,
create a product with an ID (say `coins_100`), type **Consumable** (it can be bought again and
again - as opposed to Non-consumable, which is bought once, like removing ads). You set a price
in your home currency and Play converts it for every other market.

**About 25 cents.** Play enforces a minimum price per currency, and the allowed range is shown
in the Console when you type the price in. Put 0.25 in and it will either accept it or tell you
the lowest it takes in that currency - I am not going to quote you a figure from memory when the
Console will tell you the true one in ten seconds. Whatever you set, **do not hard-code the price
in the app**: ask billing for the formatted price and display that, which is what
`Store.coinPackPrice` is for. The player in Japan sees yen, and it is Play's number, not yours.

**Google's cut** is 15% on the first $1,000,000 you earn in a year, 30% above that.

**The purchase flow**, in order: connect to billing → query the product's details → launch the
billing flow → Google charges the card → your `PurchasesUpdatedListener` fires → **grant the
coins** → **consume** the purchase so it can be bought again. If you do not consume it, the
player can never buy a second one. If you grant before you verify, you can be defrauded.

**Where to grant.** Granting on the device, as this seam does, is what almost every small game
does and it is what a determined player can cheat. The proper version verifies the purchase
token against Google's Play Developer API from a server you run. For a 25c coin pack, on-device
is a reasonable place to start.

---

## Two policy things that apply to this game specifically

**The prize machine is a loot box.** Once real money can buy a pull at a randomised prize, Play's
policy requires you to **disclose the odds** before the purchase. The odds are already in one
place, `data/PrizeRoll.kt`, so this is a screen, not an argument with the code. Several countries
go further than disclosure; check the rules for the markets you actually publish in.

**Who the app is for.** A cartoon dog game reads as child-appealing. If you declare it for
children in the Play Console, the Families policy applies: ads must come from a certified
network, must not be personalised, and paid randomised rewards are heavily restricted. If you
declare it for a general audience you still have to handle age screening for personalised ads,
and consent under GDPR in Europe. Decide this before you build the ads in - it changes which
choices are open to you.

---

## Wiring it up

**1. Dependencies.** In `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.google.android.gms:play-services-ads:23.6.0")
    implementation("com.android.billingclient:billing-ktx:7.1.1")
}
```

Use whatever versions are current when you do this. This is the moment the "no third-party
dependencies" property of the project ends; it is a real trade, not a formality.

**2. Manifest.** AdMob needs your app ID inside `<application>`:

```xml
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY"/>
```

Billing needs the permission, outside `<application>`:

```xml
<uses-permission android:name="com.android.vending.BILLING"/>
```

**3. Write a real `Store`.** One class, implementing `data/Store.kt`, holding a `RewardedAd` and
a `BillingClient`. It needs to:

- initialise the ads SDK once at startup and keep a rewarded ad loaded, reloading after each
  show and after each failure;
- report `adsReady` as "an ad object exists right now";
- connect billing, query `coins_100`, and report `purchasesReady` and `coinPackPrice` from what
  comes back;
- on a purchase: grant `Tuning.COIN_PACK`, then consume it.

**4. Hand it to the game.** In `MainActivity`, after the `Game` is constructed:

```kotlin
game.store = PlayStore(this)
```

That is the only line in the existing code that changes. The buttons appear the moment
`adsReady` and `purchasesReady` start answering true.

**5. Test before you ship.**

- Ads: use Google's **test ad unit IDs** during development. Showing a real advert to yourself
  is click fraud and it can get the AdMob account closed.
- Purchases: add yourself as a **licence tester** in the Play Console and use a closed track.
  Test purchases are free and refund themselves.

---

## What is already in this build

- `data/Store.kt` - the interface and `NoStore`, which answers no to everything.
- `Game.store` - set to `NoStore`; the one line to replace.
- `Save.adSpins`, `Save.adsLeftToday()`, `Save.grantAdSpin()`, `Save.spendAdSpin()` - banked
  free pulls and the daily allowance, persisted, reset by the calendar day.
- `Tuning.AD_SPINS_PER_DAY` (5) and `Tuning.COIN_PACK` (100).
- The two buttons on the prize machine, drawn only when the store says it can serve them. An
  ad-earned pull is spent before coins are, so watching one is never wasted.

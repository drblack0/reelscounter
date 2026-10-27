package com.mindware.reels.detection

/**
 * Every Instagram-specific string lives here; this is the only file to patch when Instagram changes.
 * ALL VALUES ARE UNVERIFIED best guesses until checked against real device dumps (M2 handoff).
 */
data class InstagramConfig(
    val packageName: String = "com.instagram.android",
    /** Bottom-bar tab ids (seen in device dumps). Present on main screens, absent under sheets like comments. */
    val mainTabIdHints: List<String> = listOf("feed_tab", "clips_tab", "search_tab", "profile_tab"),
    /** UNVERIFIED: view-id substrings (after the "id/") that suggest the Reels viewer. */
    val reelsViewerIdHints: List<String> = listOf("clips_viewer", "clips_swipe_refresh", "reel_viewer"),
    /** UNVERIFIED: view-id substrings of the element holding one reel's content/description. */
    val reelItemIdHints: List<String> = listOf("clips_item", "clips_video_container", "clips_media_component"),
    /** UNVERIFIED: content-description patterns identifying a reel node. Matched, then hashed, never stored. */
    val reelContentDescPatterns: List<Regex> = listOf(Regex("(?i)\\breel\\b")),
    /** UNVERIFIED: class names of the vertical pager container, for the structural fallback. */
    val pagerClassNames: List<String> = listOf(
        "androidx.viewpager2.widget.ViewPager2",
        "androidx.recyclerview.widget.RecyclerView",
        "androidx.viewpager.widget.ViewPager",
    ),
    /**
     * Fallback that treats a tall scrollable pager plus a "Reel" description as the Reels viewer. Off: it
     * fires on Home-feed posts that are reels. Only enable if the viewer id hints stop matching.
     */
    val structuralFallback: Boolean = false,
    /** Max tree depth walked, to bound work per event. */
    val maxDepth: Int = 30,
    val maxNodes: Int = 600,
)

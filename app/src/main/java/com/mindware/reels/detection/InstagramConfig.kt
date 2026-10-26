package com.mindware.reels.detection

/**
 * Every Instagram-specific string lives here; this is the only file to patch when Instagram changes.
 * ALL VALUES ARE UNVERIFIED best guesses until checked against real device dumps (M2 handoff).
 */
data class InstagramConfig(
    val packageName: String = "com.instagram.android",
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
    /** Max tree depth walked, to bound work per event. */
    val maxDepth: Int = 30,
    val maxNodes: Int = 600,
)

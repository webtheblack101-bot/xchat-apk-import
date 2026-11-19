package com.example

import com.example.data.model.DatingProfile
import com.example.data.model.PublicUser
import com.example.data.model.RecommendationEvent
import com.example.data.model.RecommendationPreference
import com.example.domain.recommendation.DiscoveryFilter
import com.example.domain.recommendation.DiscoveryMode
import com.example.domain.recommendation.HybridRecommendationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridRecommendationEngineTest {

    private val engine = HybridRecommendationEngine()

    private val viewerPublic = PublicUser(
        userId = "viewer_1",
        username = "alex",
        displayName = "Alex",
        bio = "Love indie music, coffee, and weekend hikes",
        interests = listOf("Music", "Coffee", "Hiking"),
        languages = listOf("English", "Spanish")
    )

    private val viewerDating = DatingProfile(
        userId = "viewer_1",
        displayName = "Alex",
        datingEnabled = true,
        datingBio = "Indie concerts and late night coffee",
        age = 26L,
        city = "Seattle",
        relationshipIntent = "dating",
        conversationStyle = "playful",
        interests = listOf("Music", "Coffee", "Hiking"),
        languages = listOf("English", "Spanish"),
        preferredAgeMin = 22L,
        preferredAgeMax = 32L,
        preferredCity = "Seattle",
        preferredIntents = listOf("dating", "serious"),
        preferredLanguages = listOf("English")
    )

    private fun candidate(
        uid: String,
        age: Long = 25L,
        city: String = "Seattle",
        intent: String = "dating",
        datingEnabled: Boolean = true,
        interests: List<String> = listOf("Music", "Coffee"),
        languages: List<String> = listOf("English"),
        prefMinAge: Long = 20L,
        prefMaxAge: Long = 35L
    ) = DatingProfile(
        userId = uid,
        displayName = "Candidate $uid",
        datingEnabled = datingEnabled,
        datingBio = "Enjoy $interests",
        age = age,
        city = city,
        relationshipIntent = intent,
        conversationStyle = "playful",
        availabilityStatus = "available_now",
        interests = interests,
        languages = languages,
        preferredAgeMin = prefMinAge,
        preferredAgeMax = prefMaxAge,
        preferredCity = city,
        preferredIntents = listOf("dating", "serious"),
        preferredLanguages = listOf("English")
    )

    @Test
    fun `cold start redistributes behavioral weight to profile signals`() {
        val c1 = candidate("u_match", interests = listOf("Music", "Coffee", "Hiking"))
        val c2 = candidate("u_diff", interests = listOf("Chess", "Baking"), city = "Austin", intent = "casual")

        val ranked = engine.rankCandidates(
            currentUserId = "viewer_1",
            viewerPublic = viewerPublic,
            viewerDating = viewerDating,
            candidateDatingProfiles = listOf(c2, c1),
            publicUsersById = emptyMap(),
            blockedUserIds = emptySet(),
            alreadyInteractedIds = emptySet(),
            preferences = null,
            recentEvents = emptyList(),
            filter = DiscoveryFilter(mode = DiscoveryMode.DATING),
            hourOfDay = 19
        )

        assertEquals(2, ranked.size)
        assertEquals("u_match", ranked.first().profile.userId)
        assertEquals(0.0, ranked.first().breakdown.behavioralAffinity, 0.0001)
        assertTrue(ranked.first().breakdown.totalScore > ranked.last().breakdown.totalScore)
    }

    @Test
    fun `hard eligibility excludes self blocked interacted underage and disabled profiles in dating mode`() {
        val self = candidate("viewer_1")
        val blocked = candidate("u_blocked")
        val interacted = candidate("u_liked")
        val underage = candidate("u_minor", age = 17L)
        val paused = candidate("u_paused", datingEnabled = false)
        val valid = candidate("u_valid")

        val ranked = engine.rankCandidates(
            currentUserId = "viewer_1",
            viewerPublic = viewerPublic,
            viewerDating = viewerDating,
            candidateDatingProfiles = listOf(self, blocked, interacted, underage, paused, valid),
            publicUsersById = emptyMap(),
            blockedUserIds = setOf("u_blocked"),
            alreadyInteractedIds = setOf("u_liked"),
            preferences = null,
            recentEvents = emptyList(),
            filter = DiscoveryFilter(mode = DiscoveryMode.DATING),
            hourOfDay = 20
        )

        assertEquals(listOf("u_valid"), ranked.map { it.profile.userId })
    }

    @Test
    fun `friendship mode allows users who disabled dating discovery`() {
        val pausedForDating = candidate("u_social", datingEnabled = false, intent = "friendship")

        val datingResults = engine.rankCandidates(
            currentUserId = "viewer_1",
            viewerPublic = viewerPublic,
            viewerDating = viewerDating,
            candidateDatingProfiles = listOf(pausedForDating),
            publicUsersById = emptyMap(),
            blockedUserIds = emptySet(),
            alreadyInteractedIds = emptySet(),
            preferences = null,
            recentEvents = emptyList(),
            filter = DiscoveryFilter(mode = DiscoveryMode.DATING),
            hourOfDay = 15
        )
        assertTrue(datingResults.isEmpty())

        val friendResults = engine.rankCandidates(
            currentUserId = "viewer_1",
            viewerPublic = viewerPublic,
            viewerDating = viewerDating,
            candidateDatingProfiles = listOf(pausedForDating),
            publicUsersById = emptyMap(),
            blockedUserIds = emptySet(),
            alreadyInteractedIds = emptySet(),
            preferences = null,
            recentEvents = emptyList(),
            filter = DiscoveryFilter(mode = DiscoveryMode.FRIENDSHIP),
            hourOfDay = 15
        )
        assertEquals(listOf("u_social"), friendResults.map { it.profile.userId })
    }

    @Test
    fun `cyclical time context treats 23h and 00h as proximate across midnight`() {
        val casualCand = candidate("u_night", intent = "casual")
        val events = listOf(
            RecommendationEvent(
                userId = "viewer_1",
                targetUserId = "other",
                eventType = "like",
                intentContext = "casual",
                hourOfDay = 23L
            )
        )

        val midnightRanked = engine.rankCandidates(
            currentUserId = "viewer_1",
            viewerPublic = viewerPublic,
            viewerDating = viewerDating,
            candidateDatingProfiles = listOf(casualCand),
            publicUsersById = emptyMap(),
            blockedUserIds = emptySet(),
            alreadyInteractedIds = emptySet(),
            preferences = null,
            recentEvents = events,
            filter = DiscoveryFilter(mode = DiscoveryMode.DATING),
            hourOfDay = 0
        )

        val noonRanked = engine.rankCandidates(
            currentUserId = "viewer_1",
            viewerPublic = viewerPublic,
            viewerDating = viewerDating,
            candidateDatingProfiles = listOf(casualCand),
            publicUsersById = emptyMap(),
            blockedUserIds = emptySet(),
            alreadyInteractedIds = emptySet(),
            preferences = null,
            recentEvents = events,
            filter = DiscoveryFilter(mode = DiscoveryMode.DATING),
            hourOfDay = 12
        )

        assertTrue(
            midnightRanked.first().breakdown.timeContext > noonRanked.first().breakdown.timeContext
        )
    }

    @Test
    fun `mutual compatibility rewards bidirectional preference match`() {
        // Viewer is 26. Candidate A wants ages 22-30; Candidate B wants ages 40-50
        val mutualFit = candidate("u_mutual", prefMinAge = 22L, prefMaxAge = 30L)
        val oneSided = candidate("u_onesided", prefMinAge = 40L, prefMaxAge = 50L)

        val ranked = engine.rankCandidates(
            currentUserId = "viewer_1",
            viewerPublic = viewerPublic,
            viewerDating = viewerDating,
            candidateDatingProfiles = listOf(oneSided, mutualFit),
            publicUsersById = emptyMap(),
            blockedUserIds = emptySet(),
            alreadyInteractedIds = emptySet(),
            preferences = null,
            recentEvents = emptyList(),
            filter = DiscoveryFilter(mode = DiscoveryMode.DATING),
            hourOfDay = 18
        )

        assertEquals("u_mutual", ranked.first().profile.userId)
        assertTrue(ranked[0].breakdown.mutualCompatibility > ranked[1].breakdown.mutualCompatibility)
    }

    @Test
    fun `behavioral feedback adjusts ranking unless personalization disabled`() {
        val jazzFan = candidate("u_jazz", interests = listOf("Jazz", "Art"))
        val techFan = candidate("u_tech", interests = listOf("Tech", "Gaming"))
        val prefs = RecommendationPreference(
            userId = "viewer_1",
            personalizedEnabled = true,
            boostedTags = listOf("jazz", "art"),
            suppressedTags = listOf("tech", "gaming")
        )

        val personalized = engine.rankCandidates(
            currentUserId = "viewer_1",
            viewerPublic = viewerPublic,
            viewerDating = viewerDating,
            candidateDatingProfiles = listOf(techFan, jazzFan),
            publicUsersById = emptyMap(),
            blockedUserIds = emptySet(),
            alreadyInteractedIds = emptySet(),
            preferences = prefs,
            recentEvents = emptyList(),
            filter = DiscoveryFilter(mode = DiscoveryMode.DATING),
            hourOfDay = 14
        )
        assertEquals("u_jazz", personalized.first().profile.userId)
        assertTrue(personalized[0].breakdown.behavioralAffinity > personalized[1].breakdown.behavioralAffinity)

        val disabled = engine.rankCandidates(
            currentUserId = "viewer_1",
            viewerPublic = viewerPublic,
            viewerDating = viewerDating,
            candidateDatingProfiles = listOf(techFan, jazzFan),
            publicUsersById = emptyMap(),
            blockedUserIds = emptySet(),
            alreadyInteractedIds = emptySet(),
            preferences = prefs.copy(personalizedEnabled = false),
            recentEvents = emptyList(),
            filter = DiscoveryFilter(mode = DiscoveryMode.DATING),
            hourOfDay = 14
        )
        assertFalse(disabled.any { it.breakdown.behavioralAffinity > 0.0 })
    }
}

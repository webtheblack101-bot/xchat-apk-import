package com.example.domain.recommendation

import com.example.data.model.DatingProfile
import com.example.data.model.PublicUser
import com.example.data.model.RankedCandidate
import com.example.data.model.RecommendationEvent
import com.example.data.model.RecommendationPreference
import com.example.data.model.RecommendationScoreBreakdown
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class ScoringWeights(
    val profileSimilarity: Double = 0.25,
    val mutualCompatibility: Double = 0.20,
    val behavioralAffinity: Double = 0.20,
    val intentAndStyle: Double = 0.15,
    val timeContext: Double = 0.10,
    val availability: Double = 0.05,
    val diversityFreshness: Double = 0.05
)

enum class DiscoveryMode(val key: String, val label: String) {
    DATING("dating", "Dating"),
    FRIENDSHIP("friendship", "Friends & Casual")
}

data class DiscoveryFilter(
    val mode: DiscoveryMode = DiscoveryMode.DATING,
    val minAge: Int = 18,
    val maxAge: Int = 60,
    val cityFilter: String = "",
    val selectedIntent: String = "all",
    val selectedLanguage: String = "all"
)

class HybridRecommendationEngine(
    private val defaultWeights: ScoringWeights = ScoringWeights()
) {
    companion object {
        const val MODEL_VERSION = "hybrid-v1.0"
    }

    fun rankCandidates(
        currentUserId: String,
        viewerPublic: PublicUser?,
        viewerDating: DatingProfile?,
        candidateDatingProfiles: List<DatingProfile>,
        publicUsersById: Map<String, PublicUser>,
        blockedUserIds: Set<String>,
        alreadyInteractedIds: Set<String>,
        preferences: RecommendationPreference?,
        recentEvents: List<RecommendationEvent>,
        filter: DiscoveryFilter,
        hourOfDay: Int
    ): List<RankedCandidate> {
        val viewerInterests = (viewerDating?.interests?.takeIf { it.isNotEmpty() }
            ?: viewerPublic?.interests.orEmpty()).map { it.lowercase() }.toSet()
        val viewerLanguages = (viewerDating?.languages?.takeIf { it.isNotEmpty() }
            ?: viewerPublic?.languages.orEmpty()).map { it.lowercase() }.toSet()
        val viewerBioTokens = tokenize(
            listOfNotNull(viewerPublic?.bio, viewerDating?.datingBio).joinToString(" ")
        )
        val viewerAge = viewerDating?.age?.toInt() ?: 25
        val viewerCity = viewerDating?.city?.trim()?.lowercase().orEmpty()
        val viewerIntent = if (filter.selectedIntent != "all") {
            filter.selectedIntent
        } else {
            viewerDating?.relationshipIntent ?: "dating"
        }
        val viewerStyle = viewerDating?.conversationStyle ?: "playful"

        // Stage A: Hard Eligibility & Safety Candidate Filtering
        val eligible = candidateDatingProfiles.filter { candidate ->
            val uid = candidate.userId
            if (uid.isBlank() || uid == currentUserId) return@filter false
            if (uid in blockedUserIds) return@filter false
            if (uid in alreadyInteractedIds) return@filter false

            val pub = publicUsersById[uid]
            if (pub != null && pub.accountStatus != "active") return@filter false

            if (filter.mode == DiscoveryMode.DATING) {
                if (!candidate.datingEnabled) return@filter false
                if (candidate.age < 18L) return@filter false
                if (candidate.age < filter.minAge || candidate.age > filter.maxAge) return@filter false
            }

            if (filter.cityFilter.isNotBlank() &&
                !candidate.city.contains(filter.cityFilter.trim(), ignoreCase = true)
            ) {
                return@filter false
            }

            if (filter.selectedIntent != "all" &&
                !candidate.relationshipIntent.equals(filter.selectedIntent, ignoreCase = true)
            ) {
                return@filter false
            }

            if (filter.selectedLanguage != "all" &&
                candidate.languages.none { it.equals(filter.selectedLanguage, ignoreCase = true) }
            ) {
                return@filter false
            }

            true
        }

        if (eligible.isEmpty()) return emptyList()

        // Determine cold-start vs behavioral adaptation
        val personalizedEnabled = preferences?.personalizedEnabled ?: true
        val boostedTags = if (personalizedEnabled) {
            preferences?.boostedTags?.map { it.lowercase() }?.toSet().orEmpty()
        } else emptySet()
        val suppressedTags = if (personalizedEnabled) {
            preferences?.suppressedTags?.map { it.lowercase() }?.toSet().orEmpty()
        } else emptySet()

        val hasBehavioralSignals = personalizedEnabled &&
            (boostedTags.isNotEmpty() || suppressedTags.isNotEmpty() || recentEvents.isNotEmpty())

        val weights = if (hasBehavioralSignals) {
            defaultWeights
        } else {
            // Cold-start strategy: redistribute behavioral weight into explicit profile & intent signals
            defaultWeights.copy(
                profileSimilarity = defaultWeights.profileSimilarity + 0.12,
                mutualCompatibility = defaultWeights.mutualCompatibility + 0.04,
                behavioralAffinity = 0.0,
                intentAndStyle = defaultWeights.intentAndStyle + 0.04
            )
        }

        // Learn time-of-day intent affinity from historical positive events
        val hourAngle = 2.0 * PI * (hourOfDay.coerceIn(0, 23)) / 24.0
        val currentSin = sin(hourAngle)
        val currentCos = cos(hourAngle)

        val positiveHourIntents = recentEvents
            .filter { it.eventType in setOf("like", "superlike", "match", "chat_started", "more_like_this") }
            .map { event ->
                val eAngle = 2.0 * PI * (event.hourOfDay.coerceIn(0L, 23L)) / 24.0
                val proximity = ((currentSin * sin(eAngle) + currentCos * cos(eAngle)) + 1.0) / 2.0
                event.intentContext to proximity
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, vals) -> vals.average() }

        // Stage B: Multi-Factor Hybrid Scoring
        val rawScored = eligible.map { candidate ->
            val pub = publicUsersById[candidate.userId]
            val candInterests = (candidate.interests.ifEmpty { pub?.interests.orEmpty() })
                .map { it.lowercase() }.toSet()
            val candLanguages = (candidate.languages.ifEmpty { pub?.languages.orEmpty() })
                .map { it.lowercase() }.toSet()
            val candBioTokens = tokenize(
                listOfNotNull(pub?.bio, candidate.datingBio).joinToString(" ")
            )

            val interestJaccard = jaccard(viewerInterests, candInterests)
            val bioOverlap = jaccard(viewerBioTokens, candBioTokens)
            val langOverlap = jaccard(viewerLanguages, candLanguages)
            val profileSim = (0.55 * interestJaccard + 0.25 * langOverlap + 0.20 * bioOverlap)
                .coerceIn(0.0, 1.0)

            // Bidirectional Mutual Compatibility
            val aLikesB = directionalFit(
                viewerAge = viewerAge,
                viewerCity = viewerCity,
                viewerIntent = viewerIntent,
                viewerLanguages = viewerLanguages,
                targetAge = candidate.age.toInt(),
                targetCity = candidate.city.trim().lowercase(),
                targetIntent = candidate.relationshipIntent,
                targetLanguages = candLanguages,
                prefMinAge = filter.minAge,
                prefMaxAge = filter.maxAge,
                prefCity = viewerDating?.preferredCity?.trim()?.lowercase().orEmpty(),
                prefIntents = viewerDating?.preferredIntents.orEmpty(),
                prefLanguages = viewerDating?.preferredLanguages?.map { it.lowercase() }?.toSet().orEmpty(),
                profileSimilarity = profileSim
            )
            val bLikesA = directionalFit(
                viewerAge = candidate.age.toInt(),
                viewerCity = candidate.city.trim().lowercase(),
                viewerIntent = candidate.relationshipIntent,
                viewerLanguages = candLanguages,
                targetAge = viewerAge,
                targetCity = viewerCity,
                targetIntent = viewerIntent,
                targetLanguages = viewerLanguages,
                prefMinAge = candidate.preferredAgeMin.toInt(),
                prefMaxAge = candidate.preferredAgeMax.toInt(),
                prefCity = candidate.preferredCity.trim().lowercase(),
                prefIntents = candidate.preferredIntents,
                prefLanguages = candidate.preferredLanguages.map { it.lowercase() }.toSet(),
                profileSimilarity = profileSim
            )
            val mutualComp = sqrt((aLikesB * bLikesA).coerceIn(0.0, 1.0))

            // Behavioral affinity from boosted/suppressed tags
            val behavioral = if (!hasBehavioralSignals) {
                0.0
            } else {
                val boostHits = candInterests.count { it in boostedTags }
                val suppressHits = candInterests.count { it in suppressedTags }
                val raw = 0.5 + (boostHits * 0.22) - (suppressHits * 0.30)
                raw.coerceIn(0.0, 1.0)
            }

            // Intent and conversation style alignment
            val intentMatch = when {
                candidate.relationshipIntent == viewerIntent -> 1.0
                candidate.relationshipIntent in viewerDating?.preferredIntents.orEmpty() -> 0.78
                filter.mode == DiscoveryMode.FRIENDSHIP &&
                    candidate.relationshipIntent in setOf("friendship", "casual", "social") -> 0.85
                else -> 0.35
            }
            val styleMatch = if (candidate.conversationStyle == viewerStyle) 1.0 else 0.6
            val intentAndStyle = (0.7 * intentMatch + 0.3 * styleMatch).coerceIn(0.0, 1.0)

            // Cyclical time-of-day context (never penalizes if no context history)
            val learnedContextFit = positiveHourIntents[candidate.relationshipIntent] ?: 0.5
            val eveningBoost = if (hourOfDay >= 20 || hourOfDay <= 2) {
                if (candidate.relationshipIntent in setOf("casual", "social", "dating")) 0.8 else 0.55
            } else {
                0.65
            }
            val timeContext = (0.55 * learnedContextFit + 0.45 * eveningBoost).coerceIn(0.0, 1.0)

            // Availability status (neutral 0.65 when offline/unspecified so users aren't penalized)
            val availability = when (candidate.availabilityStatus) {
                "available_now" -> 1.0
                "tonight" -> 0.85
                "weekend" -> 0.72
                else -> 0.65
            }

            val freshness = if (pub?.lastActiveAt != null) 0.85 else 0.60

            val weightedScore = (
                weights.profileSimilarity * profileSim +
                weights.mutualCompatibility * mutualComp +
                weights.behavioralAffinity * behavioral +
                weights.intentAndStyle * intentAndStyle +
                weights.timeContext * timeContext +
                weights.availability * availability +
                weights.diversityFreshness * freshness
            ).coerceIn(0.0, 1.0)

            val tags = buildList {
                if (interestJaccard >= 0.25) add("Shared interests")
                if (mutualComp >= 0.72) add("High mutual fit")
                if (viewerCity.isNotBlank() && candidate.city.equals(viewerCity, ignoreCase = true)) add("Same city")
                if (candidate.availabilityStatus == "available_now") add("Available now")
                if (langOverlap > 0.0) add("Common language")
            }.take(3)

            val breakdown = RecommendationScoreBreakdown(
                profileSimilarity = profileSim,
                mutualCompatibility = mutualComp,
                behavioralAffinity = behavioral,
                intentAndStyle = intentAndStyle,
                timeContext = timeContext,
                availability = availability,
                diversityFreshness = freshness,
                totalScore = weightedScore,
                explainabilityTags = tags
            )

            RankedCandidate(
                profile = candidate,
                publicUser = pub,
                breakdown = breakdown,
                position = 0
            )
        }.sortedByDescending { it.breakdown.totalScore }

        // Diversity-aware greedy re-ranking so consecutive profiles vary in intent/city
        val remaining = rawScored.toMutableList()
        val finalOrder = mutableListOf<RankedCandidate>()
        while (remaining.isNotEmpty()) {
            val prev = finalOrder.lastOrNull()
            val pickIdx = if (prev == null || remaining.size == 1) {
                0
            } else {
                var bestIdx = 0
                var bestAdjusted = Double.NEGATIVE_INFINITY
                val window = minOf(5, remaining.size)
                for (i in 0 until window) {
                    val cand = remaining[i]
                    val sameIntentPenalty =
                        if (cand.profile.relationshipIntent == prev.profile.relationshipIntent) 0.04 else 0.0
                    val sameCityPenalty =
                        if (cand.profile.city.equals(prev.profile.city, ignoreCase = true)) 0.02 else 0.0
                    val adjusted = cand.breakdown.totalScore - sameIntentPenalty - sameCityPenalty
                    if (adjusted > bestAdjusted) {
                        bestAdjusted = adjusted
                        bestIdx = i
                    }
                }
                bestIdx
            }
            val chosen = remaining.removeAt(pickIdx)
            finalOrder += chosen.copy(position = finalOrder.size)
        }

        return finalOrder
    }

    private fun directionalFit(
        viewerAge: Int,
        viewerCity: String,
        viewerIntent: String,
        viewerLanguages: Set<String>,
        targetAge: Int,
        targetCity: String,
        targetIntent: String,
        targetLanguages: Set<String>,
        prefMinAge: Int,
        prefMaxAge: Int,
        prefCity: String,
        prefIntents: List<String>,
        prefLanguages: Set<String>,
        profileSimilarity: Double
    ): Double {
        val ageFit = if (targetAge in prefMinAge..prefMaxAge) 1.0 else 0.35
        val cityFit = when {
            prefCity.isBlank() && viewerCity.isBlank() -> 0.75
            prefCity.isNotBlank() && targetCity == prefCity -> 1.0
            viewerCity.isNotBlank() && targetCity == viewerCity -> 0.95
            else -> 0.55
        }
        val intentFit = when {
            prefIntents.isEmpty() -> if (targetIntent == viewerIntent) 1.0 else 0.7
            targetIntent in prefIntents -> 1.0
            else -> 0.4
        }
        val langFit = if (prefLanguages.isEmpty()) {
            if (viewerLanguages.intersect(targetLanguages).isNotEmpty()) 1.0 else 0.5
        } else {
            if (prefLanguages.intersect(targetLanguages).isNotEmpty()) 1.0 else 0.3
        }
        return (0.28 * ageFit + 0.22 * cityFit + 0.25 * intentFit + 0.15 * langFit + 0.10 * profileSimilarity)
            .coerceIn(0.0, 1.0)
    }

    private fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val intersection = a.intersect(b).size.toDouble()
        val union = a.union(b).size.toDouble()
        return if (union == 0.0) 0.0 else intersection / union
    }

    private fun tokenize(text: String): Set<String> =
        text.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 }
            .toSet()
}

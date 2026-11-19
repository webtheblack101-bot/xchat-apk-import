package com.example.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.DatingProfile
import com.example.data.model.MatchRecord
import com.example.data.model.PublicUser
import com.example.data.model.RankedCandidate
import com.example.data.model.RecommendationPreference
import com.example.data.model.UiState
import com.example.data.model.userFriendlyMessage
import com.example.data.repository.DiscoveryRepository
import com.example.data.repository.UserRepository
import com.example.domain.recommendation.DiscoveryFilter
import com.example.domain.recommendation.DiscoveryMode
import com.example.domain.recommendation.HybridRecommendationEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class MatchCelebration(
    val targetName: String,
    val conversationId: String
)

data class DiscoverBundle(
    val rankedCandidates: List<RankedCandidate>,
    val activeMatches: List<MatchRecord>,
    val publicUsersById: Map<String, PublicUser>,
    val preferences: RecommendationPreference?,
    val viewerDatingProfile: DatingProfile?,
    val filter: DiscoveryFilter
)

class DiscoverViewModel(
    private val currentUserId: String,
    private val userRepository: UserRepository,
    private val discoveryRepository: DiscoveryRepository,
    private val engine: HybridRecommendationEngine = HybridRecommendationEngine()
) : ViewModel() {

    private val _filter = MutableStateFlow(DiscoveryFilter())
    val filter: StateFlow<DiscoveryFilter> = _filter.asStateFlow()

    private val _matchCelebration = MutableStateFlow<MatchCelebration?>(null)
    val matchCelebration: StateFlow<MatchCelebration?> = _matchCelebration.asStateFlow()

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    private val loggedImpressions = mutableSetOf<String>()

    val discoverState: StateFlow<UiState<DiscoverBundle>> = combine(
        userRepository.observePublicUser(currentUserId),
        userRepository.observeDatingProfile(currentUserId),
        discoveryRepository.observeEligibleDatingProfiles(),
        userRepository.observeActiveUsers(60L),
        userRepository.observeBlockedByMe(currentUserId),
        userRepository.observeBlockedMe(currentUserId),
        discoveryRepository.observeMyLikes(currentUserId),
        discoveryRepository.observeMyMatches(currentUserId),
        discoveryRepository.observeRecommendationPreferences(currentUserId),
        discoveryRepository.observeRecommendationEvents(currentUserId),
        _filter
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val viewerPub = values[0] as PublicUser?
        val viewerDating = values[1] as DatingProfile?
        @Suppress("UNCHECKED_CAST")
        val datingProfiles = values[2] as List<DatingProfile>
        @Suppress("UNCHECKED_CAST")
        val activeUsers = values[3] as List<PublicUser>
        @Suppress("UNCHECKED_CAST")
        val blockedByMe = values[4] as List<com.example.data.model.BlockRecord>
        @Suppress("UNCHECKED_CAST")
        val blockedMe = values[5] as List<com.example.data.model.BlockRecord>
        @Suppress("UNCHECKED_CAST")
        val myLikes = values[6] as List<com.example.data.model.LikeInteraction>
        @Suppress("UNCHECKED_CAST")
        val myMatches = values[7] as List<MatchRecord>
        val prefs = values[8] as RecommendationPreference?
        @Suppress("UNCHECKED_CAST")
        val events = values[9] as List<com.example.data.model.RecommendationEvent>
        val activeFilter = values[10] as DiscoveryFilter

        val usersById = activeUsers.associateBy { it.userId }
        val blockedIds = (blockedByMe.map { it.blockedId } + blockedMe.map { it.blockerId }).toSet()
        val interactedIds = myLikes.map { it.toUserId }.toSet()

        // In FRIENDSHIP mode, allow social-only users who don't have a dating profile to also appear
        val combinedPool = if (activeFilter.mode == DiscoveryMode.FRIENDSHIP) {
            val datingByUid = datingProfiles.associateBy { it.userId }
            val synthesized = activeUsers
                .filter { it.userId != currentUserId && it.userId !in datingByUid }
                .map { pub ->
                    DatingProfile(
                        userId = pub.userId,
                        displayName = pub.displayName,
                        datingEnabled = false,
                        datingBio = pub.bio,
                        age = 24L,
                        city = "Community",
                        relationshipIntent = "friendship",
                        conversationStyle = "playful",
                        availabilityStatus = if (pub.onlineVisible) "available_now" else "offline",
                        interests = pub.interests,
                        languages = pub.languages,
                        photos = listOfNotNull(pub.avatarData)
                    )
                }
            datingProfiles + synthesized
        } else {
            datingProfiles
        }

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val ranked = engine.rankCandidates(
            currentUserId = currentUserId,
            viewerPublic = viewerPub,
            viewerDating = viewerDating,
            candidateDatingProfiles = combinedPool,
            publicUsersById = usersById,
            blockedUserIds = blockedIds,
            alreadyInteractedIds = interactedIds,
            preferences = prefs,
            recentEvents = events,
            filter = activeFilter,
            hourOfDay = hour
        )

        DiscoverBundle(
            rankedCandidates = ranked,
            activeMatches = myMatches.filter { it.status == "active" && it.userAId !in blockedIds && it.userBId !in blockedIds },
            publicUsersById = usersById,
            preferences = prefs,
            viewerDatingProfile = viewerDating,
            filter = activeFilter
        )
    }
        .asUiState("DiscoverVM")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    fun updateFilter(transform: (DiscoveryFilter) -> DiscoveryFilter) {
        _filter.value = transform(_filter.value)
    }

    fun dismissMatchCelebration() {
        _matchCelebration.value = null
    }

    fun clearFeedback() {
        _feedback.value = null
    }

    fun recordImpressionIfNew(candidate: RankedCandidate) {
        val uid = candidate.profile.userId
        if (uid in loggedImpressions) return
        loggedImpressions += uid
        viewModelScope.launch {
            discoveryRepository.logRecommendationEvent(
                targetUserId = uid,
                eventType = "profile_impression",
                intentContext = candidate.profile.relationshipIntent,
                position = candidate.position
            )
        }
    }

    fun interact(me: PublicUser, candidate: RankedCandidate, interactionType: String) {
        viewModelScope.launch {
            val res = discoveryRepository.submitSwipeInteraction(
                me = me,
                targetProfile = candidate.profile,
                interactionType = interactionType,
                intentContext = candidate.profile.relationshipIntent,
                position = candidate.position
            )
            res.onSuccess { outcome ->
                if (outcome.matched && outcome.conversationId != null) {
                    _matchCelebration.value = MatchCelebration(
                        targetName = candidate.profile.displayName,
                        conversationId = outcome.conversationId
                    )
                }
            }.onFailure {
                _feedback.value = it.userFriendlyMessage("Interaction could not be saved.")
            }
        }
    }

    fun applyFeedback(
        prefs: RecommendationPreference?,
        candidate: RankedCandidate,
        moreLikeThis: Boolean
    ) {
        viewModelScope.launch {
            val res = discoveryRepository.applyFeedbackTags(
                currentPrefs = prefs,
                targetProfile = candidate.profile,
                moreLikeThis = moreLikeThis,
                position = candidate.position
            )
            _feedback.value = res.fold(
                onSuccess = {
                    if (moreLikeThis) "Prioritizing similar profiles."
                    else "Showing fewer profiles like this."
                },
                onFailure = { it.userFriendlyMessage("Could not update recommendation weights.") }
            )
        }
    }

    fun resetPersonalization(enabled: Boolean) {
        viewModelScope.launch {
            val res = discoveryRepository.setPersonalizedEnabled(enabled)
            _feedback.value = res.fold(
                onSuccess = {
                    if (enabled) "Recommendation tuning reset." else "Personalized ranking disabled."
                },
                onFailure = { it.userFriendlyMessage("Failed to update recommendation settings.") }
            )
        }
    }

    fun unmatch(matchId: String) {
        viewModelScope.launch {
            val res = discoveryRepository.unmatch(matchId)
            _feedback.value = res.fold(
                onSuccess = { "Unmatched." },
                onFailure = { it.userFriendlyMessage("Could not unmatch.") }
            )
        }
    }
}

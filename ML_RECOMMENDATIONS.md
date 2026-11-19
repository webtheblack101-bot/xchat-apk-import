# X Chat Hybrid Recommendation System (`hybrid-v1.0`)

`HybridRecommendationEngine` blends content-based filtering, bidirectional preference compatibility, collaborative/behavioral feedback, cyclical temporal context, and diversity re-ranking.

## Stage A: Hard Eligibility & Safety Filtering
Before any scoring runs, candidates are excluded if:
- `userId == currentUserId`, `accountStatus != "active"`, or present in bidirectional `blocks`.
- Already interacted with (`likes` contains `like`, `superlike`, or `pass`).
- In **Dating** mode: `!datingEnabled`, `age < 18`, or outside `minAge..maxAge`.
- City, intent, or language filters do not match when explicitly specified.

## Stage B: Multi-Factor Scoring
For active behavioral histories, total score `S in [0, 1]` is:
- `0.25 * profileSimilarity` (Jaccard overlap on interests, bio tokens, and languages)
- `0.20 * mutualCompatibility` (geometric mean `sqrt(fit(A->B) * fit(B->A))` across age range, city, intent, and language preferences)
- `0.20 * behavioralAffinity` (`More like this` boosted tags minus `Fewer like this` suppressed tags)
- `0.15 * intentAndStyle` (relationship intention & conversation style alignment)
- `0.10 * timeContext` (cyclical sine/cosine hour-of-day proximity to past positive events, treating `23:00` and `00:00` as adjacent)
- `0.05 * availability` (`available_now`, `tonight`, `weekend`, neutral `0.65` fallback)
- `0.05 * diversityFreshness` (recent activity recency signal)

### Cold-Start Handling
When a user has no behavioral feedback or historical events (`!hasBehavioralSignals`) or disables personalization (`personalizedEnabled == false`), the `0.20` behavioral weight is redistributed cleanly to `profileSimilarity (+0.12)`, `mutualCompatibility (+0.04)`, and `intentAndStyle (+0.04)` so new users immediately get relevant recommendations.

### Diversity-Aware Re-Ranking & Explainability
After initial ranking, a greedy lookahead window (`window = 5`) penalizes back-to-back identical `relationshipIntent` (`-0.04`) and identical `city` (`-0.02`) to prevent repetitive feeds. Each candidate emits up to 3 explainability tags (`Shared interests`, `High mutual fit`, `Same city`, `Available now`, `Common language`).

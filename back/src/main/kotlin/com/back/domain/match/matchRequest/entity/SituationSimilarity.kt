package com.back.domain.match.matchRequest.entity

import java.util.EnumSet

object SituationSimilarity {
    private val WORK_OVERLOAD_GROUP: Set<Situation> = EnumSet.of(Situation.NIGHT_WORK, Situation.MEETING_BOMB)
    private val INTERPERSONAL_GROUP: Set<Situation> =
        EnumSet.of(Situation.BOSS_BLAME, Situation.OFFICE_POLITICS_FATIGUE, Situation.OFFICE_ROMANCE_LEAK)
    private val CAREER_CHANGE_GROUP: Set<Situation> = EnumSet.of(Situation.JOB_CHANGE_URGE, Situation.SALARY_NEGOTIATION)

    private val ALL_GROUPS: List<Set<Situation>> = listOf(WORK_OVERLOAD_GROUP, INTERPERSONAL_GROUP, CAREER_CHANGE_GROUP)

    @JvmStatic
    fun getSimilarGroup(situation: Situation): Set<Situation> =
        ALL_GROUPS.firstOrNull { it.contains(situation) }
            ?.let { EnumSet.copyOf(it) }
            ?: EnumSet.of(situation)
}

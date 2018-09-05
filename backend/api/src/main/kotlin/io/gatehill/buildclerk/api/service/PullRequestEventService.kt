package io.gatehill.buildclerk.api.service

import io.gatehill.buildclerk.api.Recorded
import io.gatehill.buildclerk.api.model.PullRequestCreatedOrUpdatedEvent
import io.gatehill.buildclerk.api.model.PullRequestMergedEvent

/**
 * Processes pull request events.
 */
interface PullRequestEventService : Recorded {
    fun checkPullRequest(event: PullRequestMergedEvent)
    fun describePullRequest(event: PullRequestMergedEvent): String
    fun findPullRequestByMergeCommit(commit: String): PullRequestMergedEvent?
    fun fetchLastPullRequest(branchName: String? = null): PullRequestMergedEvent?
    fun fetchPullRequests(branchName: String? = null): List<PullRequestMergedEvent>
    fun checkCreatedOrUpdatedPullRequest(event: PullRequestCreatedOrUpdatedEvent)
}

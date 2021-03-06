package io.gatehill.buildclerk.api.dao

import io.gatehill.buildclerk.api.Recorded
import io.gatehill.buildclerk.api.model.pr.PullRequestMergedEvent

interface PullRequestEventDao: Recorded {
    fun record(mergedEvent: PullRequestMergedEvent)
    fun findByMergeCommit(commit: String): PullRequestMergedEvent?
    fun fetchLast(branchName: String? = null): PullRequestMergedEvent?
    fun list(branchName: String? = null): List<PullRequestMergedEvent>
}

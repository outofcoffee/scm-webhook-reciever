package io.gatehill.buildclerk.service.scm.bitbucket

import io.gatehill.buildclerk.api.config.Settings
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject

/**
 * Common Bitbucket operations.
 *
 * @author Pete Cornish {@literal <outofcoffee@gmail.com>}
 */
class BitbucketOperationsService @Inject constructor(
    private val apiClientBuilder: BitbucketApiClientBuilder
) {
    private val logger: Logger = LogManager.getLogger(BitbucketOperationsService::class.java)

    fun listRestrictions(branchName: String, apiClient: BitbucketApi): List<BranchRestriction> {
        val call = apiClient.listBranchRestrictions(
            username = Settings.Bitbucket.repoUsername,
            repoSlug = Settings.Bitbucket.repoSlug
        )
        val response = call.execute()

        return if (response.isSuccessful) {
            response.body().values
        } else {
            throw RuntimeException("Unsuccessfully listed branch restriction [branch name: $branchName, request URL: ${call.request().url()}, response code: ${response.code()}] response body: ${response.errorBody().string()}")
        }
    }

    fun ensureRestriction(
        apiClient: BitbucketApi,
        branchName: String,
        restrictions: List<BranchRestriction>,
        kind: String
    ) {
        val existing = restrictions.firstOrNull { restriction ->
            restriction.kind == kind && restriction.pattern == branchName
        }

        existing?.let { updateRestriction(apiClient, branchName, kind, existing.id!!) }
            ?: addRestriction(apiClient, branchName, kind)
    }

    private fun addRestriction(
        apiClient: BitbucketApi,
        branchName: String,
        kind: String
    ) {
        logger.debug("Adding branch restriction: [branch name: $branchName, kind: $kind]")

        val call: Call<Void> = apiClient.createBranchRestriction(
            username = Settings.Bitbucket.repoUsername,
            repoSlug = Settings.Bitbucket.repoSlug,
            branchRestriction = BranchRestriction(
                kind = kind,
                pattern = branchName
            )
        )

        try {
            val response = call.execute()
            if (response.isSuccessful) {
                handleRestrictionResponse(branchName, kind, call, response)
            } else {
                throw RuntimeException("Unsuccessfully added branch restriction [branch name: $branchName, kind: $kind, request URL: ${call.request().url()}, response code: ${response.code()}] response body: ${response.errorBody().string()}")
            }

        } catch (e: Exception) {
            throw RuntimeException("Error adding branch restriction: [branch name: $branchName, kind: $kind]", e)
        }
    }

    private fun updateRestriction(
        apiClient: BitbucketApi,
        branchName: String,
        kind: String,
        branchRestrictionId: Int
    ) {
        logger.debug("Updating branch restriction: [branch name: $branchName, kind: $kind, id: $branchRestrictionId]")

        val call: Call<Void> = apiClient.updateBranchRestriction(
            username = Settings.Bitbucket.repoUsername,
            repoSlug = Settings.Bitbucket.repoSlug,
            id = branchRestrictionId,
            branchRestriction = BranchRestriction(
                kind = kind,
                pattern = branchName
            )
        )

        try {
            val response = call.execute()
            if (response.isSuccessful) {
                handleRestrictionResponse(branchName, kind, call, response)
            } else {
                throw RuntimeException("Unsuccessfully updated branch restriction [branch name: $branchName, kind: $kind, id: $branchRestrictionId, request URL: ${call.request().url()}, response code: ${response.code()}] response body: ${response.errorBody().string()}")
            }

        } catch (e: Exception) {
            throw RuntimeException(
                "Error updating branch restriction: [branch name: $branchName, kind: $kind, id: $branchRestrictionId]",
                e
            )
        }
    }

    /**
     * Process the response to adding a restriction.
     */
    private fun handleRestrictionResponse(
        branchName: String,
        kind: String,
        call: Call<Void>,
        response: Response<Void>
    ) {
        when (response.code()) {
            200, 201 -> {
                logger.info("Set branch restriction: [branch name: $branchName, kind: $kind]")
            }
            else -> {
                throw RuntimeException("Unsuccessfully set branch restriction [branch name: $branchName, kind: $kind, request URL: ${call.request().url()}, response code: ${response.code()}] response body: ${response.errorBody().string()}")
            }
        }
    }

    fun listComments(pullRequestId: Int) : List<PullRequestComment> {
        logger.debug("Checking for comments on PR $pullRequestId")

        val apiClient = apiClientBuilder.buildApiClient()

        val call: Call<BitbucketList<PullRequestComment>> = apiClient.listPullRequestComments(
            username = Settings.Bitbucket.repoUsername,
            repoSlug = Settings.Bitbucket.repoSlug,
            pullRequestId = pullRequestId
        )

        try {
            val response = call.execute()
            if (response.isSuccessful) {
                return response.body().values
            } else {
                throw RuntimeException("Unsuccessfully listed PR comments [PR: $pullRequestId, request URL: ${call.request().url()}, response code: ${response.code()}] response body: ${response.errorBody().string()}")
            }

        } catch (e: Exception) {
            throw RuntimeException("Error listing PR comments [PR: $pullRequestId]", e)
        }
    }

    fun createComment(pullRequestId: Int, comment: String) {
        logger.debug("Adding comment to PR $pullRequestId: $comment")

        val apiClient = apiClientBuilder.buildApiClient()

        val call: Call<BitbucketList<PullRequestComment>> = apiClient.createPullRequestComment(
            username = Settings.Bitbucket.repoUsername,
            repoSlug = Settings.Bitbucket.repoSlug,
            pullRequestId = pullRequestId,
            comment = PullRequestComment(
                content = CommentContent(
                    raw = comment
                )
            )
        )

        try {
            val response = call.execute()
            if (response.isSuccessful) {
                logger.debug("Added PR comment [PR: $pullRequestId, comment: $comment")
            } else {
                throw RuntimeException("Unsuccessfully added PR comment [PR: $pullRequestId, comment: $comment, request URL: ${call.request().url()}, response code: ${response.code()}] response body: ${response.errorBody().string()}")
            }

        } catch (e: Exception) {
            throw RuntimeException("Error adding PR comment [PR: $pullRequestId, comment: $comment]", e)
        }
    }
}

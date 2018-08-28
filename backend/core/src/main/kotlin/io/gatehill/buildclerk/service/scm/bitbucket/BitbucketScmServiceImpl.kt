package io.gatehill.buildclerk.service.scm.bitbucket

import io.gatehill.buildclerk.config.Settings
import io.gatehill.buildclerk.service.CommandExecutorService
import io.gatehill.buildclerk.service.scm.GitScmServiceImpl
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import javax.inject.Inject

/**
 * Bitbucket SCM implementation.
 *
 * @author Pete Cornish {@literal <outofcoffee@gmail.com>}
 */
class BitbucketScmServiceImpl @Inject constructor(
    repositorySettings: Settings.Repository,
    commandExecutorService: CommandExecutorService,
    private val apiClientBuilder: BitbucketApiClientBuilder,
    private val bitbucketOperationsService: BitbucketOperationsService
) : GitScmServiceImpl(
    repositorySettings,
    commandExecutorService
) {
    private val logger: Logger = LogManager.getLogger(BitbucketScmServiceImpl::class.java)

    override fun lockBranch(branchName: String) {
        logger.debug("Locking branch: $branchName")
        try {
            val apiClient = apiClientBuilder.buildApiClient()

            val restrictions = bitbucketOperationsService.listRestrictions(branchName, apiClient)
            bitbucketOperationsService.ensureRestriction(apiClient, branchName, restrictions, "push")
            bitbucketOperationsService.ensureRestriction(apiClient, branchName, restrictions, "restrict_merges")

        } catch (e: Exception) {
            throw RuntimeException("Error locking branch: $branchName", e)
        }
    }
}
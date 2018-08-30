package io.gatehill.buildclerk.service.scm

import com.jcraft.jsch.Session
import io.gatehill.buildclerk.api.config.Settings
import io.gatehill.buildclerk.model.scm.CommitUserInfo
import io.gatehill.buildclerk.model.scm.ScmUser
import io.gatehill.buildclerk.service.CommandExecutorService
import org.apache.logging.log4j.LogManager
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.TransportCommand
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.RepositoryState
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.JschConfigSessionFactory
import org.eclipse.jgit.transport.OpenSshConfig.Host
import org.eclipse.jgit.transport.SshTransport
import org.eclipse.jgit.transport.TransportHttp
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.util.FileUtils
import java.util.Objects.nonNull
import javax.inject.Inject


/**
 * Git SCM repository.
 *
 * @author Pete Cornish {@literal <outofcoffee@gmail.com>}
 */
open class GitScmServiceImpl @Inject constructor(
    private val repositorySettings: Settings.Repository,
    private val commandExecutorService: CommandExecutorService
) : ScmService {

    private val logger = LogManager.getLogger(ScmService::class.java)

    override fun fetchUserInfoForCommit(commit: String): CommitUserInfo = withGit {
        logger.debug("Fetching user info for commit $commit")

        fetchRefs()
        val resolvedCommit = repository.resolve(commit)
        val revCommit = repository.parseCommit(resolvedCommit)

        CommitUserInfo(
            author = ScmUser(
                userName = revCommit.authorIdent.name,
                email = revCommit.authorIdent.emailAddress
            ),
            committer = ScmUser(
                userName = revCommit.committerIdent.name,
                email = revCommit.committerIdent.emailAddress
            )
        )
    }

    override fun revertCommit(commit: String, branchName: String): Unit = withGit {
        logger.info("Reverting commit $commit in branch $branchName")

        fetchCheckout(branchName)
        revertCommit(commit)

        if (repository.repositoryState != RepositoryState.BARE) {
            throw IllegalStateException("Repository state is: ${repository.repositoryState}")
        }

        if (repositorySettings.pushChanges) {
            logger.info("Pushing changes to remote")
            push()
                .configureTransport()
                .call()
        } else {
            logger.info("Skipped pushing changes to remote")
        }
    }

    override fun lockBranch(branchName: String) {
        throw NotImplementedError("locking branches is not implemented")
    }

    private fun Git.fetchCheckout(branchName: String) {
        logger.debug("Performing checkout of branch $branchName")

        fetchRefs()
        checkout()
            .setName(branchName)
            .setForce(true)
            .call()

        if (repository.repositoryState != RepositoryState.BARE) {
            throw IllegalStateException("Repository state is not bare. Current state is: ${repository.repositoryState}")
        }
    }

    private fun Git.fetchRefs() {
        fetch()
            .configureTransport()
            .setRemoveDeletedRefs(true)
            .call()
    }

    private fun Git.revertCommit(commit: String) {
        val resolvedCommit = repository.resolve(commit)
        val revCommit = repository.parseCommit(resolvedCommit)

        if (revCommit.parentCount == 1) {
            revert().include(revCommit).call()
        } else {
            // jgit doesn't support reverting commits with multiple parents (e.g. merge commits)
            commandExecutorService.exec(
                command = "git revert $commit --mainline 1",
                workingDir = repositorySettings.localDir
            )
        }
    }

    private fun <T> withRepo(block: Repository.() -> T): T {
        val repository: Repository = if (isLocalRepoPresent()) {
            logger.debug("Existing local bare repository found at: ${repositorySettings.localDir}")
            FileRepositoryBuilder()
                .setGitDir(repositorySettings.localDir)
                .build()
        } else {
            logger.debug("Local bare repository not found at: ${repositorySettings.localDir} - attempting clone")
            clone().repository
        }

        return repository.use(block)
    }

    /**
     * Creates a `Git` for the configured repository, on which the `block` is
     * executed, then closes it.
     */
    private fun <T> withGit(block: Git.() -> T): T = withRepo {
        Git(this).use(block)
    }

    /**
     * Clone a repository.
     */
    internal fun clone(): Git {
        logger.info("Cloning remote repository: ${repositorySettings.remoteUrl}")
        val startMs = System.currentTimeMillis()

        if (repositorySettings.localDir.exists()) {
            FileUtils.delete(repositorySettings.localDir, FileUtils.RECURSIVE)
        }

        val cloneCommand = Git.cloneRepository()
            .configureTransport()
            .setBare(true)
            .setDirectory(repositorySettings.localDir)
            .setURI(repositorySettings.remoteUrl)

        val git = cloneCommand.call()
        val duration = (System.currentTimeMillis() - startMs) / 1000f
        logger.info("Cloned remote repository to: ${repositorySettings.localDir} [took $duration seconds]")
        return git
    }

    /**
     * Applies the transport configuration to any commands requiring interaction
     * with a remote repository.
     */
    private fun <T : TransportCommand<*, *>> T.configureTransport(): T = apply {
        setTransportConfigCallback { transport ->
            val sshSessionFactory = object : JschConfigSessionFactory() {
                override fun configure(host: Host, session: Session) {
                    // caters for SSH + password, i.e. when not using public key authentication
                    repositorySettings.password?.let { session.setPassword(it) }

                    // equivalent to StrictHostKeyChecking=no in ~/.ssh/config
                    repositorySettings.strictHostKeyChecking?.let { overrideValue ->
                        val strictHostKeyChecking = if (overrideValue) "yes" else "no"
                        logger.debug("Set SSH strict host key checking to '$strictHostKeyChecking'")
                        session.setConfig("StrictHostKeyChecking", strictHostKeyChecking)
                    }
                }
            }

            when (transport) {
                is SshTransport -> {
                    logger.debug("Configuring repository transport for SSH")
                    transport.sshSessionFactory = sshSessionFactory
                }
                is TransportHttp -> {
                    if (isUserNameAndPasswordConfigured()) {
                        logger.debug("Configuring repository transport with HTTP credentials")
                        transport.credentialsProvider = UsernamePasswordCredentialsProvider(
                            repositorySettings.userName,
                            repositorySettings.password
                        )
                    } else {
                        logger.debug("No HTTP credentials configured for repository transport - assuming unauthenticated")
                    }
                }
            }
        }
    }

    private fun isLocalRepoPresent() =
        repositorySettings.localDir.exists() &&
                repositorySettings.localDir.isDirectory &&
                repositorySettings.localDir.list().isNotEmpty()

    private fun isUserNameAndPasswordConfigured() =
        nonNull(repositorySettings.userName) && nonNull(repositorySettings.password)
}

package org.nemwiz.jiracommitmessage.services

import com.intellij.notification.BrowseNotificationAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vcs.changes.Change
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import org.nemwiz.jiracommitmessage.configuration.PluginSettingsState
import org.nemwiz.jiracommitmessage.provider.PluginNotifier
import java.util.*
import java.util.regex.Pattern

private val LOG = logger<JiraCommitMessagePlugin>()

private const val DEFAULT_REGEX_FOR_JIRA_PROJECT_ISSUES = "([A-Z]+[_-][0-9]+)"
private const val CONVENTIONAL_COMMITS_REGEX = "(feat|fix|build|ci|chore|docs|perf|refactor|style|test)*"

@Service(Service.Level.PROJECT)
class JiraCommitMessagePlugin(private val project: Project) : Disposable {

    fun getCommitMessageFromBranchName(branchName: String?): String {

        LOG.info("Extracting JIRA project key from branch name -> $branchName")

        if (branchName == null) {
            return ""
        }

        val jiraProjectKeys = PluginSettingsState.instance.state.jiraProjectKeys
        val isAutoDetectProjectKey = PluginSettingsState.instance.state.isAutoDetectJiraProjectKey

        LOG.info("JIRA Commit message plugin settings: jiraProjectKeys -> $jiraProjectKeys, isAutoDetectProjectKey -> $isAutoDetectProjectKey")

        if (!isAutoDetectProjectKey && jiraProjectKeys.isEmpty()) {
            val notifier = PluginNotifier()
            notifier.showWarning(
                project,
                "Missing configuration",
                "Please configure your JIRA project key under Settings > Tools > JIRA Id Commit Message",
                BrowseNotificationAction(
                    "Visit documentation",
                    "https://github.com/nemwiz/jira-commit-message-intellij-plugin"
                )
            )

            return ""
        }

        val isConventionalCommit = PluginSettingsState.instance.state.isConventionalCommit

        val jiraIssue = extractJiraIssueFromBranch(branchName)
        val conventionalCommitType = extractConventionalCommitType(isConventionalCommit, branchName)

        LOG.info("Extracted JIRA issue -> $jiraIssue, conventionalCommitType -> $conventionalCommitType")

        val selectedMessageWrapper = PluginSettingsState.instance.state.messageWrapperType
        val selectedPrefixType = PluginSettingsState.instance.state.messagePrefixType
        val selectedInfixType = PluginSettingsState.instance.state.messageInfixType

        LOG.info("JIRA Commit message wrapper settings: selectedMessageWrapper -> $selectedMessageWrapper, selectedPrefixType -> $selectedPrefixType, selectedInfixType -> $selectedInfixType")

        return CommitMessageBuilder(jiraIssue)
            .withWrapper(selectedMessageWrapper)
            .withInfix(selectedInfixType)
            .withConventionalCommit(conventionalCommitType)
            .withPrefix(selectedPrefixType)
            .getCommitMessage()
    }

    fun extractBranchNameFromChanges(changes: MutableCollection<Change>, repositoryManager: GitRepositoryManager): String? {
        val repositoriesChanged: MutableList<GitRepository> = mutableListOf()
        for (change in changes) {
            change.virtualFile?.let {
                repositoryManager.getRepositoryForFileQuick(it)?.let { repository -> repositoriesChanged.add(repository) }
            }
        }
        val changesInRepositories = repositoriesChanged.groupingBy { it }. eachCount().toList().sortedBy { (_, value) -> value }
        var branchName = repositoryManager.repositories[0].currentBranch?.name
        for (changesInRepository in changesInRepositories) {
            val currentBranch = changesInRepository.first.currentBranch ?: continue
            val jiraIssue = extractJiraIssueFromBranch(currentBranch.name) ?: continue
            branchName = jiraIssue
        }
        return branchName
    }


    private fun extractJiraIssueFromBranch(
        branchName: String,
        isAutoDetectProjectKey: Boolean = PluginSettingsState.instance.state.isAutoDetectJiraProjectKey,
        jiraProjectKeys: List<String> = PluginSettingsState.instance.state.jiraProjectKeys
    ): String? {

        var jiraIssue: String? = null

        if (isAutoDetectProjectKey) {
            val pattern = Pattern.compile(DEFAULT_REGEX_FOR_JIRA_PROJECT_ISSUES).toRegex()
            val matchedJiraIssue = pattern.find(branchName)
            jiraIssue = matchedJiraIssue?.value
        } else {
            for (projectKey in jiraProjectKeys) {
                val pattern = createPatternRegex(projectKey)
                var matchedJiraIssue = pattern.find(branchName)

                if (matchedJiraIssue == null) {
                    val lowercasePattern = createPatternRegex(projectKey.lowercase())
                    matchedJiraIssue = lowercasePattern.find(branchName)
                }

                if (matchedJiraIssue != null) {
                    jiraIssue = matchedJiraIssue.value.uppercase()
                    break
                }
            }
        }

        return jiraIssue
    }

    private fun createPatternRegex(projectKey: String) =
        Pattern.compile(String.format(Locale.US, "%s+[_-][0-9]+", projectKey)).toRegex()

    private fun extractConventionalCommitType(isConventionalCommit: Boolean, branchName: String): String? {
        if (isConventionalCommit) {
            val pattern = Pattern.compile(CONVENTIONAL_COMMITS_REGEX).toRegex()
            val matchedConventionalType = pattern.find(branchName)
            return matchedConventionalType?.value
        }
        return null
    }

    override fun dispose() {
    }
}

package org.nemwiz.jiracommitmessage.services

import com.intellij.notification.BrowseNotificationAction
import com.intellij.openapi.project.Project
import git4idea.GitLocalBranch
import git4idea.GitUtil.getRepositoryManager
import org.nemwiz.jiracommitmessage.configuration.MessageWrapperType
import org.nemwiz.jiracommitmessage.configuration.PluginSettingsState
import org.nemwiz.jiracommitmessage.provider.PluginNotifier
import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern

class MyProjectService(private val project: Project) {

    fun getTaskIdFromBranchName(): String? {
        val jiraProjectPrefix = PluginSettingsState.instance.state.jiraProjectPrefix

        if (jiraProjectPrefix == "") {
            val notifier = PluginNotifier()
            notifier.showWarning(
                project,
                "Missing configuration",
                "",
                "Please configure your JIRA project prefix under Settings > Tools > JIRA Id Commit Message",
                listOf(
                    BrowseNotificationAction(
                        "Visit documentation",
                        "https://github.com/nemwiz/jira-commit-message-intellij-plugin"
                    )
                )
            )

            return ""
        }

        val selectedMessageWrapper = PluginSettingsState.instance.state.messageWrapperType
        val repositoryManager = getRepositoryManager(project)
        val branch = repositoryManager.repositories[0].currentBranch
        val jiraPrefixRegex = branch?.let { matchBranchNameThroughRegex(jiraProjectPrefix, it) }

        return jiraPrefixRegex?.let {
            return if (jiraPrefixRegex.find()) {
                return createCommitMessage(selectedMessageWrapper, jiraPrefixRegex)
            } else {
                ""
            }
        }
    }

    private fun createCommitMessage(wrapperType: String, jiraPrefixRegex: Matcher): String {
        return if (wrapperType == MessageWrapperType.NO_WRAPPER.type) {
            jiraPrefixRegex.group(0)
        } else {
            String.format(
                Locale.US,
                "%s%s%s",
                wrapperType.substring(0, 1),
                jiraPrefixRegex.group(0),
                wrapperType.substring(1, 2)
            )
        }
    }

    private fun matchBranchNameThroughRegex(valueToMatch: String?, branch: GitLocalBranch): Matcher? {
        val pattern = Pattern.compile(String.format(Locale.US, "%s+-[0-9]+", valueToMatch))
        return pattern.matcher(branch.name)
    }
}

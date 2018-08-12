package com.gatehill.buildbouncer.service.notify.slack

import com.gatehill.buildbouncer.api.model.Analysis
import com.gatehill.buildbouncer.api.model.NotificationMessage
import com.gatehill.buildbouncer.api.model.UpdatedNotificationMessage
import com.gatehill.buildbouncer.api.model.action.PendingAction
import com.gatehill.buildbouncer.model.slack.SlackAttachmentAction
import com.gatehill.buildbouncer.model.slack.SlackMessage
import com.gatehill.buildbouncer.model.slack.SlackMessageAttachment
import com.gatehill.buildbouncer.service.notify.StdoutNotificationServiceImpl
import javax.inject.Inject

/**
 * Sends Slack notifications and actions.
 *
 * @author Pete Cornish {@literal <outofcoffee@gmail.com>}
 */
class SlackNotificationServiceImpl @Inject constructor(
        private val slackOperationsService: SlackOperationsService
) : StdoutNotificationServiceImpl() {

    override fun notify(channelName: String, message: String, color: String) {
        super.notify(channelName, message, color)

        val content = SlackMessage(
                channel = channelName,
                attachments = listOf(
                        SlackMessageAttachment(
                                text = message,
                                color = color
                        )
                )
        )

        slackOperationsService.sendMessage(content)
    }

    override fun notify(channelName: String, analysis: Analysis, color: String) {
        super.notify(channelName, analysis.toString(), color)

        if (analysis.actionSet.actions.isEmpty()) {
            return
        }

        val content = SlackMessage(
                text = analysis.describeEvents(),
                channel = channelName,
                attachments = analysis.actionSet.actions.map { action ->
                    buildMessageAttachment(analysis.actionSet.id, action, color)
                }
        )

        slackOperationsService.sendMessage(content)
    }

    override fun updateMessage(updatedMessage: UpdatedNotificationMessage) {
        val slackMessage = convertToSlackMessage(updatedMessage)
        slackOperationsService.updateMessage(slackMessage)
    }

    private fun buildMessageAttachment(
            actionSetId: String,
            action: PendingAction,
            color: String
    ) = SlackMessageAttachment(
            fallback = "Do you want to ${action.describe()}?",
            title = "Do you want to ${action.describe()}?",
            callbackId = actionSetId,
            color = color,
            attachmentType = "default",
            actions = listOf(
                    SlackAttachmentAction(
                            type = "button",
                            name = action.name,
                            text = action.title,
                            value = action.name,
                            style = "danger"
                    ),
                    SlackAttachmentAction(
                            type = "button",
                            name = action.name,
                            text = "No",
                            value = "no"
                    )
            )
    )

    private fun convertToSlackMessage(updatedMessage: NotificationMessage) = SlackMessage(
            channel = updatedMessage.channel,
            text = updatedMessage.text,
            attachments = updatedMessage.attachments?.map { attachment ->
                SlackMessageAttachment(
                        text = attachment.text,
                        color = attachment.color,
                        title = attachment.title,
                        fallback = attachment.fallback
                )
            },
            ts = when (updatedMessage) {
                is UpdatedNotificationMessage -> updatedMessage.messageId
                else -> null
            }
    )
}
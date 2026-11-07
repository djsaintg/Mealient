package gq.kirmanak.mealient.ui.invite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gq.kirmanak.mealient.datasource.MealieDataSource
import gq.kirmanak.mealient.datasource.ServerUrlProvider
import gq.kirmanak.mealient.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class InviteScreenState(
    val link: String? = null,
    val email: String = "",
    val isBusy: Boolean = false,
    val message: InviteMessage? = null,
    /** Set when the server couldn't send the email: the screen opens the phone's own mail app with this. */
    val openMailApp: MailDraft? = null,
)

internal data class MailDraft(val to: String, val link: String)

internal sealed interface InviteMessage {
    data object LinkFailed : InviteMessage
    data class SentByServer(val to: String) : InviteMessage
    data class OpeningMailApp(val to: String) : InviteMessage
    data object BadEmail : InviteMessage
}

internal sealed interface InviteEvent {
    data object CreateLink : InviteEvent
    data class EmailChanged(val email: String) : InviteEvent
    data object SendEmail : InviteEvent
    data object MailAppOpened : InviteEvent
    data object MessageShown : InviteEvent
}

/**
 * Invites someone into the signed-in user's Mealie household. The link is a one-time Mealie invite
 * (<server>/register?token=...). Email goes through the Mealie server first; when its mail server won't deliver to
 * that address (a private server that only knows its own domain, for example), the phone's mail app takes over.
 */
@HiltViewModel
internal class InviteViewModel @Inject constructor(
    private val dataSource: MealieDataSource,
    private val serverUrlProvider: ServerUrlProvider,
    private val logger: Logger,
) : ViewModel() {

    private val _state = MutableStateFlow(InviteScreenState())
    val state: StateFlow<InviteScreenState> = _state.asStateFlow()

    private var token: String? = null

    fun onEvent(event: InviteEvent) {
        logger.v { "onEvent() called with: event = $event" }
        when (event) {
            InviteEvent.CreateLink -> createLink()
            is InviteEvent.EmailChanged -> _state.update { it.copy(email = event.email.trim()) }
            InviteEvent.SendEmail -> sendEmail()
            InviteEvent.MailAppOpened -> _state.update { it.copy(openMailApp = null) }
            InviteEvent.MessageShown -> _state.update { it.copy(message = null) }
        }
    }

    private fun createLink() {
        if (_state.value.isBusy) return
        _state.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val link = try {
                val newToken = dataSource.createInvite(uses = 1).token
                val base = serverUrlProvider.getUrl()?.trimEnd('/') ?: error("no server URL")
                token = newToken
                "$base/register?token=$newToken"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(e) { "createLink failed" }
                null
            }
            _state.update {
                it.copy(
                    isBusy = false,
                    link = link ?: it.link,
                    message = if (link == null) InviteMessage.LinkFailed else null,
                )
            }
        }
    }

    private fun sendEmail() {
        val current = _state.value
        val link = current.link ?: return
        val inviteToken = token ?: return
        val to = current.email
        if (current.isBusy) return
        if (!EMAIL.matches(to)) {
            _state.update { it.copy(message = InviteMessage.BadEmail) }
            return
        }
        _state.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val sent = try {
                dataSource.emailInvite(email = to, token = inviteToken).success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w(e) { "emailInvite failed, falling back to the mail app" }
                false
            }
            _state.update {
                if (sent) {
                    it.copy(isBusy = false, message = InviteMessage.SentByServer(to))
                } else {
                    it.copy(
                        isBusy = false,
                        message = InviteMessage.OpeningMailApp(to),
                        openMailApp = MailDraft(to = to, link = link),
                    )
                }
            }
        }
    }

    private companion object {
        val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}

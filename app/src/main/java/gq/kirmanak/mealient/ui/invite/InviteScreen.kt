package gq.kirmanak.mealient.ui.invite

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import com.ramcosta.composedestinations.annotation.Destination
import gq.kirmanak.mealient.R
import gq.kirmanak.mealient.ui.AppTheme
import gq.kirmanak.mealient.ui.Dimens
import gq.kirmanak.mealient.ui.components.BaseScreenState
import gq.kirmanak.mealient.ui.components.BaseScreenWithNavigation
import gq.kirmanak.mealient.ui.components.previewBaseScreenState
import gq.kirmanak.mealient.ui.preview.ColorSchemePreview

@Destination
@Composable
internal fun InviteScreen(
    baseScreenState: BaseScreenState,
    viewModel: InviteViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    InviteScreen(
        baseScreenState = baseScreenState,
        state = state,
        onEvent = viewModel::onEvent,
    )
}

@Composable
internal fun InviteScreen(
    baseScreenState: BaseScreenState,
    state: InviteScreenState,
    onEvent: (InviteEvent) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val subject = stringResource(R.string.fragment_invite_mail_subject)
    val body = state.link?.let { stringResource(R.string.fragment_invite_mail_body, it) }

    state.message?.let { message ->
        val text = when (message) {
            InviteMessage.LinkFailed -> stringResource(R.string.fragment_invite_link_failed)
            InviteMessage.BadEmail -> stringResource(R.string.fragment_invite_bad_email)
            is InviteMessage.SentByServer -> stringResource(R.string.fragment_invite_sent_by_server, message.to)
            is InviteMessage.OpeningMailApp -> stringResource(R.string.fragment_invite_opening_mail_app, message.to)
        }
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(text)
            onEvent(InviteEvent.MessageShown)
        }
    }

    state.openMailApp?.let { draft ->
        val mailBody = stringResource(R.string.fragment_invite_mail_body, draft.link)
        LaunchedEffect(draft) {
            openMailApp(context, draft.to, subject, mailBody)
            onEvent(InviteEvent.MailAppOpened)
        }
    }

    BaseScreenWithNavigation(
        baseScreenState = baseScreenState,
        snackbarHostState = snackbarHostState,
    ) { modifier ->
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(Dimens.Medium),
            verticalArrangement = Arrangement.spacedBy(Dimens.Medium),
        ) {
            Text(
                text = stringResource(R.string.fragment_invite_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.fragment_invite_explanation),
                style = MaterialTheme.typography.bodyMedium,
            )

            Button(
                onClick = { onEvent(InviteEvent.CreateLink) },
                enabled = !state.isBusy,
            ) {
                Text(
                    text = stringResource(
                        if (state.link == null) R.string.fragment_invite_create else R.string.fragment_invite_create_another,
                    ),
                )
            }

            if (state.link != null && body != null) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    SelectionContainer {
                        Text(
                            modifier = Modifier.padding(Dimens.Medium),
                            text = state.link,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Small)) {
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(state.link)) }) {
                        Text(text = stringResource(R.string.fragment_invite_copy))
                    }
                    OutlinedButton(onClick = { shareLink(context, subject, body) }) {
                        Text(text = stringResource(R.string.fragment_invite_share))
                    }
                }

                Text(
                    text = stringResource(R.string.fragment_invite_email_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = state.email,
                    onValueChange = { onEvent(InviteEvent.EmailChanged(it)) },
                    label = { Text(text = stringResource(R.string.fragment_invite_email_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onEvent(InviteEvent.SendEmail) }),
                )
                Button(
                    onClick = { onEvent(InviteEvent.SendEmail) },
                    enabled = !state.isBusy && state.email.isNotBlank(),
                ) {
                    Text(text = stringResource(R.string.fragment_invite_send))
                }
            }
        }
    }
}

private fun shareLink(context: Context, subject: String, body: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    context.startActivity(Intent.createChooser(send, null))
}

private fun openMailApp(context: Context, to: String, subject: String, body: String) {
    val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    try {
        context.startActivity(mail)
    } catch (e: ActivityNotFoundException) {
        shareLink(context, subject, body)
    }
}

@ColorSchemePreview
@Composable
private fun InviteScreenPreview() {
    AppTheme {
        InviteScreen(
            baseScreenState = previewBaseScreenState(),
            state = InviteScreenState(link = "https://mealie.example.com/register?token=abc", email = "jane@example.com"),
            onEvent = {},
        )
    }
}

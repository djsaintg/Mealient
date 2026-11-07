package gq.kirmanak.mealient.datasource.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** POST /api/households/invitations: an invite into the caller's own group and household. */
@Serializable
data class CreateInviteRequest(
    @SerialName("uses") val uses: Int,
)

@Serializable
data class InviteTokenResponse(
    @SerialName("token") val token: String,
    @SerialName("usesLeft") val usesLeft: Int = 1,
)

/** POST /api/households/invitations/email: Mealie mails the link itself, through its own SMTP settings. */
@Serializable
data class EmailInviteRequest(
    @SerialName("email") val email: String,
    @SerialName("token") val token: String,
)

@Serializable
data class EmailInviteResponse(
    @SerialName("success") val success: Boolean,
    @SerialName("error") val error: String? = null,
)

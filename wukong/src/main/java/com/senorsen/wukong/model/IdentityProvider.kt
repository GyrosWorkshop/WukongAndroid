package com.senorsen.wukong.model

import android.content.Context
import android.net.Uri
import com.senorsen.wukong.R;
import net.openid.appauth.AuthorizationServiceConfiguration


fun getProvider(context: Context) : IdentityProvider {
    return IdentityProvider(
            context.resources.getString(R.string.b2c_discovery_uri),
            context.resources.getString(R.string.b2c_tenant),
            context.resources.getString(R.string.b2c_client_id),
            Uri.parse(context.resources.getString(R.string.b2c_redirect_uri)),
            context.resources.getString(R.string.b2c_signupin_policy),
            context.resources.getString(R.string.b2c_scope_string)
    )
}

data class IdentityProvider(
        val discoveryEndpoint: String = "",
        val tenant: String = "",
        val clientId: String = "",
        val redirectUri: Uri? = null,
        val policy: String = "",
        val scope: String = ""
    ) {
    public fun getDeiscoveryEndpoint(): Uri {
        return Uri.parse(String.format(discoveryEndpoint, tenant, policy))
    }
}


package net.tpky.demoapp;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.tapkey.mobile.concurrent.Promise;
import com.tapkey.mobile.concurrent.PromiseSource;

import net.openid.appauth.AuthorizationService;
import net.openid.appauth.AuthorizationServiceConfiguration;
import net.openid.appauth.TokenRequest;
import net.openid.appauth.TokenResponse;

import java.util.HashMap;
import java.util.HashSet;

class TapkeyTokenExchangeManager {

    private static final String TAG = TapkeyTokenExchangeManager.class.getSimpleName();

    private Context context;

    TapkeyTokenExchangeManager(Context context) {
        this.context = context;
    }

    private Promise<AuthorizationServiceConfiguration> getAuthorizationServiceConfiguration() {
        PromiseSource<AuthorizationServiceConfiguration> res = new PromiseSource<>();
        Uri.Builder builder = new Uri.Builder();
        Uri authorizationServer = builder.scheme(context.getString(R.string.tapkey_authorization_server_scheme))
                .encodedAuthority(context.getString(R.string.tapkey_authorization_server_authority))
                .build();

        AuthorizationServiceConfiguration.fetchFromIssuer(authorizationServer, (serviceConfiguration, ex) -> {
            if (ex != null) {
                Log.e(TAG, "failed to fetch authorization server configuration");
                res.setException(ex);
            }
            res.setResult(serviceConfiguration);
        });
        return res.getPromise();
    }

    /**
     * Exchanges the given external token for a Tapkey access token using the Token Exchange grant
     * type. The token exchange is based on functionality provided by AppAuth.Android.
     *
     * @param externalToken the JWT token to be exchanged for a Tapkey access token.
     * @return a Tapkey access token.
     */
    Promise<TokenResponse> exchangeToken(String externalToken) {
        return this.getAuthorizationServiceConfiguration()
                .continueAsyncOnUi(serviceConfiguration -> {
                    PromiseSource<TokenResponse> res = new PromiseSource<>();
                    AuthorizationService authService = new AuthorizationService(context);
                    TokenRequest.Builder tokenRequestBuilder =
                            new TokenRequest.Builder(serviceConfiguration, context.getString(R.string.tapkey_oauth_client_id))
                                    .setCodeVerifier(null)
                                    .setGrantType("http://tapkey.net/oauth/token_exchange")
                                    .setScopes(new HashSet<String>() {{
                                        add("register:mobiles");
                                        add("read:user");
                                        add("handle:keys");
                                    }})
                                    .setAdditionalParameters(new HashMap<String, String>() {{
                                        put("provider", context.getString(R.string.tapkey_identity_provider_id));
                                        put("subject_token_type", "jwt");
                                        put("subject_token", externalToken);
                                        put("audience", "tapkey_api");
                                        put("requested_token_type", "access_token");
                                    }});
                    TokenRequest tokenRequest = tokenRequestBuilder.build();
                    authService.performTokenRequest(tokenRequest, (response, ex) -> {
                        if (ex != null) {
                            Log.e(TAG, "failed to carry out token exchange");
                            res.setException(ex);
                            return;
                        }
                        res.setResult(response);
                    });
                    return res.getPromise();
                });
    }
}

/* /////////////////////////////////////////////////////////////////////////////////////////////////
//                          Copyright (c) Tapkey GmbH
//
//         All rights are reserved. Reproduction in whole or in part is
//        prohibited without the written consent of the copyright owner.
//    Tapkey reserves the right to make changes without notice at any time.
//   Tapkey makes no warranty, expressed, implied or statutory, including but
//   not limited to any implied warranty of merchantability or fitness for any
//  particular purpose, or that the use will not infringe any third party patent,
//   copyright or trademark. Tapkey must not be liable for any loss or damage
//                            arising from its use.
///////////////////////////////////////////////////////////////////////////////////////////////// */

package net.tpky.demoapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Base64;
import android.util.Log;
import android.view.View;

import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationRequest;
import net.openid.appauth.AuthorizationResponse;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.AuthorizationServiceConfiguration;
import net.openid.appauth.ResponseTypeValues;
import net.openid.appauth.TokenRequest;
import net.openid.appauth.TokenResponse;
import net.tpky.mc.AndroidTapkeyServiceFactory;
import net.tpky.mc.auth.AuthScopeBuilder;
import net.tpky.mc.concurrent.Async;
import net.tpky.mc.concurrent.CancellationToken;
import net.tpky.mc.concurrent.CancellationTokens;
import net.tpky.mc.concurrent.Promise;
import net.tpky.mc.concurrent.PromiseSource;
import net.tpky.mc.manager.NotificationManager;
import net.tpky.mc.manager.UserManager;

import java.security.SecureRandom;

/**
 * A login screen that offers login via email/password.
 */
public class LoginActivity extends AppCompatActivity {

    private static final String TAG = LoginActivity.class.getSimpleName();

    private static final int RC_AUTH = 1;


    private UserManager userManager;
    private NotificationManager notificationManager;

    private AuthorizationService authService;

    // UI references.
    private View mProgressView;
    private String state;
    private String verifier;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        App app = (App) getApplication();
        userManager = app.getTapkeyServiceFactory().getUserManager();
        notificationManager = app.getTapkeyServiceFactory().getNotificationManager();
        mProgressView = findViewById(R.id.login_progress);

        authService = new AuthorizationService(this);

        startLogon();
    }

    private App getApp() {
        return (App) getApplication();
    }

    private void startLogon() {

        CancellationToken cancellationToken = CancellationTokens.None;

        String clientId = getResources().getString(R.string.oauth_client_id);
        String serverUri = getResources().getString(R.string.oauth_authorization_server);

        AuthorizationServiceConfiguration serviceConfig =
                new AuthorizationServiceConfiguration(
                        Uri.parse(serverUri + "/authorize"), // authorization endpoint
                        Uri.parse(serverUri + "/token")); // token endpoint

        SecureRandom rng = new SecureRandom();
        byte[] stateBytes = new byte[32];
        byte[] verifierBytes = new byte[32];
        rng.nextBytes(stateBytes);
        rng.nextBytes(verifierBytes);

        state = Base64.encodeToString(stateBytes, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        verifier = Base64.encodeToString(verifierBytes, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);

        String scopes = new AuthScopeBuilder()
                .withLogin()
                .withKeyHandling()
                .build();

        AuthorizationRequest.Builder authRequestBuilder =
                new AuthorizationRequest.Builder(
                        serviceConfig, // the authorization service configuration
                        clientId, // the client ID, typically pre-registered and static
                        ResponseTypeValues.CODE, // the response_type value: we want a code
                        Uri.parse("net.tpky.demoapp.appauth://redirect"))
                        .setCodeVerifier(verifier)
                        .setState(state)
                        .setPrompt("login")
                        .setScope(scopes)
                ;

        AuthorizationRequest authRequest = authRequestBuilder.build();

        mProgressView.setVisibility(View.VISIBLE);
        Async.firstAsync(() ->
            executeAuthRequestAsync(authRequest)
        ).continueAsyncOnUi(response ->
            processAuthorizationResponse(response)
        ).continueAsyncOnUi(tokenResponse ->
            getApp().getoAuthFlow().authenticateAsync(tokenResponse.accessToken, tokenResponse.refreshToken, cancellationToken)
        ).continueOnUi(userId -> {
            if (!isFinishing())
                finish();
            return null;
        }).catchOnUi(e -> {
            Log.e(TAG, "Login failed.", e);
            return null;
        }).finallyOnUi(() -> {
            mProgressView.setVisibility(View.GONE);
        }).conclude();
    }


    private PromiseSource<AuthorizationResponse> pendingAuthorizationPromiseSorce;

    private Promise<AuthorizationResponse> executeAuthRequestAsync(AuthorizationRequest authRequest) {

        if (pendingAuthorizationPromiseSorce != null)
            return Async.PromiseFromException(new IllegalStateException());

        Intent authIntent = authService.getAuthorizationRequestIntent(authRequest);
        startActivityForResult(authIntent, RC_AUTH);

        pendingAuthorizationPromiseSorce = new PromiseSource<>();
        return pendingAuthorizationPromiseSorce.getPromise();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RC_AUTH) {

            PromiseSource<AuthorizationResponse> pendingPromiseSource = this.pendingAuthorizationPromiseSorce;
            if (pendingPromiseSource == null) {
                Log.w(TAG, "Received authorization callback without pending operation.");
                return;
            }

            this.pendingAuthorizationPromiseSorce = null;

            AuthorizationResponse resp = AuthorizationResponse.fromIntent(data);
            AuthorizationException ex = AuthorizationException.fromIntent(data);

            if (ex != null) {
                pendingPromiseSource.setException(ex);
            } else {
                pendingPromiseSource.setResult(resp);
            }
        }
    }

    private Promise<TokenResponse> processAuthorizationResponse(AuthorizationResponse resp) {

        PromiseSource<TokenResponse> res = new PromiseSource<>();

        TokenRequest tokenRequest = resp.createTokenExchangeRequest();
        authService.performTokenRequest(tokenRequest, (response, ex) -> {
            if (ex != null)
                res.setException(ex);
            else
                res.setResult(response);
        });

        return res.getPromise();
    }

    @Override
    protected void onDestroy() {

        if (authService != null) {
            authService.dispose();
            authService = null;
        }

        super.onDestroy();
    }
}


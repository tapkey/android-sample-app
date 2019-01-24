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

package net.tpky.demoapp.authentication;

import android.util.Log;

import com.auth0.android.authentication.AuthenticationException;
import com.auth0.android.result.Credentials;

import net.tpky.mc.concurrent.Async;
import net.tpky.mc.concurrent.AsyncException;
import net.tpky.mc.concurrent.CancellationToken;
import net.tpky.mc.concurrent.Promise;
import net.tpky.mc.dao.Dao;
import net.tpky.mc.dao.DataContext;
import net.tpky.mc.error.AuthenticationErrorCodes;
import net.tpky.mc.error.GenericErrorCodes;
import net.tpky.mc.error.TkException;
import net.tpky.mc.manager.ConfigManager;
import net.tpky.mc.manager.idenitity.IdentityProvider;
import net.tpky.mc.manager.idenitity.IdentityProviderErrorCodes;
import net.tpky.mc.model.Auth0Config;
import net.tpky.mc.model.Identity;
import net.tpky.mc.model.TkErrorDescriptor;
import net.tpky.mc.model.User;
import net.tpky.mc.model.auth0.Auth0Data;

public class Auth0PasswordIdentityProvider implements IdentityProvider {

    public static final String IP_ID = "com.auth0";

    private static final String TAG = Auth0PasswordIdentityProvider.class.getSimpleName();
    private static final String PARTITION_KEY = "";

    private final ConfigManager configManager;
    private final Dao<Auth0Data> auth0Dao;

    public Auth0PasswordIdentityProvider(ConfigManager configManager, DataContext dataContext) {
        this.configManager = configManager;
        this.auth0Dao = dataContext.getAuth0Dao();
    }

    public Promise<Identity> signInWithPassword(String email, String password, CancellationToken cancellationToken) {

        return this.getAuth0Client(cancellationToken)

                .continueAsyncOnUi((AsyncAuth0Client asyncAuth0) ->

                        asyncAuth0.signIn(email, password, "openid email offline_access")
                            .continueOnUi((Credentials response) -> {

                                if (response == null)
                                    throw new IllegalStateException("no response");

                                if (response.getIdToken() != null) {

                                    Auth0Data auth0Data = this.auth0Dao.get(PARTITION_KEY, email);
                                    String refreshToken = (auth0Data != null) ? auth0Data.getRefresh_token() : null;

                                    if(refreshToken != null){
                                        Log.w(TAG, "There is already a refresh token for this user. Invalidate it now");
                                        asyncAuth0.revokeToken(refreshToken)
                                                .continueOnUi(arg1 -> {
                                                    Log.d(TAG, "Revocation of token was successfully");
                                                    return null;
                                                })
                                                .catchOnUi( e -> {
                                                    Log.e(TAG, "Failed to revoke token", e);
                                                    return null;
                                                });
                                    }

                                    auth0Dao.save(PARTITION_KEY, email, new Auth0Data(response.getAccessToken(), response.getRefreshToken()));
                                }

                                return new Identity(IP_ID, response.getIdToken());

                            })
                            .catchOnUi((Exception e) -> {
                                parseException(e);
                                return null;
                            })
                );

    }

    private void parseException(Exception e) throws Exception {

        Exception srcException = (e instanceof AsyncException) ? ((AsyncException) e).getSyncSrcException() : e;

        if (srcException instanceof AuthenticationException) {

            AuthenticationException authenticationException = (AuthenticationException) srcException;

            if (authenticationException.isInvalidCredentials()) {
                Log.e(TAG, "Username or password wrong");
                throw new TkException(new TkErrorDescriptor(AuthenticationErrorCodes.VerificationFailed, "", null));
            }

            if (authenticationException.getCode() != null && authenticationException.getCode().equals("too_many_attempts")) {
                throw new TkException(new TkErrorDescriptor(AuthenticationErrorCodes.TooManyAttempts, "", null));
            }

        }

        if (srcException instanceof TkException) {
            throw srcException;
        }

        Log.e(TAG, "Authentication failed", srcException);
        throw new TkException(new TkErrorDescriptor(GenericErrorCodes.GenericError, "", null));
    }

    @Override
    public Promise<Void> logOutAsync(User user, CancellationToken cancellationToken) {

        String email = user.getIpUserName();

        Auth0Data auth0Data = this.auth0Dao.get(PARTITION_KEY, email);

        // no user was signed in, return
        if (auth0Data == null)
            return Async.PromiseFromException(new IllegalStateException("No user signed in with this username"));

        // delete refresh_token
        this.auth0Dao.delete(PARTITION_KEY, email);


        String refreshToken = auth0Data.getRefresh_token();

        if(refreshToken == null){
            Log.w(TAG, "No refresh token persisted. Skip revocation of refresh token");
            return Async.PromiseFromResult(null);
        }

        Log.d(TAG, "Going to revoke refresh token.");

        return this.getAuth0Client(cancellationToken)
                .continueAsyncOnUi(

                        auth0Client -> auth0Client.revokeToken(refreshToken)
                                .continueOnUi(aVoid -> {
                                    Log.d(TAG, "Revocation of refresh token was successfully.");
                                    return null;
                                })
                                .catchOnUi(e -> {
                                    Log.e(TAG, "Revocation of refresh token failed.", e);
                                    return null;
                                }).asVoid()
                );

    }

    @Override
    public Promise<Identity> refreshToken(User user, CancellationToken cancellationToken) {

        String ipId = user.getIpId();
        String email = user.getIpUserName();

        // Verify that this IdentityProvider is responsible for this user
        if (!ipId.equals(IP_ID)) {
            Log.e(TAG, "The ipId " + ipId + " can not be handled by this IdentityProvider.");
            return Async.PromiseFromException(new IllegalArgumentException("The ipId " + ipId + " can not be handled by this IdentityProvider."));
        }

        Auth0Data auth0Data = this.auth0Dao.get(PARTITION_KEY, email);

        // Verify that this user was logged in by this IdentityProvider
        if (auth0Data == null) {
            Log.e(TAG, "This user was not logged in by this IdentityProvider.");
            return Async.PromiseFromException(new IllegalStateException("This user was not logged in by this IdentityProvider."));
        }

        return this.getAuth0Client(cancellationToken)
                .continueAsyncOnUi((AsyncAuth0Client client) ->

                    client.renewAuth(auth0Data.getRefresh_token())

                            .continueAsyncOnUi( credentials -> {

                                if(credentials.getIdToken() != null){
                                    return Async.PromiseFromResult(new Identity(IP_ID, credentials.getIdToken()));
                                }

                                ////
                                // When use was signed in with the previous auth0 identity provider version, the scope openId was not
                                // requested. So the user won't get the idToken direct via renewAuth and we have to call
                                // the delegate endpoint!
                                if(credentials.getAccessToken() != null){

                                    Log.d(TAG, "This seems to be a legacy user. User does not have openId in scope. Have to obtain IdToken via delegation endpoint!");

                                    return client.delegate(auth0Data.getRefresh_token())
                                            .continueOnUi(delegation -> {

                                                if(delegation.getIdToken() != null){
                                                    return new Identity(IP_ID, delegation.getIdToken());
                                                }

                                                throw new IllegalStateException("Failed to obtain a IdToken from delegation endpoint");

                                            });

                                }

                                throw new IllegalStateException("Use does not have neither idToken nor accessToken. Can't reAuthenticate");

                            })
                            .catchOnUi(e -> {
                                Log.e(TAG, "Refresh token via auth0 failed", e);
                                throw new TkException(new TkErrorDescriptor(IdentityProviderErrorCodes.TokenRefreshFailed, "Refresh token via auth0 failed", null));
                            })
                );

    }


    private Promise<AsyncAuth0Client> getAuth0Client(CancellationToken cancellationToken) {
        return this.configManager.updateConfigAsync(cancellationToken)
                .continueOnUi(config -> {

                    if (config == null) {
                        throw new IllegalStateException("Config result was unexpected null.");
                    }

                    Auth0Config auth0Config = config.getAuth0Config();

                    if (auth0Config == null || auth0Config.getServer() == null || auth0Config.getClientId() == null || auth0Config.getConnection() == null) {
                        throw new Exception("Can't fetch auth0 information");
                    }

                    return new AsyncAuth0Client(auth0Config);
                })

                .catchOnUi(e -> {
                    Log.e(TAG, "Failed to fetch auth0 configuration from backend", e);
                    throw e;
                });
    }

}

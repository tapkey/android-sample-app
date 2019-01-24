package net.tpky.demoapp.authentication;

import com.auth0.android.Auth0;
import com.auth0.android.authentication.AuthenticationAPIClient;
import com.auth0.android.authentication.AuthenticationException;
import com.auth0.android.callback.BaseCallback;
import com.auth0.android.request.AuthenticationRequest;
import com.auth0.android.request.Request;
import com.auth0.android.result.Credentials;
import com.auth0.android.result.Delegation;

import net.tpky.mc.concurrent.Promise;
import net.tpky.mc.concurrent.PromiseSource;
import net.tpky.mc.model.Auth0Config;

class AsyncAuth0Client {

    private final Auth0 auth0;
    private final AuthenticationAPIClient apiClient;
    private final String server;
    private final String dbConnection;

    AsyncAuth0Client(Auth0Config auth0Config) {
        this(auth0Config.getClientId(), auth0Config.getServer(), auth0Config.getConnection());
    }

    AsyncAuth0Client(String clientId, String server, String dbConnection) {

        this.server = server;
        this.dbConnection = dbConnection;

        String domain = server.replace("https://", "");


        auth0 = new Auth0(clientId, domain);
        auth0.setOIDCConformant(true);

        apiClient = new AuthenticationAPIClient(auth0);
    }

    AsyncAuth0Client setLoggingEnabled(boolean enabled){
        this.auth0.setLoggingEnabled(enabled);
        return this;
    }

    Promise<Credentials> signIn(String email, String password, String scope){

        AuthenticationRequest request = apiClient
                .login(email, password, dbConnection)
                .setAudience(server + "/userinfo")
                .setScope(scope);

        return this.requestToPromise(request);
    }

    Promise<Credentials> renewAuth(String refreshToken){
        return requestToPromise(apiClient.renewAuth(refreshToken));
    }

    Promise<Delegation> delegate(String refreshToken) {
        return requestToPromise(apiClient.delegationWithRefreshToken(refreshToken));
    }

    Promise<Void> revokeToken(String refreshToken){
        return requestToPromise(apiClient.revokeToken(refreshToken));
    }

    private <T> Promise<T> requestToPromise(Request<T, AuthenticationException> request) {

        PromiseSource<T> promiseSource = new PromiseSource<>();

        request.start(new BaseCallback<T, AuthenticationException>() {
            @Override
            public void onSuccess(T payload) {
                promiseSource.setResult(payload);
            }

            @Override
            public void onFailure(AuthenticationException error) {
                promiseSource.setException(error);
            }
        });

        return promiseSource.getPromise();

    }

}

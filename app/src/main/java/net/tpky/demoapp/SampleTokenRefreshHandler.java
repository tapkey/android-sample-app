package net.tpky.demoapp;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.tapkey.mobile.auth.AuthenticationHandlerErrorCodes;
import com.tapkey.mobile.auth.TokenRefreshHandler;
import com.tapkey.mobile.concurrent.CancellationToken;
import com.tapkey.mobile.concurrent.Promise;
import com.tapkey.mobile.error.TkException;

class SampleTokenRefreshHandler implements TokenRefreshHandler {

    private static final String TAG = MainActivity.class.getSimpleName();

    private final Context context;
    private final TapkeyTokenExchangeManager tokenExchangeManager;
    private final SampleServerManager sampleServerManager;

    SampleTokenRefreshHandler(Context context) {
        this.context = context;
        this.tokenExchangeManager = new TapkeyTokenExchangeManager(context);
        this.sampleServerManager = new SampleServerManager(context);
    }

    @Override
    public Promise<String> refreshAuthenticationAsync(
            String tapkeyUserId, CancellationToken cancellationToken) {
        if (!AuthStateManager.isLoggedIn(context)) {
            throw new TkException(AuthenticationHandlerErrorCodes.TokenRefreshFailed);
        }

        return sampleServerManager.getExternalToken(
                AuthStateManager.getUsername(context),
                AuthStateManager.getPassword(context)
        )
                .continueAsyncOnUi(tokenExchangeManager::exchangeToken)
                .continueOnUi(tokenResponse -> tokenResponse.accessToken)
                .catchOnUi(ex -> {
                    throw new TkException(AuthenticationHandlerErrorCodes.TokenRefreshFailed);
                });
    }

    @Override
    public void onRefreshFailed(String tapkeyUserId) {
        /*
         * This sample app does not support multiple Tapkey users, hence the user ID is ignored. It
         * is good practice to check if it matches the user that is expected nonetheless in real
         * applications.
         * Furthermore, if your application is likely unable to recover from this situation without
         * running the user through the application's own authentication logic, this is a good place
         * to force-logout the user, for instance, in this sample:
         * AuthStateManager.setLoggedOut(this).
         */
        Log.d(TAG, "Refreshing Tapkey authentication failed. Redirecting to login activity.");
        Intent intent = new Intent(context, LoginActivity.class);
        context.startActivity(intent);
    }
}

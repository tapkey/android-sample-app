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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;

import net.tpky.demoapp.authentication.Auth0PasswordIdentityProvider;
import net.tpky.mc.concurrent.AsyncException;
import net.tpky.mc.concurrent.CancellationTokens;
import net.tpky.mc.error.AuthenticationErrorCodes;
import net.tpky.mc.error.TkException;
import net.tpky.mc.manager.NotificationManager;
import net.tpky.mc.manager.UserManager;
import net.tpky.mc.model.User;
import net.tpky.mc.utils.Func1;

/**
 * A login screen that offers login via email/password.
 */
public class LoginActivity extends AppCompatActivity {

    private static final String TAG = LoginActivity.class.getSimpleName();

    private UserManager userManager;
    private Auth0PasswordIdentityProvider passwordIdentityProvider;
    private NotificationManager notificationManager;

    private boolean signinInProgress;

    // UI references.
    private AutoCompleteTextView mEmailView;
    private EditText mPasswordView;
    private View mProgressView;
    private View mLoginFormView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        App app = (App) getApplication();
        userManager = app.getTapkeyServiceFactory().getUserManager();
        passwordIdentityProvider = app.getPasswordIdentityProvider();
        notificationManager = app.getTapkeyServiceFactory().getNotificationManager();

        // Set up the login form.
        mEmailView = findViewById(R.id.email);

        mPasswordView = findViewById(R.id.password);
        mPasswordView.setOnEditorActionListener((textView, id, keyEvent) -> {
            if (id == R.id.login || id == EditorInfo.IME_NULL) {
                attemptLogin();
                return true;
            }
            return false;
        });

        Button mEmailSignInButton = findViewById(R.id.email_sign_in_button);
        mEmailSignInButton.setOnClickListener(view -> attemptLogin());

        mLoginFormView = findViewById(R.id.login_form);
        mProgressView = findViewById(R.id.login_progress);
    }

    /**
     * Attempts to sign in or register the account specified by the login form.
     * If there are form errors (invalid email, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     */
    private void attemptLogin() {


        if (signinInProgress) {
            return;
        }

        // Reset errors.
        mEmailView.setError(null);
        mPasswordView.setError(null);

        // Store values at the time of the login attempt.
        String email = mEmailView.getText().toString();
        String password = mPasswordView.getText().toString();

        // Check for a valid email address.
        if (TextUtils.isEmpty(email)) {
            mEmailView.setError(getString(R.string.error_field_required));
            mEmailView.requestFocus();
            return;
        }

        if (!isEmailValid(email)) {
            mEmailView.setError(getString(R.string.error_invalid_email));
            mEmailView.requestFocus();
            return;
        }

        // Check for a valid password, if the user entered one.
        if (TextUtils.isEmpty(password)) {
            mPasswordView.setError(getString(R.string.error_invalid_password));
            mPasswordView.requestFocus();
            return;
        }

        showProgress(true);

        // as the sign in process is asynchronous, we track the info, that the process is running
        // (will be started) now, to avoid concurrency issues.
        signinInProgress = true;

        // start the asynchronous sign in process.
        passwordIdentityProvider.signInWithPassword(mEmailView.getText().toString(), mPasswordView.getText().toString(), CancellationTokens.None)

                .continueAsyncOnUi(identity -> {
                    // if authentication against our identity provider succeeded, provide the
                    // identity to the Tapkey UserManager to authenticate against the Tapkey
                    // Trust Service.
                    return userManager.authenticateAsync(identity, CancellationTokens.None);
                })

                // when done, continue on the UI thread
                .continueOnUi((Func1<User, Void, Exception>) user -> {

                    Log.d(TAG, "Sign in was successful");

                    // actively poll for notifications, so we don't have to wait for push
                    // notifications being delivered.
                    notificationManager.pollForNotificationsAsync()
                            .catchOnUi(e -> {
                                Log.e(TAG, "Couldn't poll for notifications.", e);
                                return null;
                            }).conclude();

                    // redirect to the main activity
                    Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                    startActivity(intent);
                    finish();

                    return null;
                })

                // Handle any exceptions on the UI thread
                .catchOnUi(e -> {

                    // If this is an AsyncException, that was raised in an async call (which is most likely here),
                    // find out, what the original exception was.
                    Exception syncSrcException = (e instanceof AsyncException) ? ((AsyncException) e).getSyncSrcException() : e;

                    Log.e(TAG, "Sign in failed", syncSrcException);

                    if (syncSrcException instanceof TkException) {

                        if (AuthenticationErrorCodes.VerificationFailed.equals(((TkException) syncSrcException).getErrorCode())) {
                            mEmailView.setError(getString(R.string.error_sign_in_failed));
                            mPasswordView.setError(getString(R.string.error_sign_in_failed));
                            mPasswordView.requestFocus();
                            return null;
                        }
                    }

                    mEmailView.setError(getString(R.string.error_sign_in_generic_error));
                    mPasswordView.setError(getString(R.string.error_sign_in_generic_error));
                    mEmailView.requestFocus();

                    return null;
                })

                // finally - do this on the UI thread
                .finallyOnUi(() -> {

                    // mark, that the sign in process is not pending any more.
                    signinInProgress = false;
                    showProgress(false);
                })

                // make sure, not to miss any exceptions
                .conclude();
    }

    private boolean isEmailValid(String email) {
        //TODO: Replace this with your own logic
        return email.contains("@");
    }

    /**
     * Shows the progress UI and hides the login form.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    private void showProgress(final boolean show) {

        int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

        mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
        mLoginFormView.animate().setDuration(shortAnimTime).alpha(
                show ? 0 : 1).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
            }
        });

        mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
        mProgressView.animate().setDuration(shortAnimTime).alpha(
                show ? 1 : 0).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        });
    }
}


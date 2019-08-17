package net.tpky.demoapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.tapkey.mobile.concurrent.CancellationTokens;
import com.tapkey.mobile.manager.UserManager;

import java.util.Objects;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = LoginActivity.class.getSimpleName();

    private UserManager userManager;

    private View mProgressView;
    private Button mButtonCreate;
    private EditText mEditTextEmail;
    private EditText mEditTextPassword;
    private TextView mTextViewErrors;

    private SampleServerManager sampleServerManager;
    private TapkeyTokenExchangeManager tokenExchangeManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mProgressView = findViewById(R.id.progressViewLogin);
        mButtonCreate = findViewById(R.id.buttonCreateAccount);
        mEditTextEmail = findViewById(R.id.editTextEmail);
        mEditTextPassword = findViewById(R.id.editTextPassword);
        mTextViewErrors = findViewById(R.id.textViewErrors);

        App app = (App) getApplication();
        userManager = app.getTapkeyServiceFactory().getUserManager();
        tokenExchangeManager = new TapkeyTokenExchangeManager(this);
        sampleServerManager = new SampleServerManager(this);
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (AuthStateManager.isLoggedIn(this)) {
            Log.d(TAG, "Already authorized, redirecting to MainActivity.");
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        }
    }

    public void onClickCreateAccount(View view) {
        String username = Objects.requireNonNull(mEditTextEmail.getText()).toString();
        String password = Objects.requireNonNull(mEditTextPassword.getText()).toString();

        mProgressView.setVisibility(View.VISIBLE);
        mButtonCreate.setEnabled(false);

        sampleServerManager.registerUser(username, password)
                .continueAsyncOnUi(userId -> {
                    AuthStateManager.setLoggedIn(this, username, password);
                    return sampleServerManager.getExternalToken(username, password);
                })
                .continueAsyncOnUi(externalToken -> tokenExchangeManager.exchangeToken(externalToken))
                .continueAsyncOnUi(tokenResponse -> this.userManager.logInAsync(tokenResponse.accessToken, CancellationTokens.None))
                .continueOnUi(tapkeyUserId -> {
                    mProgressView.setVisibility(View.GONE);
                    Log.d(TAG, String.format("Created account and logged in Tapkey user %s, redirecting to MainActivity.", tapkeyUserId));
                    Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                    startActivity(intent);
                    return null;
                })
                .catchOnUi(e -> {
                    Log.e(TAG, "Error while creating account and logging in.", e);
                    mProgressView.setVisibility(View.GONE);
                    mButtonCreate.setEnabled(true);
                    mTextViewErrors.setText(e.getMessage());
                    AuthStateManager.setLoggedOut(this);
                    return null;
                })
                .conclude();
    }
}

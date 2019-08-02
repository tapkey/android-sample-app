package net.tpky.demoapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.navigation.NavigationView;
import com.tapkey.mobile.TapkeyServiceFactory;
import com.tapkey.mobile.concurrent.Async;
import com.tapkey.mobile.concurrent.CancellationTokens;
import com.tapkey.mobile.manager.UserManager;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int LOGON_REQUEST_CODE = 1;


    private TapkeyServiceFactory tapkeyServiceFactory;
    private TextView usernameTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /*
         * Retrieve the TapkeyServiceFactory singleton.
         */
        App app = (App) getApplication();
        this.tapkeyServiceFactory = app.getTapkeyServiceFactory();

        UserManager userManager = tapkeyServiceFactory.getUserManager();

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        View headerView = navigationView.getHeaderView(0);
        this.usernameTextView = (TextView) headerView.findViewById(R.id.nav_header__username);

        /*
         * Redirect to LoginActivity if not authorized.
         */
        if (!AuthStateManager.isLoggedIn(this)) {
            Log.d(TAG, "Not authorized. Redirecting to LoginActivity.");
            Intent intent = new Intent(this, LoginActivity.class);
            startActivityForResult(intent, LOGON_REQUEST_CODE);
        }
        /*
         * Check for Tapkey SDK login status.
         */
        else if (userManager.getUsers().isEmpty()) {
            Log.d(TAG, "Tapkey SDK: No user is logged in. Attempting to log in again.");

            /*
             * In a real application, the implementor may want to try to re-authenticate
             * the user gracefully. In this sample app, the entire authentication
             * information is deleted and the user is redirected to the login page.
             */
            AuthStateManager.setLoggedOut(this);
            Intent intent = new Intent(this, LoginActivity.class);
            startActivityForResult(intent, LOGON_REQUEST_CODE);
        } else {
            refreshUi();
        }
    }

    private void refreshUi() {

        UserManager userManager = tapkeyServiceFactory.getUserManager();

        // TODO handle more than one users
        String firstUser = userManager.getUsers().get(0);
        if (firstUser != null) {
            usernameTextView.setText(firstUser); // TODO query for user's details
        }
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {

        // Handle navigation view item clicks here.
        int id = item.getItemId();

        switch (id) {

            case R.id.nav__sign_out:
                logOut();
                break;

            case R.id.nav__refresh:
                refreshKeys();
                break;

            case R.id.nav__about:
                Intent intent = new Intent(this, AboutActivity.class);
                startActivity(intent);
                break;
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private void refreshKeys() {

        Async.firstAsync(() ->
                tapkeyServiceFactory.getNotificationManager().pollForNotificationsAsync(CancellationTokens.None)
        ).catchOnUi(e -> {
            Log.e(TAG, "Error while polling for notifications.", e);
            return null;
        }).finallyOnUi(() ->
                Log.i(TAG, "Polling for notifications completed.")
        ).conclude();
    }

    /*
     * Sign out user
     */
    private void logOut() {
        UserManager userManager = tapkeyServiceFactory.getUserManager();

        // Tapkey SDK logout
        if (!userManager.getUsers().isEmpty()) {
            String userId = userManager.getUsers().get(0);

            Async.firstAsync(() ->
                    userManager.logOutAsync(userId, CancellationTokens.None)
            ).catchOnUi(e -> {
                Log.e(TAG, "Could not log out user: " + userId, e);
                return null;
            }).conclude();
        }

        AuthStateManager.setLoggedOut(this);

        /*
         * Redirect to LoginActivity
         */
        Intent intent = new Intent(this, LoginActivity.class);
        startActivityForResult(intent, LOGON_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        refreshUi();
        super.onActivityResult(requestCode, resultCode, data);
    }
}

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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import net.tpky.demoapp.authentication.Auth0PasswordIdentityProvider;
import net.tpky.mc.TapkeyServiceFactory;
import net.tpky.mc.concurrent.CancellationTokens;
import net.tpky.mc.manager.ConfigManager;
import net.tpky.mc.manager.UserManager;
import net.tpky.mc.model.User;
import net.tpky.mc.utils.Func1;

import java.util.List;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    private UserManager userManager;
    private Auth0PasswordIdentityProvider passwordIdentityProvider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /*
         * Retrieve the TapkeyServiceFactory singleton.
         */
        App app = (App) getApplication();
        TapkeyServiceFactory tapkeyServiceFactory = app.getTapkeyServiceFactory();

        ConfigManager configManager = tapkeyServiceFactory.getConfigManager();
        userManager = tapkeyServiceFactory.getUserManager();
        passwordIdentityProvider = app.getPasswordIdentityProvider();

        /*
         * When no user is signed in, redirect to login LoginActivity
         */
        if(!userManager.hasUsers()){
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
            finish();
            return;
        }

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
        TextView usernameTextView = (TextView) headerView.findViewById(R.id.nav_header__username);

        /*
         * Get the username of first user and display it in side menu. The first user is used here,
         * because we don't support multi-user yet anyways.
         */
        User firstUser = userManager.getFirstUser();
        if(firstUser != null){
            usernameTextView.setText(firstUser.getIpUserName());
        }

        /*
         * Verify, that app is compatible
         */
        configManager.getAppState(CancellationTokens.None)

                // asynchronously receive the operation result on the UI thread
                .continueOnUi((Func1<ConfigManager.AppState, Void, Exception>) appState -> {

                    // some time might have passed and maybe the activity is already finishing.
                    if (isFinishing())
                        return null;


                    switch (appState) {

                        // SDK Version is not supported anymore
                        case CLIENT_TOO_OLD:

                            new AlertDialog.Builder(MainActivity.this)
                                    .setMessage(R.string.main_app_too_old)
                                    .setPositiveButton(R.string.app_too_old_button, new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int id) {
                                            finish();
                                        }
                                    })
                                    .setOnDismissListener(dialogInterface -> {

                                        // TODO: Redirect to the app store, to let the user
                                        // download the latest app version.

                                        finish();
                                    })
                                    .create()
                                    .show();

                            break;

                        // This version of the SDK is still supported but is short before expiring and should be updated.
                        case CLIENT_UPDATE_SUGGESTED:

                            new AlertDialog.Builder(MainActivity.this)
                                    .setMessage(R.string.newer_app_available)
                                    .setPositiveButton(R.string.newer_app_available_button, null)
                                    .create()
                                    .show();

                            break;

                        case CLIENT_VERSION_OK:
                        default:
                    }

                    return null;
                })

                // Handle exceptions raised in async code.
                .catchOnUi(e -> {

                    // Handle error
                    Log.e(TAG, "failed to fetch app state: ", e);
                    return null;
                })

                // Make sure, no exceptions get lost.
                .conclude();
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
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

        switch (id){

            case R.id.nav__sign_out:
                signOut();
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


    /*
     * Sign out user
     */
    private void signOut(){

        /**
         * To sign out user, get all users and call {@link net.tpky.mc.manager.UserManager#logOff(User)}}
         * for each
         */

        List<User> users = userManager.getUsers();

        for(User user : users){
            userManager.logOff(user, CancellationTokens.None);
            passwordIdentityProvider.logOutAsync(user, CancellationTokens.None);
        }

        /*
         * Redirect to LoginActivity
         */
        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);

        finish();
    }
}

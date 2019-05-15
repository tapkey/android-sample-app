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
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import com.google.android.material.snackbar.Snackbar;
import androidx.fragment.app.ListFragment;
import androidx.core.content.ContextCompat;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import net.tpky.mc.TapkeyServiceFactory;
import net.tpky.mc.concurrent.CancellationToken;
import net.tpky.mc.concurrent.CancellationTokens;
import net.tpky.mc.concurrent.Promise;
import net.tpky.mc.manager.BleLockManager;
import net.tpky.mc.manager.CommandExecutionFacade;
import net.tpky.mc.manager.KeyManager;
import net.tpky.mc.manager.UserManager;
import net.tpky.mc.model.BleLock;
import net.tpky.mc.model.User;
import net.tpky.mc.model.webview.CachedKeyInformation;
import net.tpky.mc.utils.Func1;
import net.tpky.mc.utils.ObserverRegistration;

import java.util.List;
import java.util.Map;


public class KeyListFragment extends ListFragment {

    private final static String TAG = KeyListFragment.class.getSimpleName();
    private static final int PERMISSIONS_REQUEST__ACCESS_COARSE_LOCATION = 0;

    private KeyManager keyManager;
    private CommandExecutionFacade commandExecutionFacade;
    private UserManager userManager;
    private BleLockManager bleLockManager;

    private ArrayAdapter<CachedKeyInformation> adapter;

    private ObserverRegistration bleObserverRegistration;
    private ObserverRegistration keyUpdateObserverRegistration;

    // the handler that connects the individual items of the key ring (i.e. individual keys) to the
    // functional components.
    private final KeyItemAdapter.KeyItemAdapterHandler keyItemAdapterHandler = new KeyItemAdapter.KeyItemAdapterHandler() {

        @Override
        public boolean isLockNearby(String physicalLockId) {
            return (physicalLockId != null) && bleLockManager.isLockNearby(physicalLockId);
        }

        @Override
        public Promise<Boolean> triggerLock(String physicalLockId, CancellationToken ct) {

            // let the BLE lock manager establish a connection to the BLE lock and then let the
            // CommandExecutionFacade use this connection to execute a TriggerLock command.
            return bleLockManager.executeCommandAsync(new String[0], physicalLockId, tlcpConnection -> {

                // now, that we have a TlcpConnection to the lock, let the CommandExecutionFacade
                // asynchronously execute the trigger lock command.
                return commandExecutionFacade.triggerLockAsync(tlcpConnection, ct);

            }, ct).continueOnUi(commandResult -> {

                switch (commandResult.getCommandResultCode()) {
                    case Ok:
                        return true;

                    // TODO: Issue meaningful error messages for different error codes here.
                    // Functionality to do this will be provided in future versions of the
                    // App SDK.
                }

                // let the user know, something went wrong.
                Toast.makeText(getContext(), R.string.key_item__triger_failed, Toast.LENGTH_SHORT).show();
                return false;

            }).catchOnUi(e -> {

                Log.e(TAG, "Couldn't execute trigger lock command.", e);

                if (!ct.isCancellationRequested()) {
                    // handle any exceptions and let the user know, something went wrong.
                    Toast.makeText(getContext(), R.string.key_item__triger_failed, Toast.LENGTH_SHORT).show();
                }
                return false;

            });
        }
    };

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        App app = (App) getActivity().getApplication();
        TapkeyServiceFactory tapkeyServiceFactory = app.getTapkeyServiceFactory();
        keyManager = tapkeyServiceFactory.getKeyManager();
        commandExecutionFacade = tapkeyServiceFactory.getCommandExecutionFacade();
        userManager = tapkeyServiceFactory.getUserManager();
        bleLockManager = tapkeyServiceFactory.getBleLockManager();

        adapter = new KeyItemAdapter(getActivity(), keyItemAdapterHandler);

        setListAdapter(adapter);

        // make sure, we have the ACCESS_COARSE_LOCATION permission, which is required, to detect
        // BLE locks nearby.
        if (ContextCompat.checkSelfPermission(getContext(), android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            // Should we explain to the user, why we need this permission before actually requesting
            // it? This is the case, if the user rejected the permission before.
            if (shouldShowRequestPermissionRationale(android.Manifest.permission.ACCESS_COARSE_LOCATION)) {
                showPermissionRationale();
            }else{
                requestPermission();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Listen for changes in the available keys. Changes might happen, e.g. due to push
        // notifications received from the Tapkey backend.
        if (keyUpdateObserverRegistration == null) {
            keyUpdateObserverRegistration = keyManager.getKeyUpdateObservable().addObserver(aVoid -> onKeyUpdate(false));
        }
        onKeyUpdate(true);

        // listen for Tapkey locks coming into or leaving range.
        if (bleObserverRegistration == null) {
            bleObserverRegistration = bleLockManager.getLocksChangedObservable().addObserver(stringBleLockMap -> bleLocksChanged(stringBleLockMap));
        }

        if (ContextCompat.checkSelfPermission(getContext(), android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                // If we have the COARSE_LOCATION permission, start scanning for BLE devices
                bleLockManager.startForegroundScan();
            } catch (Exception e) {
                Log.i(TAG, "Couldn't start scanning for BLE locks nearby.", e);
            }
        }else{
            if (shouldShowRequestPermissionRationale(android.Manifest.permission.ACCESS_COARSE_LOCATION)) {
                showPermissionRationale();
            }
        }

    }

    @Override
    public void onPause() {
        super.onPause();

        // stop scanning for BLE locks nearby.
        bleLockManager.stopForegroundScan();

        // stop listening for key updates and BLE lock changes.
        if (keyUpdateObserverRegistration != null) {
            keyUpdateObserverRegistration.close();
            keyUpdateObserverRegistration = null;
        }
        if (bleObserverRegistration != null) {
            bleObserverRegistration.close();
            bleObserverRegistration = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST__ACCESS_COARSE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    bleLockManager.startForegroundScan();
                } else {

                    if (shouldShowRequestPermissionRationale(android.Manifest.permission.ACCESS_COARSE_LOCATION)) {
                        showPermissionRationale();
                    } else {

                        Snackbar.make(getView(), R.string.key_item__permission_needed, Snackbar.LENGTH_INDEFINITE)
                                .setAction("ALLOW", view -> {

                                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", getActivity().getPackageName(), null));
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    startActivity(intent);

                                }).show();

                    }
                }
            }

        }
    }

    private void showPermissionRationale(){

        Snackbar.make(getView(), R.string.key_item__permission_needed, Snackbar.LENGTH_INDEFINITE)
                .setAction("ALLOW", view -> requestPermission()).show();
    }

    private void requestPermission(){
        requestPermissions(new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSIONS_REQUEST__ACCESS_COARSE_LOCATION);
    }


    private void onKeyUpdate(boolean forceUpdate){

        // We only support a single user today, so the first user is the only user.
        User firstUser = userManager.getFirstUser();

        if(firstUser == null){
            // should not happen.
            Log.e(TAG, "onKeyUpdate: No user is signed in");
            return;
        }

        // query for this user's keys asynchronously
        keyManager.queryLocalKeysAsync(firstUser, forceUpdate, CancellationTokens.None)

                // when completed with success, continue on the UI thread
                .continueOnUi((Func1<List<CachedKeyInformation>, Void, Exception>) cachedKeyInformations -> {

                    adapter.clear();
                    adapter.addAll(cachedKeyInformations);

                    return null;
                })

                // handle async exceptions
                .catchOnUi(e -> {

                    Log.e(TAG, "query local keys failed ", e);
                    // Handle error
                    return null;
                })

                // make sure, we don't miss any exceptions.
                .conclude();

    }

    private void bleLocksChanged(Map<String, BleLock> stringBleLockMap){
        adapter.notifyDataSetChanged();
    }
}

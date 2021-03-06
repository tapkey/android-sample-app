package net.tpky.demoapp;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.ListFragment;

import com.google.android.material.snackbar.Snackbar;
import com.tapkey.mobile.TapkeyServiceFactory;
import com.tapkey.mobile.ble.BleLockCommunicator;
import com.tapkey.mobile.ble.BleLockScanner;
import com.tapkey.mobile.concurrent.CancellationToken;
import com.tapkey.mobile.concurrent.CancellationTokens;
import com.tapkey.mobile.concurrent.Promise;
import com.tapkey.mobile.manager.CommandExecutionFacade;
import com.tapkey.mobile.manager.KeyManager;
import com.tapkey.mobile.manager.UserManager;
import com.tapkey.mobile.model.KeyDetails;
import com.tapkey.mobile.utils.ObserverRegistration;
import com.tapkey.mobile.utils.Tuple;

import java.util.stream.Collectors;


public class KeyListFragment extends ListFragment {

    private final static String TAG = KeyListFragment.class.getSimpleName();
    private static final int PERMISSIONS_REQUEST__ACCESS_FINE_LOCATION = 0;

    private KeyManager keyManager;
    private CommandExecutionFacade commandExecutionFacade;
    private UserManager userManager;
    private BleLockScanner bleLockScanner;
    private BleLockCommunicator bleLockCommunicator;
    private SampleServerManager sampleServerManager;

    private ArrayAdapter<Tuple<KeyDetails, ApplicationGrantDto>> adapter;

    private ObserverRegistration bleScanObserverRegistration;
    private ObserverRegistration bleObserverRegistration;
    private ObserverRegistration keyUpdateObserverRegistration;

    // the handler that connects the individual items of the key ring (i.e. individual keys) to the
    // functional components.
    private final KeyItemAdapter.KeyItemAdapterHandler keyItemAdapterHandler = new KeyItemAdapter.KeyItemAdapterHandler() {

        @Override
        public boolean isLockNearby(String physicalLockId) {
            return (physicalLockId != null) && bleLockScanner.isLockNearby(physicalLockId);
        }

        @Override
        public Promise<Boolean> triggerLock(String physicalLockId, CancellationToken ct) {

            String bluetoothAddress = bleLockScanner.getLock(physicalLockId).getBluetoothAddress();

            // let the BLE lock manager establish a connection to the BLE lock and then let the
            // CommandExecutionFacade use this connection to execute a TriggerLock command.
            return bleLockCommunicator.executeCommandAsync(bluetoothAddress, physicalLockId, tlcpConnection -> {

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
                Toast.makeText(getContext(), R.string.key_item__trigger_lock_failed, Toast.LENGTH_SHORT).show();
                return false;

            }).catchOnUi(e -> {

                Log.e(TAG, "Couldn't execute trigger lock command.", e);

                if (!ct.isCancellationRequested()) {
                    // handle any exceptions and let the user know, something went wrong.
                    Toast.makeText(getContext(), R.string.key_item__trigger_lock_failed, Toast.LENGTH_SHORT).show();
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
        bleLockScanner = tapkeyServiceFactory.getBleLockScanner();
        bleLockCommunicator = tapkeyServiceFactory.getBleLockCommunicator();
        sampleServerManager = new SampleServerManager(getContext());

        adapter = new KeyItemAdapter(getActivity(), keyItemAdapterHandler);

        setListAdapter(adapter);

        // make sure, we have the ACCESS_FINE_LOCATION permission, which is required, to detect
        // BLE locks nearby.
        if (ContextCompat.checkSelfPermission(getContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            // Should we explain to the user, why we need this permission before actually requesting
            // it? This is the case, if the user rejected the permission before.
            if (shouldShowRequestPermissionRationale(android.Manifest.permission.ACCESS_FINE_LOCATION)) {
                showPermissionRationale();
            } else {
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
            bleObserverRegistration = bleLockScanner.getLocksChangedObservable().addObserver(stringBleLockMap -> adapter.notifyDataSetChanged());
        }

        if (ContextCompat.checkSelfPermission(getContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                // If we have the ACCESS_FINE_LOCATION permission, start scanning for BLE devices
                if (bleScanObserverRegistration == null) {
                    bleScanObserverRegistration = bleLockScanner.startForegroundScan();
                }
            } catch (Exception e) {
                Log.i(TAG, "Couldn't start scanning for nearby BLE locks.", e);
            }
        } else {
            if (shouldShowRequestPermissionRationale(android.Manifest.permission.ACCESS_FINE_LOCATION)) {
                showPermissionRationale();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        // Stop scanning for nearby BLE locks
        if (bleScanObserverRegistration != null) {
            bleScanObserverRegistration.close();
            bleScanObserverRegistration = null;
        }

        // Stop listening for nearby BLE locks
        if (bleObserverRegistration != null) {
            bleObserverRegistration.close();
            bleObserverRegistration = null;
        }

        // Stop listening for key updates
        if (keyUpdateObserverRegistration != null) {
            keyUpdateObserverRegistration.close();
            keyUpdateObserverRegistration = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST__ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (bleScanObserverRegistration == null) {
                        bleScanObserverRegistration = bleLockScanner.startForegroundScan();
                    }
                } else {

                    if (shouldShowRequestPermissionRationale(android.Manifest.permission.ACCESS_FINE_LOCATION)) {
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

    private void showPermissionRationale() {

        Snackbar.make(getView(), R.string.key_item__permission_needed, Snackbar.LENGTH_INDEFINITE)
                .setAction("ALLOW", view -> requestPermission()).show();
    }

    private void requestPermission() {
        requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSIONS_REQUEST__ACCESS_FINE_LOCATION);
    }

    private void onKeyUpdate(boolean forceUpdate) {

        if (userManager.getUsers().isEmpty()) {
            Log.e(TAG, "onKeyUpdate failed: no user is signed in.");
            return;
        }

        String userId = userManager.getUsers().get(0);

        // Retrieve local mobile keys
        keyManager.queryLocalKeysAsync(userId, CancellationTokens.None)

                // Query sample server for application grant information for the local keys
                .continueAsyncOnUi(keyDetails -> this.sampleServerManager.getGrants(
                        AuthStateManager.getUsername(getContext()),
                        AuthStateManager.getPassword(getContext()),
                        keyDetails.stream().map((KeyDetails::getGrantId)).toArray(String[]::new)
                )
                        // Map the resulting application grant information with the local keys
                        .continueOnUi(applicationGrants -> keyDetails
                                .stream()
                                .map(key -> {
                                    ApplicationGrantDto grant = applicationGrants
                                            .stream()
                                            .filter(x -> x.getId().equals(key.getGrantId()))
                                            .findAny()
                                            .orElse(null);
                                    return new Tuple<>(key, grant);
                                })
                                .collect(Collectors.toList())))

                // Add items to the list adapter
                .continueOnUi(listItems -> {
                    adapter.clear();
                    adapter.addAll(listItems);
                    return null;
                })

                // Handle async exceptions
                .catchOnUi(e -> {

                    Log.e(TAG, "Querying for local keys failed.", e);
                    // Handle error
                    return null;
                })

                // Ensure no exceptions are missed
                .conclude();

    }
}

package net.tpky.demoapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import com.tapkey.mobile.tlcp.commands.DefaultTriggerLockCommandBuilder;
import com.tapkey.mobile.utils.ObserverRegistration;
import com.tapkey.mobile.utils.Tuple;

import net.tpky.mc.tlcp.model.TriggerLockCommand;

import java.util.stream.Collectors;


public class KeyListFragment extends ListFragment {

    private final static String TAG = KeyListFragment.class.getSimpleName();

    private KeyManager keyManager;
    private CommandExecutionFacade commandExecutionFacade;
    private UserManager userManager;
    private BleLockScanner bleLockScanner;
    private BleLockCommunicator bleLockCommunicator;
    private SampleServerManager sampleServerManager;

    private ActivityResultLauncher<String[]> requestPermissionLauncher;

    private ArrayAdapter<Tuple<KeyDetails, ApplicationGrantDto>> adapter;

    private ObserverRegistration bleScanObserverRegistration;
    private ObserverRegistration bleObserverRegistration;
    private ObserverRegistration keyUpdateObserverRegistration;

    private static final String[] REQUIRED_PERMISSIONS;
    private static final Integer PERMISSION_RATIONALE_STRING_ID;

    static {

        if (Build.VERSION.SDK_INT >= 31) {
            REQUIRED_PERMISSIONS = new String[] { Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT };
            PERMISSION_RATIONALE_STRING_ID = R.string.key_item__permission_needed__ble_scan_connect;
        }

        else if (Build.VERSION.SDK_INT >= 29) {
            REQUIRED_PERMISSIONS = new String[] { Manifest.permission.ACCESS_FINE_LOCATION };
            PERMISSION_RATIONALE_STRING_ID = R.string.key_item__permission_needed__fine_location;
        }
        else if (Build.VERSION.SDK_INT >= 23) {
            REQUIRED_PERMISSIONS =  new String[] { Manifest.permission.ACCESS_COARSE_LOCATION };
            PERMISSION_RATIONALE_STRING_ID = R.string.key_item__permission_needed__coarse_location;
        } else
        {
            REQUIRED_PERMISSIONS = new String[0];
            PERMISSION_RATIONALE_STRING_ID = null;
        }
    }

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

                TriggerLockCommand triggerLockCommand = new DefaultTriggerLockCommandBuilder()
                        .build();

                return commandExecutionFacade.executeStandardCommandAsync(
                        tlcpConnection,
                        triggerLockCommand,
                        ct);

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
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

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

        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {

            boolean showRationale = false;
            boolean blocked = false;

            for (String permission : result.keySet()) {

                Boolean granted = result.get(permission);

                if (granted)
                    continue;

                boolean shouldShowRationale = shouldShowRequestPermissionRationale(permission);
                showRationale = showRationale || shouldShowRationale;
                blocked = blocked || !shouldShowRationale;
            }

            if (blocked) {
                Snackbar.make(getView(), PERMISSION_RATIONALE_STRING_ID, Snackbar.LENGTH_INDEFINITE)
                        .setAction("ALLOW", x -> {

                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", getActivity().getPackageName(), null));
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);

                        }).show();
            } else if (showRationale) {
                showPermissionRationale();
            } else {
                if (bleScanObserverRegistration == null) {
                    bleScanObserverRegistration = bleLockScanner.startForegroundScan();
                }
            }
        });

        if (!checkPermissions()) {

            // Should we explain to the user, why we need this permission before actually requesting
            // it? This is the case, if the user rejected the permission before.
            if (shouldShowRationale()) {
                showPermissionRationale();
            } else {
                requestPermissions();
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

        if (checkPermissions()) {
            try {
                // If we have the required permissions, start scanning for BLE devices
                if (bleScanObserverRegistration == null) {
                    bleScanObserverRegistration = bleLockScanner.startForegroundScan();
                }
            } catch (Exception e) {
                Log.i(TAG, "Couldn't start scanning for nearby BLE locks.", e);
            }
        } else {
            if (shouldShowRationale()) {
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

    private void showPermissionRationale() {
        Snackbar.make(getView(), PERMISSION_RATIONALE_STRING_ID, Snackbar.LENGTH_INDEFINITE)
                .setAction("ALLOW", view -> requestPermissions()).show();
    }

    private void requestPermissions() {
        requestPermissionLauncher
            .launch(REQUIRED_PERMISSIONS);
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

    private boolean shouldShowRationale() {
        for (String permission : REQUIRED_PERMISSIONS) {

            if(shouldShowRequestPermissionRationale(permission))
                return true;
        }
        return false;
    }

    private boolean checkPermissions() {

        for (String permission : REQUIRED_PERMISSIONS) {
            int permissionResult = ContextCompat.checkSelfPermission(getContext(), permission);

            if(permissionResult == PackageManager.PERMISSION_GRANTED)
                continue;

            return false;
        }
        return true;
    }
}

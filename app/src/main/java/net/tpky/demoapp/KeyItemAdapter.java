package net.tpky.demoapp;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatButton;

import com.tapkey.mobile.concurrent.Async;
import com.tapkey.mobile.concurrent.CancellationToken;
import com.tapkey.mobile.concurrent.CancellationTokenSource;
import com.tapkey.mobile.concurrent.Promise;
import com.tapkey.mobile.model.KeyDetails;
import com.tapkey.mobile.utils.Func1;

import net.tpky.mc.concurrent.CancellationUtils;

import java.util.Date;

public class KeyItemAdapter extends ArrayAdapter<KeyDetails> {

    private final static String TAG = KeyItemAdapter.class.getSimpleName();

    public interface KeyItemAdapterHandler {
        boolean isLockNearby(String physicalLockId);

        Promise<Boolean> triggerLock(String physicalLockId, CancellationToken ct);
    }

    private final KeyItemAdapterHandler handler;

    KeyItemAdapter(Context context, KeyItemAdapterHandler keyItemAdapterHandler) {
        super(context, R.layout.key_item);
        this.handler = keyItemAdapterHandler;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        LayoutInflater mInflater = (LayoutInflater) getContext().getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
        final View viewToUse = (convertView == null) ? mInflater.inflate(R.layout.key_item, null) : convertView;
        final TextView lockTitleTextView = viewToUse.findViewById(R.id.key_item__lock_title);
        final TextView issuerTextView = viewToUse.findViewById(R.id.key_item__issuer);
        final TextView accessRestrictionTextView = viewToUse.findViewById(R.id.key_item__access_restriction);
        final AppCompatButton triggerButton = viewToUse.findViewById(R.id.key_item__trigger_button);
        final AppCompatButton cancelButton = viewToUse.findViewById(R.id.key_item__cancel_trigger_button);

        final KeyDetails item = getItem(position);
        lockTitleTextView.setText(getTitle(item));
        issuerTextView.setText(getContext().getString(R.string.key_item__issued_by, getOwnerName(item)));

        Date validFrom = (item != null && item.getGrant() != null) ? item.getGrant().getValidFrom() : null;
        Date validBefore = (item != null && item.getGrant() != null) ? item.getGrant().getValidBefore() : null;
        boolean icalRestricted = (item != null && item.getGrant() != null) && item.getGrant().getTimeRestrictionIcal() != null;

        if (validBefore != null || validFrom != null || icalRestricted) {
            accessRestrictionTextView.setText(R.string.key_item__access_restricted);
        } else {
            accessRestrictionTextView.setText(R.string.key_item__access_unrestricted);
        }

        final String physicalLockId = ((item != null) && (item.getGrant() != null) && (item.getGrant().getBoundLock() != null)) ? item.getGrant().getBoundLock().getPhysicalLockId() : null;


        if (!handler.isLockNearby(physicalLockId)) {
            triggerButton.setVisibility(View.GONE);
            cancelButton.setVisibility(View.GONE);
            triggerButton.setOnClickListener(null);
            cancelButton.setOnClickListener(null);
        } else {
            triggerButton.setOnClickListener(view -> {

                CancellationTokenSource cts = new CancellationTokenSource();

                triggerButton.setEnabled(false);
                triggerButton.setVisibility(View.GONE);
                cancelButton.setVisibility(View.VISIBLE);
                cancelButton.setEnabled(true);

                cancelButton.setOnClickListener(ignore -> {
                    cts.requestCancellation();
                    cancelButton.setEnabled(false);
                });

                // asynchronously trigger an unlock command
                handler.triggerLock(physicalLockId, CancellationUtils.withTimeout(cts.getToken(), 15000))

                        // Catch errors and return false to indicate failure
                        .catchOnUi(e -> {
                            Log.e(TAG, "Triggering lock failed.", e);
                            return false;
                        })

                        // when done, continue on the UI thread
                        .continueOnUi((Func1<Boolean, Void, Exception>) success -> {

                            // when trigger lock was successfully set background color to green
                            // otherwise set background color to red
                            viewToUse.setBackgroundColor((success) ? Color.GREEN : cts.getToken().isCancellationRequested() ? Color.TRANSPARENT : Color.RED);

                            // enable button to allow another trigger
                            triggerButton.setEnabled(true);
                            triggerButton.setVisibility(View.VISIBLE);
                            cancelButton.setVisibility(View.GONE);

                            // reset background after a delay
                            Async.delayAsync(3000).continueOnUi((Func1<Void, Void, Exception>) aVoid -> {
                                viewToUse.setBackgroundColor(Color.TRANSPARENT);
                                return null;
                            });

                            return null;
                        })

                        // make sure, we don't miss any exceptions.
                        .conclude();
            });

            triggerButton.setVisibility(View.VISIBLE);
            triggerButton.setEnabled(true);
            cancelButton.setVisibility(View.GONE);
        }

        return viewToUse;
    }

    private String getTitle(KeyDetails cachedKeyInformation) {

        if (cachedKeyInformation == null || cachedKeyInformation.getGrant() == null || cachedKeyInformation.getGrant().getBoundLock() == null || cachedKeyInformation.getGrant().getBoundLock().getTitle() == null || cachedKeyInformation.getGrant().getBoundLock().getTitle().isEmpty()) {
            return getContext().getString(R.string.key_item__unknown_lock);
        }

        return cachedKeyInformation.getGrant().getBoundLock().getTitle();
    }

    private String getOwnerName(KeyDetails cachedKeyInformation) {
        if (cachedKeyInformation == null || cachedKeyInformation.getGrant() == null || cachedKeyInformation.getGrant().getOwner() == null || cachedKeyInformation.getGrant().getOwner().getName() == null || cachedKeyInformation.getGrant().getOwner().getName().isEmpty()) {
            return getContext().getString(R.string.key_item__unknown_owner);
        }


        return cachedKeyInformation.getGrant().getOwner().getName();
    }
}

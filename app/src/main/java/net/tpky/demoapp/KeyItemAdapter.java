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
import com.tapkey.mobile.concurrent.CancellationTokens;
import com.tapkey.mobile.concurrent.CancellationTokenSource;
import com.tapkey.mobile.concurrent.Promise;
import com.tapkey.mobile.model.KeyDetails;
import com.tapkey.mobile.utils.Func1;
import com.tapkey.mobile.utils.Tuple;

public class KeyItemAdapter extends ArrayAdapter<Tuple<KeyDetails, ApplicationGrantDto>> {

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
        final TextView locationTextView = viewToUse.findViewById(R.id.key_item__location);
        final TextView granteeTextView = viewToUse.findViewById(R.id.key_item__grantee);
        final TextView accessRestrictionTextView = viewToUse.findViewById(R.id.key_item__access_restriction);
        final AppCompatButton triggerButton = viewToUse.findViewById(R.id.key_item__trigger_button);
        final AppCompatButton cancelButton = viewToUse.findViewById(R.id.key_item__cancel_trigger_button);

        final Tuple<KeyDetails, ApplicationGrantDto> item = getItem(position);
        assert item != null;
        final ApplicationGrantDto grant = item.getValue2();
        final KeyDetails key = item.getValue1();
        assert grant != null;
        assert key != null;

        if (grant.getLockTitle() != null) {
            lockTitleTextView.setText(grant.getLockTitle());
        } else {
            lockTitleTextView.setText(getContext().getString(R.string.key_item__unknown_lock));
        }

        if (grant.getIssuer() != null) {
            issuerTextView.setText(getContext().getString(R.string.key_item__issued_by, grant.getIssuer()));
        } else {
            issuerTextView.setText(getContext().getString(R.string.key_item__unknown_issuer));
        }

        if (grant.getLockLocation() != null) {
            locationTextView.setText(getContext().getString(R.string.key_item__location, grant.getLockLocation()));
        } else {
            locationTextView.setText(getContext().getString(R.string.key_item__unknown_location));
        }

        if (grant.getValidFrom() != null || grant.getValidBefore() != null || grant.getTimeRestrictionIcal() != null) {
            accessRestrictionTextView.setText(R.string.key_item__access_restricted);
        } else {
            accessRestrictionTextView.setText(R.string.key_item__access_unrestricted);
        }

        if (grant.getGranteeFirstName() != null || grant.getGranteeLastName() != null) {
            granteeTextView.setText(getContext().getString(
                    R.string.key_item_grantee,
                    String.format(
                            "%1$s %2$s",
                            grant.getGranteeFirstName(),
                            grant.getGranteeLastName())
            ));
        }

        if (!handler.isLockNearby(grant.getPhysicalLockId())) {
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
                handler.triggerLock(grant.getPhysicalLockId(), CancellationTokens.withTimeout(cts.getToken(), 15000))

                        // Catch errors and return false to indicate failure
                        .catchOnUi(e -> {
                            Log.e(TAG, "Triggering lock failed.", e);
                            return false;
                        })

                        // when done, continue on the UI thread
                        .continueOnUi((Func1<Boolean, Void, Exception>) success -> {

                            // When trigger lock was successfully set background color to green
                            // otherwise set background color to red
                            viewToUse.setBackgroundColor((success) ? getContext().getColor(R.color.success) : cts.getToken().isCancellationRequested() ? Color.TRANSPARENT : getContext().getColor(R.color.error));

                            // Trigger lock completed, cancelling is not possible anymore.
                            cancelButton.setVisibility(View.GONE);

                            // Reset background after a delay
                            Async.delayAsync(3000).continueOnUi((Func1<Void, Void, Exception>) aVoid -> {
                                viewToUse.setBackgroundColor(Color.TRANSPARENT);
                                // enable button to allow another trigger
                                triggerButton.setEnabled(true);
                                triggerButton.setVisibility(View.VISIBLE);
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
}

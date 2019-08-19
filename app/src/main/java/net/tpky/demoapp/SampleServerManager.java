package net.tpky.demoapp;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.tapkey.mobile.concurrent.Promise;
import com.tapkey.mobile.concurrent.PromiseSource;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

class SampleServerManager {

    private DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US);

    private final Uri.Builder uriBuilder;
    private final RequestQueue queue;

    SampleServerManager(Context context) {
        this.queue = Volley.newRequestQueue(context);
        uriBuilder = new Uri.Builder();
        uriBuilder.scheme(context.getString(R.string.sample_backend_scheme));
        uriBuilder.encodedAuthority(context.getString(R.string.sample_backend_authority));
    }

    Promise<String> registerUser(String username, String password, String firstName, String lastName) {
        PromiseSource<String> res = new PromiseSource<>();
        uriBuilder.path("user");

        try {
            JSONObject jsonBody = new JSONObject();
            jsonBody.put("username", username);
            jsonBody.put("password", password);
            jsonBody.put("firstName", firstName);
            jsonBody.put("lastName", lastName);

            // Request a string response from the provided URL
            JsonObjectRequest stringRequest = new JsonObjectRequest(
                    Request.Method.POST,
                    uriBuilder.build().toString(),
                    jsonBody,
                    response -> {
                        try {
                            res.setResult(response.getString("id"));
                        } catch (JSONException e) {
                            res.setException(e);
                        }
                    },
                    error -> res.setException(new Exception(error.getCause())));

            // Add the request to the RequestQueue
            queue.add(stringRequest);
        } catch (JSONException e) {
            res.setException(e);
        }

        return res.getPromise();
    }

    Promise<String> getExternalToken(String username, String password) {
        PromiseSource<String> res = new PromiseSource<>();
        uriBuilder.path("user").appendPath("tapkey-token");

        // Request a Token for exchange with Tapkey using Basic Authentication
        JsonObjectRequest stringRequest = new JsonObjectRequest(
                Request.Method.GET,
                uriBuilder.build().toString(),
                null,
                response -> {
                    try {
                        res.setResult(response.getString("externalToken"));
                    } catch (JSONException e) {
                        res.setException(e);
                    }
                },
                error -> res.setException(new Exception(error.getCause()))) {

            @Override
            public Map<String, String> getHeaders() {
                HashMap<String, String> headers = new HashMap<>();
                headers.put(
                        "Authorization",
                        "Basic " + Base64.encodeToString(
                                (username + ":" + password).getBytes(),
                                Base64.NO_WRAP)
                );
                return headers;
            }

        };

        queue.add(stringRequest);
        return res.getPromise();
    }

    Promise<List<ApplicationGrantDto>> getGrants(String username, String password, String[] grantIds) {
        PromiseSource<List<ApplicationGrantDto>> res = new PromiseSource<>();
        uriBuilder
                .path("user")
                .appendPath("grants")
                .appendQueryParameter("grantIds", String.join(",", grantIds));

        // Request a Token for exchange with Tapkey using Basic Authentication
        JsonArrayRequest stringRequest = new JsonArrayRequest(
                Request.Method.GET,
                uriBuilder.build().toString(),
                null,
                response -> {
                    try {
                        // Deserialize application-specific grant information
                        List<ApplicationGrantDto> grants = new ArrayList<>(response.length());
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject obj = response.getJSONObject(i);
                            ApplicationGrantDto appGrant = new ApplicationGrantDto();
                            appGrant.setGranteeFirstName(obj.optString("granteeFirstName", null));
                            appGrant.setGranteeLastName(obj.optString("granteeLastName", null));
                            appGrant.setIssuer(obj.optString("issuer", null));
                            appGrant.setLockTitle(obj.optString("lockTitle", null));
                            appGrant.setLockLocation(obj.optString("lockLocation", null));
                            appGrant.setId(obj.getString("id"));
                            appGrant.setPhysicalLockId(obj.getString("physicalLockId"));
                            appGrant.setState(obj.getString("state"));
                            appGrant.setTimeRestrictionIcal(obj.optString("timeRestrictionIcal", null));
                            if (obj.has("validFrom")) {
                                appGrant.setValidFrom(dateFormat.parse(obj.getString("validFrom")));
                            }
                            if (obj.has("validBefore")) {
                                appGrant.setValidBefore(dateFormat.parse(obj.getString("validBefore")));
                            }
                            grants.add(appGrant);
                        }
                        res.setResult(grants);
                    } catch (JSONException e) {
                        res.setException(e);
                    } catch (ParseException e) {
                        res.setException(e);
                    }
                },
                error -> res.setException(new Exception(error.getCause()))) {

            @Override
            public Map<String, String> getHeaders() {
                HashMap<String, String> headers = new HashMap<>();
                headers.put(
                        "Authorization",
                        "Basic " + Base64.encodeToString(
                                (username + ":" + password).getBytes(),
                                Base64.NO_WRAP)
                );
                return headers;
            }

        };

        queue.add(stringRequest);
        return res.getPromise();
    }
}

package net.tpky.demoapp;

import android.content.res.AssetManager;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Locale;

public class AboutActivity extends AppCompatActivity {

    private static final String TAG = AboutActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        int versionCode = BuildConfig.VERSION_CODE;
        String versionName = BuildConfig.VERSION_NAME;

        TextView appVersionName = findViewById(R.id.about__app_version_name);
        TextView appVersionCode = findViewById(R.id.about__app_version_code);

        appVersionName.setText(versionName);
        appVersionCode.setText(String.format(Locale.US, "%1$d", versionCode));
    }


    public void onClickShowThirdPartyLicences(View v){

        AssetManager assetManager = getAssets();
        InputStream fIn = null;
        InputStreamReader isr = null;
        BufferedReader input = null;

        try {

            StringBuilder returnString = new StringBuilder();
            fIn = assetManager.open("third_party_licenses.txt");
            isr = new InputStreamReader(fIn);
            input = new BufferedReader(isr);

            String line;
            while ((line = input.readLine()) != null) {
                returnString.append(line);
                returnString.append("\n");
            }

            String data = returnString.toString();

            new AlertDialog.Builder(this)
                    .setTitle("Third Party Licenses")
                    .setMessage(data)
                    .setNeutralButton("OK", null)
                    .show();

        } catch (IOException e) {

            Log.e(TAG, "Failed to read third party licences");

        } finally {


            try {
                if(fIn != null) fIn.close();
            } catch (IOException ignore) { }

            try {
                if(isr != null) isr.close();
            } catch (IOException ignore) { }

            try {
                if(input != null) input.close();
            } catch (IOException ignore) { }

        }

    }
}

package net.tpky.demoapp;

import android.app.Application;

import com.tapkey.mobile.TapkeyAppContext;
import com.tapkey.mobile.TapkeyServiceFactory;
import com.tapkey.mobile.TapkeyServiceFactoryBuilder;
import com.tapkey.mobile.broadcast.PollingScheduler;

/*
 * Tapkey expects the Application instance to implement the TapkeyAppContext interface.
 */
public class App extends Application implements TapkeyAppContext {

    /*
     * The TapkeyServiceFactory holds all needed services
     */
    private TapkeyServiceFactory tapkeyServiceFactory;

    @Override
    public void onCreate() {
        super.onCreate();

        /*
         * Create an instance of TapkeyServiceFactory. Tapkey expects that a single instance of
         * TapkeyServiceFactory exists inside an application that can be retrieved via the
         * Application instance's getTapkeyServiceFactory() method.
         */
        TapkeyServiceFactoryBuilder b = new TapkeyServiceFactoryBuilder(this);
        b.setTokenRefreshHandler(new SampleTokenRefreshHandler(this));
        this.tapkeyServiceFactory = b.build();

        /*
         * Register PushNotificationReceiver
         *
         * The PushNotificationReceiver polls for notifications from the Tapkey backend.
         *
         * The JobId must a unique id across the whole app
         *
         * The default interval is 8 hours and can be changed fitting requirements of the provided service
         *
         */
        PollingScheduler.register(this, 1, PollingScheduler.DEFAULT_INTERVAL);
    }

    @Override
    public TapkeyServiceFactory getTapkeyServiceFactory() {
        return tapkeyServiceFactory;
    }
}

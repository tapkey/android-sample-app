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

import android.app.Application;

import net.tpky.mc.AndroidTapkeyServiceFactory;
import net.tpky.mc.TapkeyAppContext;
import net.tpky.mc.TapkeyServiceFactoryBuilder;
import net.tpky.mc.auth.OAuthRefreshableFlow;
import net.tpky.mc.broadcast.PushNotificationReceiver;
import net.tpky.mc.concurrent.AsyncSchedulerUtils;
import net.tpky.mc.concurrent.AsyncSchedulers;
import net.tpky.mc.concurrent.AsyncStackTrace;

/*
 * Tapkey expects the Application instance to implement the TapkeyAppContext interface.
 */
public class App extends Application implements TapkeyAppContext{

    static {

        /*
         * Enable generation of meaningful stack traces in async code.
         * This may have performance impact, even if no exceptions are raised but significantly
         * easens debugging. The performance overhead is usually neglictable on newer devices.
         */
        AsyncStackTrace.enableAsyncStateTrace();

        /*
         * Enable logging of async scheduler statistics. This can help identifying issues that
         * cause the UI to get unresponsive. This usually wouldn't be active in production
         * scenarios but can help during development or error analysis.
         * The batch size and max dump interval define the frequency of log outputs.
         */
        AsyncSchedulers.setSchedulerInterceptor((x, name) -> AsyncSchedulerUtils.applyLogging(x, name, 500, 10000));
    }

    /*
     * The TapkeyServiceFactory holds all needed services
     */
    private AndroidTapkeyServiceFactory tapkeyServiceFactory;
    private OAuthRefreshableFlow oAuthFlow;

    @Override
    public void onCreate() {
        super.onCreate();


        /*
         * Create an instance of TapkeyServiceFactory. Tapkey expects that a single instance of
         * TapkeyServiceFactory exists inside an application that can be retrieved via the
         * Application instance's getTapkeyServiceFactory() method.
         */
        TapkeyServiceFactoryBuilder b =
                new TapkeyServiceFactoryBuilder()

        /*
         * Optionally, settings like the backend URI and tenant ID can be changed.
         */

                // Change the backend URI if required.
                //.setServiceBaseUri("https://example.com")

                // Change the tenant if required.
                //.setTenantId("someTenant")

                // Change the SSLContext if required to implement certificate pinning, etc.
                //.setSSLContext(SSLContext.getDefault())
        ;

        // build the TapkeyServiceFactory instance.
        this.tapkeyServiceFactory = b.build(this);

        try {
            String clientId = getResources().getString(R.string.oauth_client_id);
            String serverUri = getResources().getString(R.string.oauth_authorization_server);
            this.oAuthFlow = new OAuthRefreshableFlow(tapkeyServiceFactory.getRestExecutor(), serverUri, clientId);
            this.oAuthFlow.link(tapkeyServiceFactory.getLogonManager());

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

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
        PushNotificationReceiver.register(this, tapkeyServiceFactory.getServerClock(), 1, PushNotificationReceiver.DEFAULT_INTERVAL);

    }

    @Override
    public AndroidTapkeyServiceFactory getTapkeyServiceFactory() {
        return tapkeyServiceFactory;
    }

    public OAuthRefreshableFlow getoAuthFlow() {
        return oAuthFlow;
    }
}

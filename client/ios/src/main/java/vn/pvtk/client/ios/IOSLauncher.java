package vn.pvtk.client.ios;

import com.badlogic.gdx.backends.iosrobovm.IOSApplication;
import com.badlogic.gdx.backends.iosrobovm.IOSApplicationConfiguration;
import org.robovm.apple.foundation.NSAutoreleasePool;
import org.robovm.apple.uikit.UIApplication;
import vn.pvtk.client.gdx.PvtkConfig;
import vn.pvtk.client.gdx.PvtkGame;

/**
 * iOS launcher (RoboVM). Hosts the shared {@link PvtkGame} via the libGDX iOS
 * backend. Build/run on macOS with the RoboVM toolchain:
 *
 * <pre>
 *   ./gradlew :client-ios:launchIPhoneSimulator
 *   ./gradlew :client-ios:createIPA
 * </pre>
 */
public final class IOSLauncher extends IOSApplication.Delegate {

    @Override
    protected IOSApplication createApplication() {
        IOSApplicationConfiguration config = new IOSApplicationConfiguration();
        config.useAccelerometer = false;
        PvtkConfig cfg = new PvtkConfig();
        cfg.username = "iOS-" + (System.nanoTime() % 1000);
        return new IOSApplication(new PvtkGame(cfg), config);
    }

    public static void main(String[] argv) {
        NSAutoreleasePool pool = new NSAutoreleasePool();
        UIApplication.main(argv, null, IOSLauncher.class);
        pool.close();
    }
}

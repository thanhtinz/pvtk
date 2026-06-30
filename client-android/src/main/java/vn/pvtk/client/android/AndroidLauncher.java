package vn.pvtk.client.android;

import android.os.Bundle;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import vn.pvtk.client.gdx.PvtkConfig;
import vn.pvtk.client.gdx.PvtkGame;

/**
 * Android launcher. Hosts the shared {@link PvtkGame} via the libGDX Android
 * backend. The server host can be overridden through the launch intent extras
 * ({@code pvtk_host}, {@code pvtk_port}, {@code pvtk_user}).
 */
public final class AndroidLauncher extends AndroidApplication {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PvtkConfig cfg = new PvtkConfig();
        if (getIntent() != null) {
            String host = getIntent().getStringExtra("pvtk_host");
            if (host != null) cfg.host = host;
            cfg.port = getIntent().getIntExtra("pvtk_port", cfg.port);
            String user = getIntent().getStringExtra("pvtk_user");
            cfg.username = user != null ? user : "Android-" + (System.nanoTime() % 1000);
        }

        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.useImmersiveMode = true;
        initialize(new PvtkGame(cfg), config);
    }
}

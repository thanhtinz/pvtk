package vn.pvtk.client.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import java.io.File;

/**
 * Resolves original game assets ({@code assets/common/1.png}, {@code assets/map/…})
 * at runtime. The libGDX {@code internal} root is the process working directory,
 * but the art lives under an {@code assets/} folder — so this walks up from the
 * working directory (or honours {@code PVTK_ASSETS}) to find it, mirroring the
 * server's {@code GameData.resolveAssetsRoot()}. Keeps the client working no
 * matter where it is launched from (Gradle run, installDist, IDE).
 */
final class Assets {

    private Assets() { }

    /**
     * A file handle for {@code <assets-root>/rel}. Falls back to an internal
     * handle (working-directory relative) if no assets root can be located, so
     * callers can still {@code exists()}-check and degrade gracefully.
     */
    static FileHandle resolve(String rel) {
        String env = System.getenv("PVTK_ASSETS");
        if (env != null && !env.isBlank()) {
            File f = new File(env, rel);
            if (f.exists()) {
                return Gdx.files.absolute(f.getAbsolutePath());
            }
        }
        File dir = new File(System.getProperty("user.dir", ".")).getAbsoluteFile();
        for (int up = 0; up < 8 && dir != null; up++, dir = dir.getParentFile()) {
            File f = new File(dir, "assets/" + rel);
            if (f.exists()) {
                return Gdx.files.absolute(f.getAbsolutePath());
            }
        }
        return Gdx.files.internal(rel);
    }
}

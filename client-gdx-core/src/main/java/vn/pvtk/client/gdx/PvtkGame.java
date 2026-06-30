package vn.pvtk.client.gdx;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector3;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import vn.pvtk.client.GameClient;
import vn.pvtk.client.GameClientListener;
import vn.pvtk.client.model.Entity;
import vn.pvtk.protocol.message.Messages.ChatBroadcast;

/**
 * The shared libGDX game, identical on PC, Android and iOS. It renders the world
 * snapshot held in {@link vn.pvtk.client.model.GameState}, lets the player tap /
 * click a tile to move, and shows a rolling chat log. All networking lives in the
 * platform-neutral {@link GameClient}; this class only deals with rendering and
 * input, marshalling network callbacks onto the render thread.
 */
public final class PvtkGame extends ApplicationAdapter {

    private final PvtkConfig config;

    private OrthographicCamera camera;
    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private BitmapFont font;

    private GameClient client;
    private final Deque<String> chatLog = new ArrayDeque<>();
    private volatile String status = "connecting...";

    public PvtkGame(PvtkConfig config) {
        this.config = config;
    }

    @Override
    public void create() {
        camera = new OrthographicCamera();
        camera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        font = new BitmapFont();

        client = new GameClient(new RenderThreadListener());
        Gdx.input.setInputProcessor(new TapToMove());

        new Thread(() -> {
            try {
                client.connect(config.host, config.port);
                client.login(config.username, "", 0);
            } catch (IOException e) {
                status = "connect failed: " + e.getMessage();
            }
        }, "pvtk-connect").start();
    }

    @Override
    public void render() {
        camera.update();
        Gdx.gl.glClearColor(0.07f, 0.08f, 0.10f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        int ts = config.tileSize;

        // --- grid ---
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(0.15f, 0.16f, 0.20f, 1f);
        for (int x = 0; x <= Gdx.graphics.getWidth(); x += ts) {
            shapes.line(x, 0, x, Gdx.graphics.getHeight());
        }
        for (int y = 0; y <= Gdx.graphics.getHeight(); y += ts) {
            shapes.line(0, y, Gdx.graphics.getWidth(), y);
        }
        shapes.end();

        // --- entities ---
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Entity e : client.state().others()) {
            drawEntity(e, Color.SKY, ts);
        }
        Entity self = client.state().self();
        if (self != null) {
            drawEntity(self, Color.GOLD, ts);
        }
        shapes.end();

        // --- HUD: names, status, chat ---
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        font.setColor(Color.WHITE);
        for (Entity e : client.state().others()) {
            font.draw(batch, e.name, e.x * ts, e.y * ts + ts + 12);
        }
        if (self != null) {
            font.setColor(Color.GOLD);
            font.draw(batch, self.name + " (you)", self.x * ts, self.y * ts + ts + 12);
        }
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch, "PVTK  " + status, 8, Gdx.graphics.getHeight() - 8);

        int y = 8 + (chatLog.size() * 16);
        for (String msg : chatLog) {
            font.draw(batch, msg, 8, y);
            y -= 16;
        }
        batch.end();
    }

    private void drawEntity(Entity e, Color color, int ts) {
        shapes.setColor(color);
        shapes.rect(e.x * ts + 3, e.y * ts + 3, ts - 6, ts - 6);
    }

    private void pushChat(String line) {
        chatLog.addFirst(line);
        while (chatLog.size() > 6) {
            chatLog.removeLast();
        }
    }

    @Override
    public void resize(int width, int height) {
        camera.setToOrtho(false, width, height);
    }

    @Override
    public void dispose() {
        if (client != null) {
            client.disconnect();
        }
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }

    /** Marshals network callbacks onto libGDX's render thread. */
    private final class RenderThreadListener implements GameClientListener {
        @Override public void onConnected() {
            Gdx.app.postRunnable(() -> status = "connected");
        }
        @Override public void onLoginResult(boolean ok, String message) {
            Gdx.app.postRunnable(() -> status = ok ? "in game" : ("login failed: " + message));
        }
        @Override public void onChat(ChatBroadcast chat) {
            Gdx.app.postRunnable(() -> pushChat(chat.fromName() + ": " + chat.text()));
        }
        @Override public void onDisconnected(String reason) {
            Gdx.app.postRunnable(() -> status = "disconnected: " + reason);
        }
    }

    /** Converts a tap/click to a tile coordinate and sends a move request. */
    private final class TapToMove extends InputAdapter {
        @Override public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            Vector3 world = camera.unproject(new Vector3(screenX, screenY, 0));
            int tx = (int) (world.x / config.tileSize);
            int ty = (int) (world.y / config.tileSize);
            client.move(tx, ty, 0);
            return true;
        }
    }
}

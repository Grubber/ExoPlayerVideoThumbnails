package pw.zengzhihao.exoplayervideothumbnails;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.ui.DefaultTimeBar;
import com.google.android.exoplayer2.ui.SimpleExoPlayerView;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheDataSourceFactory;
import com.google.android.exoplayer2.upstream.cache.CacheEvictor;
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.google.android.exoplayer2.util.Util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Created by grubber on 18/07/2017.
 */
public class PlayerActivity extends AppCompatActivity implements View.OnTouchListener {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        SimpleExoPlayerView playerView = (SimpleExoPlayerView) findViewById(R.id.playerView);
        DefaultTimeBar timeBar = playerView.findViewById(R.id.exo_progress);
        positionView = playerView.findViewById(R.id.exo_position);
        thumbnailView = (ImageView) findViewById(R.id.thumbnailView);

        // get thumbnail bitmap first from local or remote source
        getThumbnailBitmap();

        // init player
        RenderersFactory renderersFactory = new DefaultRenderersFactory(this);
        LoadControl loadControl = new DefaultLoadControl();
        TrackSelector trackSelector = new DefaultTrackSelector();
        player = ExoPlayerFactory.newSimpleInstance(renderersFactory,
                trackSelector, loadControl);

        // init media source
        CacheEvictor evictor = new LeastRecentlyUsedCacheEvictor(50 * 1024 * 1024);
        final Cache cache = new SimpleCache(new File(getExternalCacheDir(), "exo_cache"), evictor);
        HttpDataSource.Factory upstreamFactory = new DefaultHttpDataSourceFactory(
                Util.getUserAgent(this, getString(R.string.app_name)));
        CacheDataSourceFactory dataSourceFactory = new CacheDataSourceFactory(cache,
                upstreamFactory,
                CacheDataSource.FLAG_BLOCK_ON_CACHE | CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
        MediaSource mediaSource = new HlsMediaSource(Uri.parse(VIDEO_URL), dataSourceFactory, null, null);

        // prepare to play
        player.prepare(mediaSource);
        playerView.setPlayer(player);
        timeBar.setOnTouchListener(this);
    }

    private ImageView thumbnailView;
    private TextView positionView;

    private SimpleExoPlayer player;

    private static final String VIDEO_URL = "https://www.zengzhihao.pw/videos/girl/index.m3u8";
    private static final String THUMBNAIL_URL = "https://www.zengzhihao.pw/images/girl/index.webp";

    private Bitmap thumbnailBitmap;

    private static final int DEFAULT_THUMBNAIL_TIME_INTERVAL = 3;
    private static final int DEFAULT_THUMBNAIL_COLUMN_COUNT = 6;
    private static final int DEFAULT_THUMBNAIL_WIDTH = 200;
    private static final int DEFAULT_THUMBNAIL_HEIGHT = 150;

    private boolean isLoadingThumbnailBitmap = false;

    private void getThumbnailBitmap() {
        final File bitmapFile = new File(getExternalCacheDir(), "thumbnailBitmap");
        if (bitmapFile.exists()) {
            thumbnailBitmap = BitmapFactory.decodeFile(bitmapFile.getPath());
            return;
        }

        if (isLoadingThumbnailBitmap) return;
        isLoadingThumbnailBitmap = true;

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(THUMBNAIL_URL).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                isLoadingThumbnailBitmap = false;
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                isLoadingThumbnailBitmap = false;
                if (response.isSuccessful()) {
                    ResponseBody body = response.body();
                    if (body != null) {
                        FileOutputStream fos = new FileOutputStream(bitmapFile);
                        InputStream is = body.byteStream();
                        byte[] buffer = new byte[1024 * 4];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                        fos.close();
                        is.close();
                        thumbnailBitmap = BitmapFactory.decodeFile(bitmapFile.getPath());
                    }
                }
            }
        });
    }

    private Bitmap getBitmapAtFrame(long timeMs) {
        if (thumbnailBitmap == null) {
            getThumbnailBitmap();
            return null;
        } else {
            long position = timeMs / DEFAULT_THUMBNAIL_TIME_INTERVAL;
            long x = position % DEFAULT_THUMBNAIL_COLUMN_COUNT * DEFAULT_THUMBNAIL_WIDTH;
            long y = position / DEFAULT_THUMBNAIL_COLUMN_COUNT * DEFAULT_THUMBNAIL_HEIGHT;
            Bitmap source = Bitmap.createBitmap(thumbnailBitmap,
                    (int) x, (int) y,
                    DEFAULT_THUMBNAIL_WIDTH, DEFAULT_THUMBNAIL_HEIGHT);
            Bitmap target = Bitmap.createScaledBitmap(source,
                    dpToPx(source.getWidth()), dpToPx(source.getHeight()), false);
            source.recycle();
            return target;
        }
    }

    private int dpToPx(int dps) {
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        return (int) (dps * displayMetrics.density + 0.5f);
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        if (view.getId() == R.id.exo_progress) {
            switch (motionEvent.getAction()) {
                case MotionEvent.ACTION_MOVE:
                    long position = getTimeFromString(positionView.getText().toString());
                    Bitmap bitmap = getBitmapAtFrame(position);
                    if (bitmap != null) {
                        thumbnailView.setImageBitmap(bitmap);
                        thumbnailView.setVisibility(View.VISIBLE);
                    } else {
                        thumbnailView.setVisibility(View.GONE);
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    thumbnailView.setVisibility(View.GONE);
                    break;

                default:
                    break;
            }
        }
        return false;
    }

    private long getTimeFromString(String position) {
        String[] s = position.split(":");
        if (s.length == 2) {
            return Long.valueOf(s[0]) * 60 + Long.valueOf(s[1]);
        } else {
            return Long.valueOf(s[0]) * 3600 + Long.valueOf(s[1]) * 60 + Long.valueOf(s[2]);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (player != null) {
            player.setPlayWhenReady(true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (player != null) {
            player.setPlayWhenReady(false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (player != null) {
            player.release();
        }
        if (thumbnailBitmap != null) {
            thumbnailBitmap.recycle();
        }
    }
}

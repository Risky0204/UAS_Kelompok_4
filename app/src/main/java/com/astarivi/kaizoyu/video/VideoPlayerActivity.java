package com.astarivi.kaizoyu.video;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GestureDetectorCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;

import com.astarivi.kaizoyu.R;
import com.astarivi.kaizoyu.core.analytics.AnalyticsClient;
import com.astarivi.kaizoyu.core.models.Result;
import com.astarivi.kaizoyu.core.theme.AppCompatActivityTheme;
import com.astarivi.kaizoyu.databinding.ActivityVideoPlayerBinding;
import com.astarivi.kaizoyu.video.gui.PlayerSkipView;
import com.astarivi.kaizoyu.video.utils.AnimeEpisodeManager;
import com.astarivi.kaizoyu.video.utils.BundleUtils;
import com.google.android.material.snackbar.Snackbar;

import org.videolan.libvlc.MediaPlayer;

import io.github.tonnyl.spark.Spark;


public class VideoPlayerActivity extends AppCompatActivityTheme {
    private ActivityVideoPlayerBinding binding;
    private VideoPlayerViewModel viewModel;
    private GestureDetectorCompat gestureDetector;
    private Spark spark;
    private AnimeEpisodeManager animeEpisodeManager = null;
    private boolean isPlaying = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityVideoPlayerBinding.inflate(this.getLayoutInflater());
        setContentView(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(VideoPlayerViewModel.class);

        // Bundle verification
        final Bundle bundle = getIntent().getExtras();

        if (bundle == null) return;

        final Result result = BundleUtils.getResultFromBundle(bundle);
        if (result == null) {
            finish();
            return;
        }

        // If we're operating in basic mode, decode from bundle.
        try {
            animeEpisodeManager = new AnimeEpisodeManager.Builder(bundle)
                    .build();
        } catch (IllegalArgumentException illegalArgumentException) {
            // We don't have enough data to operate
            if (!bundle.getBoolean("isAdvancedMode", false)) {
                finish();
                return;
            }
        }

        // Hide stuff
        binding.mainPlayer.setVisibility(View.INVISIBLE);

        // Prepare progress bar
        binding.initialDownloadProgress.setIndeterminate(false);
        binding.initialDownloadProgress.setProgress(0);
        binding.initialDownloadProgress.setMax(10);

        // Everything else
        binding.downloadStatus.setText(getResources().getString(R.string.executing_irc_handshake));
        gestureDetector = new GestureDetectorCompat(this, new PlayerGestureListener());

        binding.mainPlayer.initialize(
                animeEpisodeManager != null ? animeEpisodeManager.getAnimeTitle() : result.getCleanedFilename(),
                animeEpisodeManager != null ? animeEpisodeManager.getEpisodeTitle(this) : getString(R.string.advanced_mode_title),
                this::finish
        );

        // Fullscreen setup

        // Draw behind screen cutout
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P){
            final WindowManager.LayoutParams wManager = getWindow().getAttributes();
            wManager.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        // Draw behind system bars (edge to edge)
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false); // This doesn't seem to do the trick by itself
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        );

        // Show system bars only when swiping down, and hide them again automatically
        WindowInsetsControllerCompat windowInsetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        windowInsetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        );

        // Actually hide the goddamn bars.
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());

        spark = new Spark.Builder()
                .setView(binding.getRoot())
                .setDuration(4000)
                .setAnimList(Spark.ANIM_BLUE_PURPLE)
                .build();

        // Handle errors
        viewModel.getIrcFailure().observe(this, failureCode -> {
            String message;

            switch (failureCode) {
                case NoQuickRetry:
                    message = "You have been rate-limited by the server. Please wait some minutes.";
                    break;
                case TimeOut:
                    message = "Connection to IRC has timed out. Check your internet connection and retry later.";
                    break;
                case StrictModeFailure:
                    message = "Strict mode has stopped this connection to protect your privacy.";
                    break;
                default:
                    message = "There was a general I/O exception. Check your internet connection, and/or app permissions.";
                    break;
            }

            AnalyticsClient.onError("irc_failure", result.getContents(), failureCode.name());

            Snackbar.make(
                    binding.getRoot(),
                    message,
                    Snackbar.LENGTH_LONG
            ).show();

            delayedExit();
        });

        viewModel.getXdccFailure().observe(this, xdccFailure -> {
            String message;

            switch (xdccFailure) {
                case ConnectionLost:
                    message = "Connection to remote server lost, buffering has stopped.";
                    break;
                case TimedOut:
                    message = "Remote server didn't respond, please try another bot.";
                    delayedExit();
                    break;
                case UnknownHost:
                    message = "Couldn't find the remote host, please try another bot.";
                    delayedExit();
                    break;
                default:
                    message = "There was a general I/O exception. Check your internet connection, and/or app permissions.";
                    delayedExit();
                    break;
            }

            AnalyticsClient.onError("xdcc_failure", result.getContents(), xdccFailure.name());

            Snackbar.make(
                    binding.videoBackground,
                    message,
                    Snackbar.LENGTH_LONG
            ).show();
        });

        // Handle state
        viewModel.getProgress().observe(this, progressSpeedPair -> {
            if (isPlaying){
                binding.mainPlayer.setCacheProgress(progressSpeedPair.first);
                binding.mainPlayer.setDownloadSpeed(progressSpeedPair.second);
                return;
            }

            binding.initialDownloadProgress.setProgress(progressSpeedPair.first);
            binding.downloadSpeed.setText(progressSpeedPair.second);
            binding.downloadStatus.setText(getResources().getString(R.string.downloading_buffer));
        });

        viewModel.getDownloadFile().observe(this, file -> {
            if (isDestroyed()) return;

            if (spark != null) spark.stopAnimation();
            binding.getRoot().setBackgroundColor(Color.BLACK);
            binding.initialDownloadProgress.setVisibility(View.GONE);
            binding.downloadSpeed.setVisibility(View.GONE);
            binding.downloadStatus.setVisibility(View.GONE);

            binding.mainPlayer.play(file);
            binding.mainPlayer.setVisibility(View.VISIBLE);
            isPlaying = true;
        });

        viewModel.startDownload(this, result);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isPlaying) spark.startAnimation();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!isPlaying) spark.stopAnimation();

        MediaPlayer mMediaPlayer = binding.mainPlayer.getMediaPlayer();
        if (mMediaPlayer != null) {
            mMediaPlayer.pause();
            if (isPlaying && animeEpisodeManager != null && mMediaPlayer.getTime() != -1) {
                animeEpisodeManager.saveProgress(
                        (int) mMediaPlayer.getTime(),
                        (int) mMediaPlayer.getLength()
                );
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isPlaying) spark.stopAnimation();
        if (binding.mainPlayer.getMediaPlayer() != null) {
            binding.mainPlayer.getMediaPlayer().pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding.mainPlayer.destroy();
        viewModel.destroy();
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (gestureDetector.onTouchEvent(event)) {
            return true;
        }

        return super.onTouchEvent(event);
    }

    private void delayedExit(){
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(this::finish, 5000);
    }

    public class PlayerGestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onSingleTapConfirmed(@NonNull MotionEvent event) {
            if (!isPlaying) return true;
            binding.mainPlayer.showPlayerBar();

            return super.onSingleTapConfirmed(event);
        }

        @Override
        public boolean onDoubleTap(@NonNull MotionEvent event) {
            if (!isPlaying) return true;

            PlayerSkipView skipView = binding.mainPlayer.getSkipManager();

            if (skipView == null) return true;

            int eventX = (int)event.getX();
            int eventY = (int)event.getY();
            int height = binding.getRoot().getHeight();
            int width = binding.getRoot().getWidth();
            int halfWidth = width / 2;

            if (eventX > halfWidth) {
                skipView.skipAhead();
            } else {
                skipView.skipBack();
            }

            return super.onDoubleTap(event);
        }
    }
}

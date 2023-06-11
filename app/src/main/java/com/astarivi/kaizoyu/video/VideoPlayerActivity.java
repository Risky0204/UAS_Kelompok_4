package com.astarivi.kaizoyu.video;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.astarivi.kaizoyu.R;
import com.astarivi.kaizoyu.core.analytics.AnalyticsClient;
import com.astarivi.kaizoyu.core.models.Result;
import com.astarivi.kaizoyu.core.theme.AppCompatActivityTheme;
import com.astarivi.kaizoyu.databinding.ActivityVideoPlayerBinding;
import com.astarivi.kaizoyu.video.utils.AnimeEpisodeManager;
import com.astarivi.kaizoyu.video.utils.BundleUtils;
import com.google.android.material.snackbar.Snackbar;

import org.videolan.libvlc.MediaPlayer;

import io.github.tonnyl.spark.Spark;


public class VideoPlayerActivity extends AppCompatActivityTheme {
    private ActivityVideoPlayerBinding binding;
    private VideoPlayerViewModel viewModel;
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P){
            final WindowManager.LayoutParams wManager = getWindow().getAttributes();
            wManager.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        binding.mainPlayer.initialize(
                animeEpisodeManager != null ? animeEpisodeManager.getAnimeTitle() : result.getCleanedFilename(),
                animeEpisodeManager != null ? animeEpisodeManager.getEpisodeTitle(this) : getString(R.string.advanced_mode_title),
                this::finish
        );

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
                    message = "This provider doesn't support quick retry, please try again later (150 seconds max).";
                    break;
                case TimeOut:
                    message = "Connection to IRC has timed out. Check your internet connection and retry later.";
                    break;
                case UnknownHost:
                    message = "The IRC handshake server couldn't be reached. Check your internet connection.";
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

            isPlaying = true;

            if (spark != null) spark.stopAnimation();
            binding.getRoot().setBackgroundColor(Color.BLACK);
            binding.initialDownloadProgress.setVisibility(View.GONE);
            binding.downloadSpeed.setVisibility(View.GONE);
            binding.downloadStatus.setVisibility(View.GONE);
            binding.mainPlayer.play(file);
            binding.mainPlayer.setVisibility(View.VISIBLE);
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
        if (!isPlaying) spark.stopAnimation(); // stop animation on pause

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
    protected void onDestroy() {
        super.onDestroy();
        binding.mainPlayer.destroy();
        viewModel.destroy();
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        hideSystemUI();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUI();
    }

    private void hideSystemUI(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            final WindowInsetsController controller = getWindow().getInsetsController();

            if (controller != null) controller.hide(WindowInsets.Type.statusBars());
            if (controller != null) controller.hide(WindowInsets.Type.navigationBars());
        }
        else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            );
        }
    }

    private void delayedExit(){
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(this::finish, 5000);
    }
}

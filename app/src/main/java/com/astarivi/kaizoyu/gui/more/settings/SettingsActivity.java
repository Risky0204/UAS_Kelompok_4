package com.astarivi.kaizoyu.gui.more.settings;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.astarivi.kaizoyu.R;
import com.astarivi.kaizoyu.core.analytics.AnalyticsClient;
import com.astarivi.kaizoyu.core.storage.properties.ExtendedProperties;
import com.astarivi.kaizoyu.core.theme.AppCompatActivityTheme;
import com.astarivi.kaizoyu.core.theme.Theme;
import com.astarivi.kaizoyu.databinding.ActivitySettingsBinding;
import com.astarivi.kaizoyu.utils.Data;
import com.astarivi.kaizoyu.utils.Threading;
import com.astarivi.kaizoyu.utils.Translation;
import com.astarivi.kaizoyu.utils.Utils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.File;


public class SettingsActivity extends AppCompatActivityTheme {
    private ActivitySettingsBinding binding;
    private int nightTheme;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivitySettingsBinding.inflate(getLayoutInflater());

        setContentView(binding.getRoot());

        binding.settingsMainContainer.getLayoutTransition().setAnimateParentHierarchy(false);
        binding.developerSection.setVisibility(View.GONE);

        binding.openLogs.setOnClickListener(v -> {
            File logFile = new File (getFilesDir(), "log.txt");

            if (!logFile.exists()) {
                Toast.makeText(this, getString(R.string.dev_logs_toast), Toast.LENGTH_SHORT).show();
                return;
            }

            startActivity(
                    Intent.createChooser(
                            new Intent(Intent.ACTION_SEND)
                                    .putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(
                                            this,
                                            getString(R.string.provider_authority),
                                            logFile
                                    ))
                                    .setType("text/plain"),
                            "Share logs"
                    )
            );
        });

        binding.miscTitle.setOnLongClickListener(v -> {
            ExtendedProperties config = Data.getProperties(Data.CONFIGURATION.APP);
            config.setBooleanProperty("developer_menu", true);
            binding.developerSection.setVisibility(View.VISIBLE);
            config.save();

            Toast.makeText(this, getString(R.string.dev_section_toast), Toast.LENGTH_SHORT).show();
            return true;
        });

        binding.hideDevMenu.setOnClickListener(v -> {
            ExtendedProperties config = Data.getProperties(Data.CONFIGURATION.APP);
            config.setBooleanProperty("developer_menu", false);
            binding.developerSection.setVisibility(View.GONE);
            config.save();
        });

        loadSettings();

        // Set click listeners
        binding.nightThemeTrigger.setOnClickListener(this::showNightThemePopup);

        MaterialSwitch analytics = binding.analyticsValue;
        analytics.setOnCheckedChangeListener(this::triggerSave);

        MaterialSwitch ipv6Sources = binding.ipv6SorcesValue;
        ipv6Sources.setOnCheckedChangeListener(this::triggerSave);

        MaterialSwitch preferEnglishTitles = binding.preferEnglishValue;
        preferEnglishTitles.setOnCheckedChangeListener(this::triggerSave);

        binding.autoFavoriteValue.setOnCheckedChangeListener(this::triggerSave);
        binding.advancedSearch.setOnCheckedChangeListener(this::triggerSave);
        binding.strictModeValue.setOnCheckedChangeListener(this::triggerSave);

        binding.clearCacheTrigger.setOnClickListener(view -> {
            Utils.clearCache();
            Toast.makeText(this, getString(R.string.cache_toast), Toast.LENGTH_SHORT).show();
        });

        binding.clearSearchTrigger.setOnClickListener(v -> {
            Data.getRepositories().getSearchHistoryRepository().deleteAllAsync();
            Toast.makeText(this, getString(R.string.history_toast), Toast.LENGTH_SHORT).show();
        });

        binding.themeTrigger.setOnClickListener(v -> {
            ThemeSelectionModalBottomSheet modalBottomSheet = new ThemeSelectionModalBottomSheet(theme -> {
                AnalyticsClient.logEvent("theme_changed", theme.getTitle(this));

                Theme.setTheme(theme, this);

                Context ctx = getApplicationContext();
                PackageManager pm = ctx.getPackageManager();
                Intent intent = pm.getLaunchIntentForPackage(ctx.getPackageName());
                if (intent == null) return;
                ComponentName componentName = intent.getComponent();
                if (componentName == null) return;
                Intent mainIntent = Intent.makeRestartActivityTask(componentName);
                ctx.startActivity(mainIntent);
                Runtime.getRuntime().exit(0);
            });
            modalBottomSheet.show(getSupportFragmentManager(), ThemeSelectionModalBottomSheet.TAG);
        });
    }

    private void showNightThemePopup(View v) {
        final String night_theme_default = getString(R.string.night_theme_default);
        final String night_theme_day = getString(R.string.night_theme_day);
        final String night_theme_night = getString(R.string.night_theme_night);

        final String[] themes = {
                night_theme_default,
                night_theme_day,
                night_theme_night
        };

        TextView theme = binding.nightThemeValue;

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(getString(R.string.night_theme_context));
        builder.setItems(themes, (dialog, index) -> {
            nightTheme = index;
            theme.setText(themes[index]);
            saveSettings();
        });
        builder.show();
    }

    private void triggerSave(View v, boolean value) {
        saveSettings();
    }

    private void saveSettings(){
        ExtendedProperties config = Data.getProperties(Data.CONFIGURATION.APP);

        config.setIntProperty(
                "night_theme",
                nightTheme
        );

        config.setBooleanProperty(
                "analytics",
                binding.analyticsValue.isChecked()
        );

        config.setBooleanProperty(
                "show_ipv6",
                binding.ipv6SorcesValue.isChecked()
        );

        config.setBooleanProperty(
                "prefer_english",
                binding.preferEnglishValue.isChecked()
        );

        config.setBooleanProperty(
                "auto_favorite",
                binding.autoFavoriteValue.isChecked()
        );

        config.setBooleanProperty(
                "advanced_search",
                binding.advancedSearch.isChecked()
        );

        config.setBooleanProperty(
                "strict_mode",
                binding.strictModeValue.isChecked()
        );

        config.save();

        Data.reloadProperties();
    }

    private void loadSettings() {
        ExtendedProperties config = Data.getProperties(Data.CONFIGURATION.APP);

        // Hidden sections
        if (config.getBooleanProperty("developer_menu", false)) {
            binding.developerSection.setVisibility(View.VISIBLE);
        }

        // Translated Values
        TextView nightThemeText = binding.nightThemeValue;
        nightTheme = config.getIntProperty("night_theme", 0);

        nightThemeText.setText(
                Translation.getNightThemeTranslation(
                        nightTheme,
                        this
                )
        );

        // Switches
        binding.analyticsValue.setChecked(
                config.getBooleanProperty("analytics", true)
        );

        binding.strictModeValue.setChecked(
                config.getBooleanProperty("strict_mode", false)
        );

        binding.preferEnglishValue.setChecked(
                config.getBooleanProperty("prefer_english", true)
        );

        binding.autoFavoriteValue.setChecked(
                config.getBooleanProperty("auto_favorite", false)
        );

        binding.themeValue.setText(
                Theme.getCurrentTheme().getTitle(this)
        );

        binding.advancedSearch.setChecked(
                config.getBooleanProperty("advanced_search", false)
        );

        // IPv6 stuff
        MaterialSwitch ipv6Sources = binding.ipv6SorcesValue;
        ipv6Sources.setChecked(
                config.getBooleanProperty("show_ipv6", false)
        );

        ipv6Sources.setEnabled(false);

        Threading.submitTask(Threading.TASK.INSTANT, () -> {
            boolean ipv6Capable = Utils.isIPv6Capable();
            ipv6Sources.post(() -> setIPv6Capability(ipv6Capable));
        });
    }

    private void setIPv6Capability(boolean isIpv6Capable) {
        if (!isIpv6Capable) {
            ExtendedProperties config = Data.getProperties(Data.CONFIGURATION.APP);

            config.setBooleanProperty(
                    "show_ipv6",
                    false
            );

            config.save();

            try {
                binding.textView5.setOnClickListener(
                        v -> Toast.makeText(this, getString(R.string.ipv6_toast), Toast.LENGTH_SHORT).show()
                );
            } catch (NullPointerException ignored) {
                // This view doesn't exist anymore or is not in focus
            }

            return;
        }

        try {
            binding.ipv6SorcesValue.setEnabled(true);
        } catch (NullPointerException ignored) {
            // This view doesn't exist anymore or is not in focus
        }
    }
}
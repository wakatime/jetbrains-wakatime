/* ==========================================================
File:        CustomStatusBar.java
Description: Shows today's total code time in the status bar.
Maintainer:  WakaTime <support@wakatime.com>
License:     BSD, see LICENSE for more details.
Website:     https://wakatime.com/
===========================================================*/

package com.wakatime.intellij.plugin;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.Consumer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.*;

import javax.swing.Icon;
import java.awt.event.MouseEvent;

public class CustomStatusBar implements StatusBarWidgetFactory {

    @NotNull
    @Override
    public String getId() {
        return "WakaTime";
    }

    @Nls
    @NotNull
    @Override
    public String getDisplayName() {
        return "WakaTime";
    }

    @Override
    public boolean isAvailable(@NotNull Project project) { return true; }

    @NotNull
    @Override
    public StatusBarWidget createWidget(@NotNull Project project) {
        return new WakaTimeStatusBarWidget(project);
    }

    @Override
    public void disposeWidget(@NotNull StatusBarWidget widget) { }

    @Override
    public boolean canBeEnabledOn(@NotNull StatusBar statusBar) {
        return true;
    }

    public class WakaTimeStatusBarWidget implements StatusBarWidget {
        public final Project project;
        public final StatusBar statusBar;

        @Contract(pure = true)
        public WakaTimeStatusBarWidget(Project project) {
            this.project = project;
            this.statusBar = WindowManager.getInstance().getStatusBar(project);
        }

        @NotNull
        @Override
        public String ID() {
            return "WakaTime";
        }

        @Nullable
        @Override
        public WidgetPresentation getPresentation() {
            return new StatusBarPresenter(this);
        }

        @Override
        public void install(@NotNull StatusBar statusBar) { }

        @Override
        public void dispose() { }

        private class StatusBarPresenter implements StatusBarWidget.MultipleTextValuesPresentation, StatusBarWidget.Multiframe {
            private final WakaTimeStatusBarWidget widget;

            public StatusBarPresenter(WakaTimeStatusBarWidget widget) {
                this.widget = widget;
            }

            @Nullable
            @Override
            public ListPopup getPopupStep() {
                WakaTime.openDashboardWebsite();
                WakaTime.updateStatusBarText();
                if (widget.statusBar != null) widget.statusBar.updateWidget("WakaTime");
                return null;
            }

            @Nullable
            @Override
            public String getSelectedValue() { return WakaTime.getStatusBarText(); }

            @Override
            public @Nullable
            Icon getIcon() {
                String theme = UIUtil.isUnderDarcula() ? "dark" : "light";
                return IconLoader.getIcon("status-bar-icon-" + theme + "-theme.svg", WakaTime.class);
            }

            @Nullable
            @Override
            public String getTooltipText() {
                return null;
            }

            @Nullable
            @Override
            public Consumer<MouseEvent> getClickConsumer() {
                // Not used; use getPopupStep to handle click events
                return null;
            }

            @Override
            public StatusBarWidget copy() {
                return new WakaTimeStatusBarWidget(this.widget.project);
            }

            @Override
            public @NonNls
            @NotNull String ID() {
                return "WakaTime";
            }

            @Override
            public void install(@NotNull StatusBar statusBar) {

            }

            @Override
            public void dispose() {
                Disposer.dispose(widget);
            }
        }
    }
}
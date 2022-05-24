/* ==========================================================
File:        CustomExecutionListener.java
Description: Logs time from compile and build events.
Maintainer:  WakaTime <support@wakatime.com>
License:     BSD, see LICENSE for more details.
Website:     https://wakatime.com/
===========================================================*/

package com.wakatime.intellij.plugin;

import com.intellij.compiler.server.BuildManagerListener;
import com.intellij.openapi.compiler.CompilationStatusListener;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.awt.EventQueue;
import java.util.UUID;

public class CustomBuildManagerListener implements BuildManagerListener, CompilationStatusListener {
    @Override
    public void buildStarted(@NotNull Project project, @NotNull UUID sessionId, boolean isAutomake) {
        // WakaTime.log.debug("buildStarted event");
        if (!WakaTime.isAppActive()) return;
        if (!WakaTime.isProjectInitialized(project)) return;
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                VirtualFile file = WakaTime.getCurrentFile(project);
                if (file == null) return;
                WakaTime.isBuilding = true;
                WakaTime.appendHeartbeat(file, project, false, null);
            }
        });
    }

    @Override
    public void buildFinished(@NotNull Project project, @NotNull UUID sessionId, boolean isAutomake)  {
        // WakaTime.log.debug("buildFinished event");
        WakaTime.isBuilding = false;
    }

    @Override
    public void compilationFinished(boolean aborted, int errors, int warnings, @NotNull CompileContext compileContext) {
        // WakaTime.log.debug("compilationFinished event");
        WakaTime.isBuilding = false;
    }

    @Override
    public void automakeCompilationFinished(int errors, int warnings, @NotNull CompileContext compileContext) {
        // WakaTime.log.debug("automakeCompilationFinished event");
        WakaTime.isBuilding = false;
    }
}
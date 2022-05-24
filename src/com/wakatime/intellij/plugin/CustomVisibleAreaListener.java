/* ==========================================================
File:        CustomEditorMouseMotionListener.java
Description: Logs time from mouse motion events.
Maintainer:  WakaTime <support@wakatime.com>
License:     BSD, see LICENSE for more details.
Website:     https://wakatime.com/
===========================================================*/

package com.wakatime.intellij.plugin;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.awt.Rectangle;

public class CustomVisibleAreaListener implements VisibleAreaListener {
    @Override
    public void visibleAreaChanged(VisibleAreaEvent visibleAreaEvent) {
        // WakaTime.log.debug("visibleAreaChanged event");
        if (!didChange(visibleAreaEvent)) return;
        if (!WakaTime.isAppActive()) return;
        Document document = visibleAreaEvent.getEditor().getDocument();
        VirtualFile file = WakaTime.getFile(document);
        if (file == null) return;
        Project project = visibleAreaEvent.getEditor().getProject();
        if (!WakaTime.isProjectInitialized(project)) return;
        WakaTime.appendHeartbeat(file, project, false, document.getLineCount(), null);
    }

    private boolean didChange(VisibleAreaEvent visibleAreaEvent) {
        Rectangle oldRect = visibleAreaEvent.getOldRectangle();
        if (oldRect == null) return true;
        Rectangle newRect = visibleAreaEvent.getNewRectangle();
        if (newRect == null) return false;
        return newRect.x != oldRect.x || newRect.y != oldRect.y;
    }
}

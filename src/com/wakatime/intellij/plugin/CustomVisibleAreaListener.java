/* ==========================================================
File:        CustomEditorMouseMotionListener.java
Description: Logs time from mouse motion events.
Maintainer:  WakaTime <support@wakatime.com>
License:     BSD, see LICENSE for more details.
Website:     https://wakatime.com/
===========================================================*/

package com.wakatime.intellij.plugin;

import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.awt.Rectangle;

public class CustomVisibleAreaListener implements VisibleAreaListener {
    @Override
    public void visibleAreaChanged(VisibleAreaEvent visibleAreaEvent) {
        if (!didChange(visibleAreaEvent)) return;
        FileDocumentManager instance = FileDocumentManager.getInstance();
        VirtualFile file = instance.getFile(visibleAreaEvent.getEditor().getDocument());
        Project project = visibleAreaEvent.getEditor().getProject();
        WakaTime.appendHeartbeat(file, project, false);
    }

    private boolean didChange(VisibleAreaEvent visibleAreaEvent) {
        Rectangle oldRect = visibleAreaEvent.getOldRectangle();
        if (oldRect == null) return true;
        Rectangle newRect = visibleAreaEvent.getNewRectangle();
        if (newRect == null) return false;
        return newRect.x != oldRect.x || newRect.y != oldRect.y;
    }
}

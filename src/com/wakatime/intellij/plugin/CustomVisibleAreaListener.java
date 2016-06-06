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
import com.intellij.openapi.vfs.VirtualFile;

import java.math.BigDecimal;

public class CustomVisibleAreaListener implements VisibleAreaListener {
    @Override
    public void visibleAreaChanged(VisibleAreaEvent visibleAreaEvent) {
        final FileDocumentManager instance = FileDocumentManager.getInstance();
        final VirtualFile file = instance.getFile(visibleAreaEvent.getEditor().getDocument());
        if (file != null && !file.getUrl().startsWith("mock://")) {
            final String currentFile = file.getPath();
            if (WakaTime.shouldLogFile(currentFile)) {
                BigDecimal currentTime = WakaTime.getCurrentTimestamp();
                if (!currentFile.equals(WakaTime.lastFile) || WakaTime.enoughTimePassed(currentTime)) {
                    WakaTime.appendHeartbeat(currentTime, currentFile, false);
                }
            }
        }
    }
}

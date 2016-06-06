/* ==========================================================
File:        CustomEditorMouseListener.java
Description: Logs time from mouse click events.
Maintainer:  WakaTime <support@wakatime.com>
License:     BSD, see LICENSE for more details.
Website:     https://wakatime.com/
===========================================================*/

package com.wakatime.intellij.plugin;

import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;

import java.math.BigDecimal;

public class CustomEditorMouseListener  implements EditorMouseListener {
    @Override
    public void mousePressed(EditorMouseEvent editorMouseEvent) {
        final FileDocumentManager instance = FileDocumentManager.getInstance();
        final VirtualFile file = instance.getFile(editorMouseEvent.getEditor().getDocument());
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

    @Override
    public void mouseClicked(EditorMouseEvent editorMouseEvent) {
    }

    @Override
    public void mouseReleased(EditorMouseEvent editorMouseEvent) {
    }

    @Override
    public void mouseEntered(EditorMouseEvent editorMouseEvent) {
    }

    @Override
    public void mouseExited(EditorMouseEvent editorMouseEvent) {
    }
}

package com.wakatime.intellij.plugin;

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;

/**
 * Created by alanhamlett on 3/20/14.
 */
public class CustomDocumentListener implements DocumentListener {
    @Override
    public void beforeDocumentChange(DocumentEvent documentEvent) {
    }

    @Override
    public void documentChanged(DocumentEvent documentEvent) {
        String currentFile = FileDocumentManager.getInstance().getFile(documentEvent.getDocument()).getPath();
        long currentTime = System.currentTimeMillis() / 1000;
        if ((!currentFile.equals(WakaTime.lastFile) || WakaTime.enoughTimePassed(currentTime)) && !currentFile.contains("/.idea/workspace.xml")) {
            WakaTime.logFile(currentFile, false);
            WakaTime.lastFile = currentFile;
            WakaTime.lastTime = currentTime;
        }
    }
}

package com.wakatime.intellij.plugin;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerAdapter;
import org.jetbrains.annotations.NotNull;

/**
 * Created by alanhamlett on 3/20/14.
 */
public class CustomSaveListener extends FileDocumentManagerAdapter {

    @Override
    public void beforeDocumentSaving(Document document) {
        String currentFile = FileDocumentManager.getInstance().getFile(document).getPath();
        long currentTime = System.currentTimeMillis() / 1000;
        WakaTime.logFile(currentFile, true);
        WakaTime.lastFile = currentFile;
        WakaTime.lastTime = currentTime;
    }
}

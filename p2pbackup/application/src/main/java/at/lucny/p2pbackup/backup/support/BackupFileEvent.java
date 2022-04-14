package at.lucny.p2pbackup.backup.support;

import org.springframework.context.ApplicationEvent;

import java.nio.file.Path;

public class BackupFileEvent extends ApplicationEvent {

    private final transient Path file;

    public BackupFileEvent(Object source, Path file) {
        super(source);
        this.file = file;
    }

    public Path getFile() {
        return file;
    }
}


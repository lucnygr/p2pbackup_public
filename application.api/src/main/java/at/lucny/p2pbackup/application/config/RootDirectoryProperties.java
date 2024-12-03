package at.lucny.p2pbackup.application.config;

import lombok.Data;
import lombok.ToString;

import java.nio.file.Path;

@Data
@ToString
public class RootDirectoryProperties {

    private String name;

    private Path path;
}

package at.lucny.p2pbackup.application.config;

import lombok.Data;
import lombok.ToString;

import javax.validation.constraints.NotNull;
import java.nio.file.Path;

@Data
@ToString
public class DatabaseProperties {

    @NotNull
    private Path databaseDir;

    @NotNull
    private Boolean encrypt = Boolean.TRUE;
}

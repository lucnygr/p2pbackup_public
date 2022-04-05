package at.lucny.p2pbackup.application.config;

import lombok.Data;
import lombok.ToString;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.nio.file.Path;

@Data
@ToString
public class UserProperties {

    @NotNull
    private String user;

    @NotNull
    private String address;

    @NotNull
    @Min(1)
    private Integer port;

    @NotNull
    private Path certificatePath;

    @NotNull
    private Boolean allowBackupFromUser;

    @NotNull
    private Boolean allowBackupToUser;

}

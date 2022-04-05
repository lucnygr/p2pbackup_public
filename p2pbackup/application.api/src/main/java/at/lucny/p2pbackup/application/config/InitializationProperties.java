package at.lucny.p2pbackup.application.config;

import lombok.Data;
import lombok.ToString;

import javax.validation.constraints.NotNull;
import java.util.List;

@Data
@ToString
public class InitializationProperties {

    @NotNull
    private List<UserProperties> users;

    @NotNull
    private List<RootDirectoryProperties> rootDirectories;

    @NotNull
    private List<CloudProviderProperties> cloudProvider;

    @NotNull
    private Boolean disableUploadAgent = Boolean.FALSE;

    @NotNull
    private Boolean disableDistributionAgent = Boolean.FALSE;

    @NotNull
    private Boolean disableVerificationAgent = Boolean.FALSE;

    @NotNull
    private Boolean disableRestorationAgent = Boolean.FALSE;
}

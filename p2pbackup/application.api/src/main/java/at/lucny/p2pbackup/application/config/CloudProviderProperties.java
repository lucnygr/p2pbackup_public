package at.lucny.p2pbackup.application.config;

import lombok.Data;
import lombok.ToString;

import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;

@Data
@ToString
public class CloudProviderProperties {

    @NotNull
    private String id;

    @NotNull
    private Map<String, String> properties = new HashMap<>();

}

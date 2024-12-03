package at.lucny.p2pbackup.core.support;

import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Converts a String to a {@link Path}
 */
@Component
@ConfigurationPropertiesBinding
public class StringToPathConverter implements Converter<String, Path> {

    @Override
    public Path convert(String source) {
        return Paths.get(source);
    }
}

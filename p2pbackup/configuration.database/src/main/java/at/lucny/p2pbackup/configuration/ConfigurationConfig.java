package at.lucny.p2pbackup.configuration;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.DatabaseConfiguration;
import org.apache.commons.configuration2.builder.BasicConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.DatabaseBuilderParameters;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * Configures a {@link Configuration} that stores all configuration-properties in the database.
 */
@org.springframework.context.annotation.Configuration
public class ConfigurationConfig {

    @Bean
    public Configuration configuration(DataSource dataSource) throws ConfigurationException {
        Parameters params = new Parameters();
        DatabaseBuilderParameters databaseBuilderParameters = params.database()
                .setAutoCommit(true)
                .setDataSource(dataSource)
                .setTable("CONFIG_ENTRY")
                .setKeyColumn("KEY")
                .setValueColumn("VALUE")
                .setListDelimiterHandler(
                        new DefaultListDelimiterHandler(','))
                .setThrowExceptionOnMissing(false);
        var builder = new BasicConfigurationBuilder<>(DatabaseConfiguration.class)
                .configure(databaseBuilderParameters);

        return builder.getConfiguration();
    }
}

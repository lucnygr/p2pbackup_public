package at.lucny.p2pbackup.configuration.support;

public final class ConfigurationConstants {

    private ConfigurationConstants() {
    }

    /**
     * This property is set if the backup-application has run at least once.
     */
    public static final String PROPERTY_FIRST_STARTUP = "INIT.FIRST_STARTUP";

    /**
     * This property is set to TRUE if the automatic upload-agent should not be started.
     */
    public static final String PROPERTY_DISABLE_UPLOAD_AGENT = "AGENTS.DISABLE_UPLOAD_AGENT";

    /**
     * This property is set to TRUE if the automatic distribution-agent should not be started.
     */
    public static final String PROPERTY_DISABLE_DISTRIBUTION_AGENT = "AGENTS.DISABLE_DISTRIBUTION_AGENT";

    /**
     * This property is set to TRUE if the automatic verification-agent should not be started.
     */
    public static final String PROPERTY_DISABLE_VERIFICATION_AGENT = "AGENTS.DISABLE_VERIFICATION_AGENT";

    /**
     * This property is set to TRUE if the automatic restoration-agent should not be started.
     */
    public static final String PROPERTY_DISABLE_RESTORATION_AGENT = "AGENTS.DISABLE_RESTORATION_AGENT";

    /**
     * This property is set to the current recovery state.
     */
    public static final String PROPERTY_RECOVERY_STATE = "RECOVERY.STATE";
}

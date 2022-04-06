package at.lucny.p2pbackup.application.config;

import lombok.Data;
import lombok.ToString;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.time.Duration;

@Data
@ToString
public class NetworkProperties {

    @NotNull
    @Min(0)
    private Integer port;

    /**
     * The duration between attempts to connect another user.
     * Defaults to 10 minutes. Minimum 1 minute, maximum 1 day.
     */
    @NotNull
    @DurationMin(minutes = 1)
    @DurationMax(days = 1)
    private Duration durationBetweenConnectionAttempts = Duration.ofMinutes(10);
}

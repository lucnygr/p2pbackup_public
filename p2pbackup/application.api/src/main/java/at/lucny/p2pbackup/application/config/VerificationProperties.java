package at.lucny.p2pbackup.application.config;

import lombok.Data;
import lombok.ToString;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;

import javax.validation.constraints.NotNull;
import java.time.Duration;

@Data
@ToString
public class VerificationProperties {

    /**
     * The duration between attempts to verify a block at another user.
     * Defaults to 14 days. Minimum 1 day, maximum 30 days.
     */
    @NotNull
    @DurationMin(days = 1)
    @DurationMax(days = 30)
    private Duration durationBetweenVerifications = Duration.ofDays(14);

    /**
     * The duration before a verified block no longer counts as verified
     * Defaults to 21 days. Minimum 2 days, maximum 60 days.
     */
    @NotNull
    @DurationMin(days = 2)
    @DurationMax(days = 60)
    private Duration durationBeforeVerificationInvalid = Duration.ofDays(21);

    /**
     * The duration between before an unverifired block gets deleted at a another user.
     * Defaults to 30 days. Minimum 7 days, maximum 180 days.
     */
    @NotNull
    @DurationMin(days = 7)
    @DurationMax(days = 180)
    private Duration durationBeforeDeletion = Duration.ofDays(30);
}

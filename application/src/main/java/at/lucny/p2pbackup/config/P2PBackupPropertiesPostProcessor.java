package at.lucny.p2pbackup.config;

import at.lucny.p2pbackup.application.config.P2PBackupProperties;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Profile("dev")
public class P2PBackupPropertiesPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof P2PBackupProperties properties) {
            Duration durationInDays = properties.getVerificationProperties().getDurationBetweenVerifications();
            Duration durationAsSeconds = Duration.ofMinutes(durationInDays.getSeconds() / (24 * 60 * 60));
            properties.getVerificationProperties().setDurationBetweenVerifications(durationAsSeconds);

            durationInDays = properties.getVerificationProperties().getDurationBeforeVerificationInvalid();
            durationAsSeconds = Duration.ofMinutes(durationInDays.getSeconds() / (24 * 60 * 60));
            properties.getVerificationProperties().setDurationBeforeVerificationInvalid(durationAsSeconds);

            durationInDays = properties.getVerificationProperties().getDurationBeforeDeletion();
            durationAsSeconds = Duration.ofMinutes(durationInDays.getSeconds() / (24 * 60 * 60));
            properties.getVerificationProperties().setDurationBeforeDeletion(durationAsSeconds);
        }
        return bean;
    }
}

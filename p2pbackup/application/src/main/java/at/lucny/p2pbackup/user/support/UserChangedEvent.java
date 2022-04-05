package at.lucny.p2pbackup.user.support;

import lombok.Getter;
import lombok.ToString;
import org.springframework.context.ApplicationEvent;

@Getter
@ToString
public class UserChangedEvent extends ApplicationEvent {

    private final String userId;

    public UserChangedEvent(Object source, String userId) {
        super(source);
        this.userId = userId;
    }
}

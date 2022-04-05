package at.lucny.p2pbackup.user.support;

import lombok.Getter;
import lombok.ToString;
import org.springframework.context.ApplicationEvent;

@Getter
@ToString
public class UserAddedEvent extends ApplicationEvent {

    private final String userId;

    public UserAddedEvent(Object source, String userId) {
        super(source);
        this.userId = userId;
    }
}

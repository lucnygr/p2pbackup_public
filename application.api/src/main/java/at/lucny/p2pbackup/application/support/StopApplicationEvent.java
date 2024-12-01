package at.lucny.p2pbackup.application.support;

import org.springframework.context.ApplicationEvent;

public class StopApplicationEvent extends ApplicationEvent {

    public StopApplicationEvent(Object source) {
        super(source);
    }
}

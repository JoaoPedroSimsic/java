package com.example.message.core.ports.output;

import com.example.message.core.events.UserCreatedEvent;

public interface OutboxPort {
    void save(UserCreatedEvent event);
}

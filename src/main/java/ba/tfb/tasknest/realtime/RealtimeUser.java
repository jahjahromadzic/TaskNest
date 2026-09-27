package ba.tfb.tasknest.realtime;

import java.security.Principal;
import java.util.UUID;

public record RealtimeUser(UUID id) implements Principal {

    @Override
    public String getName() {
        return id.toString();
    }
}

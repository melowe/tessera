package com.quorum.tessera.server.jersey;

import java.util.Collections;
import java.util.Set;
import javax.ws.rs.core.Application;

public class SampleApplication extends Application {

    @Override
    public Set<Object> getSingletons() {
        return Collections.singleton(new SampleResource());
    }

}

package com.quorum.tessera.context;

import com.quorum.tessera.loader.ServiceLoaderUtil;

import java.util.Optional;

public interface ContextHolder {

    Optional<RuntimeContext> getContext();

    void setContext(RuntimeContext runtimeContext);

    static ContextHolder getInstance() {
        return ServiceLoaderUtil.load(ContextHolder.class)
            .orElse(DefaultContextHolder.INSTANCE);
    }

}

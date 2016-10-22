/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.infra;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Utilities for Optional.
 *
 * @author Michael Vorburger
 */
public final class Optionals {

    private static final IfPresentElse IF_PRESENT_ELSE_DO_IMPL = new IfPresentElseImpl();

    private static final IfPresentElse IF_PRESENT_ELSE_NOOP_IMPL = new IfPresentElseNoopImpl();

    private Optionals() {
    }

    /**
     * Transform a Google Guava Optional to a JDK java.util Optional.
     * @param guavaOptional the Guava Optional
     * @return the JDK java.util Optional
     */
    public static <T> Optional<T> of(com.google.common.base.Optional<T> guavaOptional) {
        // http://stackoverflow.com/a/33918585/421602
        return guavaOptional.transform(java.util.Optional::of).or(java.util.Optional.empty());
    }

    public static <T> IfPresentElse ifPresent(Optional<T> optional, Consumer<? super T> consumer) {
        if (optional.isPresent()) {
            consumer.accept(optional.get());
            return IF_PRESENT_ELSE_NOOP_IMPL;
        } else {
            return IF_PRESENT_ELSE_DO_IMPL;
        }
    }

    @FunctionalInterface
    // TODO Am I just too dumb to see it in java.util.function, or did they really not add this??
    public interface Procedure {
        void apply();
    }

    public interface IfPresentElse {
        void elseDo(Procedure elseProcedure);
    }

    private static class IfPresentElseImpl implements IfPresentElse {
        @Override
        public void elseDo(Procedure elseProcedure) {
            elseProcedure.apply();
        }
    }

    private static class IfPresentElseNoopImpl implements IfPresentElse {
        @Override
        public void elseDo(Procedure elseProcedure) {
            // Do nothing.
        }
    }
}

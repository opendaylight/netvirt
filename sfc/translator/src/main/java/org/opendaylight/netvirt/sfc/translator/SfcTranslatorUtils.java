/*
 * Copyright © 2018 Red Hat, Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.sfc.translator;

import static java.util.Collections.emptyList;

import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class SfcTranslatorUtils {
    private SfcTranslatorUtils() {
        // Utility class
    }

    // TODO Replace this with mdsal's DataObjectUtils.nullToEmpty when upgrading to mdsal 3
    @Nonnull
    public static <T> List<T> nullToEmpty(final @Nullable List<T> input) {
        return input != null ? input : emptyList();
    }
}

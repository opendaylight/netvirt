/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.qosservice;

import static java.util.Objects.requireNonNull;

import java.util.Optional;
import java.util.regex.Pattern;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;

/**
 * Utility for {@link Uuid}.
 * This class is ThreadSafe.
 * @author Michael Vorburger.ch
 */
class UuidUtil {

    private Pattern uuidPattern;

    Optional<Uuid> newUuidIfValidPattern(String possibleUuid) {
        requireNonNull(possibleUuid, "possibleUuid == null");

        if (uuidPattern == null) {
            // Thread safe because it really doesn't matter even if we were to do this initialization more than once
            if (Uuid.PATTERN_CONSTANTS.size() != 1) {
                throw new IllegalStateException("Uuid.PATTERN_CONSTANTS.size() != 1");
            }
            uuidPattern = Pattern.compile(Uuid.PATTERN_CONSTANTS.get(0));
        }

        if (uuidPattern.matcher(possibleUuid).matches()) {
            return Optional.of(new Uuid(possibleUuid));
        } else {
            return Optional.empty();
        }
    }
}

/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import java.util.regex.Pattern;
import javax.annotation.concurrent.ThreadSafe;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;

/**
 * Utility for {@link Uuid}.
 *
 * @author Michael Vorburger.ch
 */
@ThreadSafe
class UuidUtil {

    private Pattern uuidPattern;

    Optional<Uuid> newUuidIfValidPattern(String possibleUuid) {
        Preconditions.checkNotNull(possibleUuid, "possibleUuid == null");

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
            return Optional.absent();
        }
    }
}

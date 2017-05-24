/*
 * Copyright (c) 2017 Ericsson Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.service.domain.api;

import java.util.Set;

/**
 * The classifier state defined by a collection of {@link
 * ClassifierRenderableEntry}.
 */
public interface ClassifierState {
    Set<ClassifierRenderableEntry> getAllEntries();
}

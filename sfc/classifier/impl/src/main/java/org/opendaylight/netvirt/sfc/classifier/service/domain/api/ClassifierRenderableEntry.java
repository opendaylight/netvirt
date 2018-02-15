/*
 * Copyright (c) 2017 Ericsson Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.service.domain.api;

/**
 * A {@code ClassifierRenderableEntry} represents a unit of classifier
 * information that may be used to consistently perform ("render")
 * actions to southbound APIs that can also be consistently reversed
 * ("suppress").
 */
public interface ClassifierRenderableEntry {

    /**
     * Render the entry using the provided {@code ClassifierEntryRenderer}.
     *
     * @param classifierEntryRenderer the renderer.
     */
    void render(ClassifierEntryRenderer classifierEntryRenderer);

    /**
     * Suppress the entry using the provided {@code ClassifierEntryRenderer}.
     *
     * @param classifierEntryRenderer the renderer.
     */
    void suppress(ClassifierEntryRenderer classifierEntryRenderer);
}

/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.bgpmanager.api;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;

/**
 * Factory for creating IBgpManager instances.
 *
 * @author Alexis de Talhouët
 */
public interface BgpManagerFactory {
    IBgpManager newInstance(DataBroker dataBroker);
}
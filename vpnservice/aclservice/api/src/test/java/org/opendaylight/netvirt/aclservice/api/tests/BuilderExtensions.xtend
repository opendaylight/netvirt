/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.api.tests

import org.eclipse.xtext.xbase.lib.Procedures.Procedure1
import org.opendaylight.yangtools.concepts.Builder

/**
 * Xtend extension method for >> operator support for ODL Builders.
 *
 * <pre>import static extension org.opendaylight.netvirt.aclservice.api.tests.BuilderExtensions.operator_doubleGreaterThan</pre>
 *
 * allows to write:
 *
 * <pre>new InterfaceBuilder >> [
 *          name = "hello, world"
 *      ]</pre>
 *
 * instead of:
 *
 * <pre>(new InterfaceBuilder => [
 *          name = "hello, world"
 *      ]).build</pre>
 *
 * @see org.eclipse.xtext.xbase.lib.ObjectExtensions.operator_doubleArrow(T, Procedure1<? super T>)
 *
 * @author Michael Vorburger
 */
class BuilderExtensions {

    def static <P, T extends Builder<P>> P operator_doubleGreaterThan(T object, Procedure1<? super T> block) {
        block.apply(object);
        return object.build;
    }

}

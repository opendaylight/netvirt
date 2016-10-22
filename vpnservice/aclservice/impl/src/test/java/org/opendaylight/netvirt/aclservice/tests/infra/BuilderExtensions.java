/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests.infra;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.xtext.xbase.lib.Procedures.Procedure1;
import org.opendaylight.yangtools.concepts.Builder;

/**
 * Xtend extension method for &gt;&gt; operator support for ODL Builders.
 *
 * <p>TODO This test helper class will later move to a higher-up shared infra
 * project: pending review of https://git.opendaylight.org/gerrit/#/c/44099/,
 * where this is also included, as XtendBuilderExtensions.

 * <pre>import static extension org.opendaylight.netvirt.aclservice.api.tests
 *     .BuilderExtensions.operator_doubleGreaterThan</pre>
 *
 * <p>allows to write:
 *
 * <pre>new InterfaceBuilder &gt;&gt; [
 *          name = "hello, world"
 *      ]</pre>
 *
 * <p>instead of:
 *
 * <pre>(new InterfaceBuilder =&gt; [
 *          name = "hello, world"
 *      ]).build</pre>
 *
 * <p>See also org.eclipse.xtext.xbase.lib.ObjectExtensions.operator_doubleArrow.
 *
 * @author Michael Vorburger
 */
public class BuilderExtensions {

    public static <P, T extends Builder<P>> P operator_doubleGreaterThan(@NonNull T object,
            Procedure1<? super T> block) {
        block.apply(object);
        return object.build();
    }

}

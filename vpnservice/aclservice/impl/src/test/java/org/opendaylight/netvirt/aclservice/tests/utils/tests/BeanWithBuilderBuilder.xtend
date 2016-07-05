/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests.utils.tests

import org.eclipse.xtend.lib.annotations.Accessors
import org.eclipse.xtend.lib.annotations.FinalFieldsConstructor

@Accessors
class BeanWithBuilderBuilder {

    // This class is in a separate file instead of within XtendBeanGeneratorTest
    // so that the private fields are not visible to the test, thus simulating
    // real world beans, which are separate not inner classes, with Builder
    // classes "next" to them.

    String name

    def BeanWithBuilder build() {
        new BeanWithBuilderImpl(name)
    }

    @FinalFieldsConstructor
    @Accessors(PUBLIC_GETTER)
    static private class BeanWithBuilderImpl implements BeanWithBuilder {
        final String name
    }

}

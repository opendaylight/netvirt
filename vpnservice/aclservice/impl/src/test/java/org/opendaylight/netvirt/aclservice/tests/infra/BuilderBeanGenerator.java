/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests.infra;

import ch.vorburger.xtendbeans.XtendBeanGenerator;
import org.opendaylight.yangtools.concepts.Builder;

/**
 * {@link XtendBeanGenerator} customized for support of ODL {@link Builder}.
 *
 * <p>
 * TODO This test helper class will later move to a higher-up shared infra
 * project: pending review of https://git.opendaylight.org/gerrit/#/c/44099/,
 * where the XtendYangBeanGenerator class is a more extended version of this
 * (once that is merged, this will get integrated there; probably
 * XtendYangBeanGenerator will extend this BuilderBeanGenerator).
 *
 * @author Michael Vorburger
 */
// package-local: no need to expose this, consider it an implementation detail; public API is the AssertDataObjects
class BuilderBeanGenerator extends XtendBeanGenerator {

    private boolean useBuilderExtensions(Class<?> builderClass) {
        return Builder.class.isAssignableFrom(builderClass);
    }

    @Override
    protected boolean isUsingBuilder(Object bean, Class<?> builderClass) {
        if (useBuilderExtensions(builderClass)) {
            return false;
        } else {
            return super.isUsingBuilder(bean, builderClass);
        }
    }

    @Override
    protected String getOperator(Object bean, Class<?> builderClass) {
        if (useBuilderExtensions(builderClass)) {
            return ">>";
        } else {
            return super.getOperator(bean, builderClass);
        }
    }

    @Override
    protected String stringify(Class<?> klass) {
        return klass.getSimpleName();
    }
}

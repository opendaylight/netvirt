/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests.infra;

import ch.vorburger.xtendbeans.AssertBeans;
import org.junit.ComparisonFailure;
import org.opendaylight.yangtools.concepts.Builder;
import org.opendaylight.yangtools.yang.binding.DataObject;

/**
 * Assert equals utility for objects which have a {@link Builder}.
 *
 * <p>TODO This test helper class will later move to a higher-up shared infra
 * project: pending review of https://git.opendaylight.org/gerrit/#/c/44099/,
 * where the AssertDataObjects class is a more extended version of this.
 *
 * @see AssertBeans
 *
 * @author Michael Vorburger
 */
public final class AssertBuilderBeans {

    private static final BuilderBeanGenerator GENERATOR = new BuilderBeanGenerator();

    private AssertBuilderBeans() { }

    public static void assertEqualBeans(Object expected, Object actual) throws ComparisonFailure {
        if (expected instanceof DataObject) {
            throw new IllegalArgumentException("Use AssertDataObjects instead of this to compare YANG DataObjects");
        }
        String expectedText = GENERATOR.getExpression(expected);
        assertEqualByText(expectedText, actual);
    }

    public static void assertEqualByText(String expectedText, Object actual) throws ComparisonFailure {
        if (actual instanceof DataObject) {
            throw new IllegalArgumentException("Use AssertDataObjects instead of this to compare YANG DataObjects");
        }
        String actualText = GENERATOR.getExpression(actual);
        if (!expectedText.equals(actualText)) {
            throw new ComparisonFailure("Expected and actual beans do not match", expectedText, actualText);
        }
    }
}

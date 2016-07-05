/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package somewhere.testutils.xtend;

import java.util.Objects;
import org.junit.Assert;
import org.junit.ComparisonFailure;

/**
 * Utility similar to core JUnit's {@link Assert} but with particular support
 * for Java Beans, based on the {@link XtendBeanGenerator}.
 *
 * <p>
 * These methods can be used directly:
 * <code>AssertBeans.assertEqualBeans(...)</code>, however, they read better if
 * they are referenced through static import:<br/>
 *
 * <pre>
 * import static org.opendaylight.netvirt.aclservice.tests.utils.AssertBeans.assertEqualBeans;
 *    ...
 *    assertEqualBeans(...);
 * </pre>
 *
 * <p>
 * Note that your IDE can support you to create the static import. For example
 * in Eclipse, type: AssertBeans &lt;Ctrl-Space&gt; (will import AssertBeans,
 * non static) . &lt;Ctrl-Space&gt; (will auto-complete assertEqualBeans). Now
 * cursor back to highlight "assertEqualBeans" and Ctrl-Shift-M on it will turn it into
 * a static import.
 *
 * @author Michael Vorburger
 */
public final class AssertBeans {

    /**
     * Asserts that two JavaBean objects are equal. If they are not, an
     * {@link AssertionError} thrown. The message is If <code>expected</code>
     * and <code>actual</code> are <code>null</code>, they are considered equal.
     *
     * @param expected
     *            expected value
     * @param actual
     *            the value to check against <code>expected</code>
     */
    public static void assertEqualBeans(Object expected, Object actual) throws ComparisonFailure {
        if (!Objects.equals(expected, actual)) {
            throw new ComparisonFailure("Expected and actual beans do not match",
                    new XtendBeanGenerator().getExpression(expected), new XtendBeanGenerator().getExpression(actual));
        }
    }

    private AssertBeans() {
    }
}

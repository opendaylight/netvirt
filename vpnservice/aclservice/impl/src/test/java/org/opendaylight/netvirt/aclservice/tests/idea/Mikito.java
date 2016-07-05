/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests.idea;

import static org.mockito.Mockito.mock;
import java.lang.reflect.Modifier;
import org.apache.commons.lang3.NotImplementedException;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Mike's helper to easily create <a href=
 * "http://googletesting.blogspot.ch/2013/07/testing-on-toilet-know-your-test-doubles.html">Stubs
 * & Fakes</a>.
 *
 * <p>
 * Mikitos are:
 * <ul>
 * <li>fully type safe and refactoring resistant; whereas Mockito is not, e.g.
 * for return values with doReturn(...).when(), and uses runtime instead of
 * compile time error reporting for this.
 * <li>enforce ExceptionAnswer by default for non-implemented methods (which is
 * possible with Mockito, but is easily forgotten)
 * <li>avoid confusion re. the alternative doReturn(...).when() syntax required
 * with ExceptionAnswer instead of when(...).thenReturn()
 * </ul>
 *
 * The current implementation internally uses Mockito. Please consider this an
 * implementation detail. It may be changed in the future. One of the impacts of
 * this internal use of Mockito is that because constructors (and thus
 * field initializers) are not called by Mockito, so instead of:
 *
 * <pre>
 * abstract class FakeService implements Service {
 *     private final List<Thing> things = new ArrayList<>();
 *
 *     public List<Thing> getThings() {
 *         return things;
 *     }
 *
 *     &#64;Override
 *     public void installThing(Thing thing) {
 *         things.add(thing);
 *     }
 * }</pre>
 *
 * you'll just need to do:
 *
 * abstract class FakeService implements Service {
 *     private List<Thing> things;
 *
 *     public List<Thing> getThings() {
 *         if (things == null)
 *             things = new ArrayList<>()
 *         return things;
 *     }
 *
 *     &#64;Override
 *     public void installThing(Thing thing) {
 *         getThings().add(thing);
 *     }
 * }</pre>
 *
 * @see MikitoTest
 * @see LearnMockitoTest
 *
 * @author Michael Vorburger
 */
public class Mikito {

    public static <T> T stub(Class<T> abstractClass) {
        T stub = mock(abstractClass, new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                if (Modifier.isAbstract(invocation.getMethod().getModifiers())) {
                    throw new NotImplementedException(invocation.getMethod().getName() + " is not stubbed in " + abstractClass.getName());
                } else {
                    return invocation.callRealMethod();
                }
            }
        });
        return stub;
    }

}

/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests.utils.tests

import java.util.List
import org.eclipse.xtend.lib.annotations.Accessors
import org.junit.Test
import org.opendaylight.genius.mdsalutil.ActionInfo
import org.opendaylight.genius.mdsalutil.ActionInfoBuilder
import org.opendaylight.netvirt.aclservice.tests.utils.XtendBeanGenerator
import org.opendaylight.netvirt.aclservice.tests.utils.tests.XtendBeanGeneratorTest.Bean
import org.opendaylight.netvirt.aclservice.tests.utils.tests.XtendBeanGeneratorTest.BeanWithMultiConstructor
import org.opendaylight.netvirt.aclservice.tests.utils.tests.XtendBeanGeneratorTest.BeanWithMultiConstructorBuilder

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue
import org.junit.Ignore

/**
 * Unit test for basic XtendBeanGenerator.
 *
 * @see XtendBeanGeneratorTest for more advanced tests & examples
 *
 * @author Michael Vorburger
 */
class XtendBeanGeneratorBaseTest {

    static private class TestableXtendBeanGenerator extends XtendBeanGenerator {
        // Make some protected methods public so that we can test them here
        override public getBuilderClass(Object bean) {
            super.getBuilderClass(bean)
        }
    }

    val g = new TestableXtendBeanGenerator()

    @Test def void simplestNumberExpression() {
        assertThatEndsWith(g.getExpression(123), "123")
    }

    @Test def void simpleCharacter() {
        assertThatEndsWith(g.getExpression(new Character("c")), "'c'")
    }

    @Test def void nullCharacter() {
        var Character nullCharacter
        assertThatEndsWith(g.getExpression(nullCharacter), "null")
    }

    @Test def void defaultCharacter() {
        var char defaultCharacter
        assertThatEndsWith(g.getExpression(defaultCharacter), "")
    }

    @Test def void emptyString() {
        assertThatEndsWith(g.getExpression(""), "")
    }

    @Test def void aNull() {
        assertThatEndsWith(g.getExpression(null), "null")
    }

    @Test def void emptyList() {
        assertThatEndsWith(g.getExpression(#[]), "#[\n]")
    }

    @Test def void list() {
        assertThatEndsWith(g.getExpression(#["hi", "ho"]), "#[\n    \"hi\",\n    \"ho\"\n]")
    }

    @Test def void findEnclosingBuilderClass() {
        assertEquals(BeanWithBuilderBuilder,
            g.getBuilderClass(new BeanWithBuilderBuilder().build))
    }

    @Test def void findAdjacentBuilderClass() {
        assertEquals(BeanWithMultiConstructorBuilder,
            g.getBuilderClass(new BeanWithMultiConstructor(123)))
    }

    @Test def void findAdjacentBuilderClass2() {
        assertEquals(ActionInfoBuilder,
            g.getBuilderClass(new ActionInfo(null, null as String[])))
    }

    @Test def void emptyComplexBean() {
        assertEquals('''new Bean
            '''.toString, g.getExpression(new Bean))
    }

    @Test def void neverCallOnlyGettersIfThereIsNoSetter() {
        assertEquals("new ExplosiveBean\n", g.getExpression(new ExplosiveBean))
    }

    @Test def void neverCallOnlyGettersIfThereIsNoSetterEvenIfItDoesNotThrowAnException() {
        val bean = new WeirdBean
        assertEquals("new WeirdBean\n", g.getExpression(bean))
        assertFalse(bean.wasGetterCalled)
    }

    @Test def void testEnum() {
        assertEquals("TestEnum.a", g.getExpression(TestEnum.a))
    }

    @Test def void listBean() {
        val b = new ListBean => [
            strings += #[ "hi", "bhai" ]
        ]
        assertEquals(
            '''
            new ListBean => [
                strings += #[
                    "hi",
                    "bhai"
                ]
            ]'''.toString, g.getExpression(b))
    }

    @Test def void listBean2() {
        // If there is a List setter, then prefer that over get().add(), so = over +=
        val b = new ListBean2 => [
            strings = #[ "hi", "bhai" ]
        ]
        assertEquals(
            '''
            new ListBean2 => [
                strings = #[
                    "hi",
                    "bhai"
                ]
            ]'''.toString, g.getExpression(b))
    }

    @Test def void arrayBean() {
        val b = new ArrayBean => [
            strings = #[ "hi", "bhai" ]
        ]
        assertEquals(
            '''
            new ArrayBean => [
                strings = #[
                    "hi",
                    "bhai"
                ]
            ]'''.toString, g.getExpression(b))
    }

    @Test def void defaultArray() {
        val b = new ArrayBean
        assertEquals("new ArrayBean\n", g.getExpression(b))
    }

    @Test def void nullArray() {
        val b = new ArrayBean => [
            strings = null
        ]
        assertEquals("new ArrayBean => [\n    strings = null\n]", g.getExpression(b))
    }

    @Test def void primitiveArrayBean() {
        val b = new PrimitiveArrayBean => [
            ints = #[ 123, 456 ]
        ]
        assertEquals(
            '''
            new PrimitiveArrayBean => [
                ints = #[
                    123,
                    456
                ]
            ]'''.toString, g.getExpression(b))
    }

    @Test def void defaultPrimitiveArrayBean() {
        val b = new PrimitiveArrayBean
        assertEquals("new PrimitiveArrayBean\n", g.getExpression(b))
    }

    @Test def void nullPrimitiveArrayBean() {
        val b = new PrimitiveArrayBean => [
            ints = null
        ]
        assertEquals("new PrimitiveArrayBean => [\n    ints = null\n]", g.getExpression(b))
    }

    @Test def void arrayBeanList() {
        val b = new ArrayBeanList => [
            strings = #[ "hi", "bhai" ]
            longs = #[ 12, 34 ]
        ]
        assertEquals(
            '''
            (new ArrayBeanListBuilder => [
                longs = #[
                    12L,
                    34L
                ]
                strings += #[
                    "hi",
                    "bhai"
                ]
            ]).build()'''.toString, g.getExpression(b))
    }

    @Ignore // This messy mix of bean with array properties and *Builder with List of it instead is a mess and impossible to support nicely, so just don't do that
    @Test def void nullArrayBeanList() {
        val b = new ArrayBeanList => [
            strings = null
            longs = null
        ]
        assertEquals("new ArrayBeanBuilder\n", g.getExpression(b))
    }

    @Test def void emptyArrayToCheckCorrectDefaulting() {
        val b = new ArrayBean
        assertEquals("new ArrayBean\n", g.getExpression(b))
    }

    def private void assertThatEndsWith(String string, String endsWith) {
        assertTrue("'''" + string + "''' expected to endWith '''" + endsWith + "'''", string.endsWith(endsWith));
    }

    public static enum TestEnum { a, b, c }

    public static class ListBean {
        @Accessors(PUBLIC_GETTER) /* but no setter */
        List<String> strings = newArrayList
    }

    public static class ListBean2 {
        @Accessors
        List<String> strings = newArrayList
    }

    @Accessors
    public static class ArrayBean {
        String[] strings = newArrayList
    }

    @Accessors
    public static class PrimitiveArrayBean {
        int[] ints = newArrayList
    }

    @Accessors(PUBLIC_GETTER) // with Builder, below!
    public static class ArrayBeanList {
        String[] strings
        long[] longs
    }

    public static class ArrayBeanListBuilder {
        @Accessors(PUBLIC_GETTER) List<String> strings = newArrayList
        @Accessors List<Long> longs = newArrayList

        def public build() {
            new ArrayBeanList() => [
                it.strings = this.strings
                it.longs = this.longs
            ]
        }
    }

    public static class ExplosiveBean {
        String onlyGetter
        def String getOnlyGetter() {
            throw new IllegalStateException
        }
    }

    public static class WeirdBean {
        boolean wasGetterCalled
        def String getOnlyGetter() {
            wasGetterCalled = true
            "hello, world"
        }
    }

}

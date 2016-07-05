/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests.utils.tests

import java.math.BigInteger
import java.util.List
import org.eclipse.xtend.lib.annotations.Accessors
import org.eclipse.xtend.lib.annotations.FinalFieldsConstructor
import org.junit.Test
import org.opendaylight.netvirt.aclservice.tests.utils.XtendBeanGenerator

import static org.junit.Assert.assertEquals
import org.opendaylight.netvirt.aclservice.tests.utils.tests.BeanWithBuilderBuilder.BeanWithBuilderImpl

/**
 * Unit test / demo for XtendBeanGenerator.
 *
 * @see XtendBeanGeneratorBaseTest for more basic tests.
 *
 * @author Michael Vorburger
 */
class XtendBeanGeneratorTest {

    val g = new XtendBeanGenerator()

    @Test def void simpleBean() {
        val bean = new Bean => [
            name = "hello, world"
        ]
        assertEquals('''
            new Bean => [
                name = "hello, world"
            ]'''.toString, g.getExpression(bean))
    }

    @Test def void complexBean() {
        val bean = new Bean => [
            ALongObject = 123L
            AShort = 123 as short
            anInt = 123
            anInteger = 123
            bigInteger = 456bi
            innerBean = new Bean => [
                name = "1beanz"
            ]
            name = "hello, world"
            beanz = #[
                new Bean => [
                    name = "beanz1"
                ]
            ]
        ]

        assertEquals('''
            new Bean => [
                ALongObject = 123L
                AShort = 123 as short
                anInt = 123
                anInteger = 123
                beanz += #[
                    new Bean => [
                        name = "beanz1"
                    ]
                ]
                bigInteger = 456bi
                innerBean = new Bean => [
                    name = "1beanz"
                ]
                name = "hello, world"
            ]'''.toString, g.getExpression(bean))
    }

    @Test def void beanWithArrayProperty() {
        val bean = new BeanWithArrayProperty => [
            names = #[ "Michael" ]
        ]
        assertEquals('''
            new BeanWithArrayProperty => [
                names = #[
                    "Michael"
                ]
            ]'''.toString, g.getExpression(bean))
    }

    @Test def void beanWithBuilder() {
        val BeanWithBuilder bean = (new BeanWithBuilderBuilder => [
            name = "hoho"
        ]).build
        assertEquals('''
            (new BeanWithBuilderBuilder => [
                name = "hoho"
            ]).build()'''.toString, g.getExpression(bean))
    }

    @Test def void emptyBeanWithBuilder() {
        val BeanWithBuilder bean = (new BeanWithBuilderBuilder).build()
        assertEquals("(new BeanWithBuilderBuilder\n).build()", g.getExpression(bean))
    }

    @Test def void beanWithConstructor() {
        val bean = new BeanWithOneConstructor("hello, world", 123) => [
            address = "Street 1"
        ]
        assertEquals('''
            new BeanWithOneConstructor("hello, world", 123) => [
                address = "Street 1"
            ]'''.toString, g.getExpression(bean))
    }

    // This currently only works if there is a Builder for such classes
    // TODO retest in new @Test without a Builder as well; I think this may work, meanwhile?
    @Test def void beanWithMultiConstructor() {
        val bean1 = new BeanWithMultiConstructor("foobar")
        assertEquals('''
        (new BeanWithMultiConstructorBuilder => [
            name = "foobar"
        ]).build()'''.toString, g.getExpression(bean1))

        val bean2 = new BeanWithMultiConstructor(123)
        assertEquals('''
        (new BeanWithMultiConstructorBuilder => [
            id = 123
        ]).build()'''.toString, g.getExpression(bean2))
    }

    @Test def void beanWithMultiMatchingConstructors() {
        val bean = new BeanWithMultiMatchingConstructors("foobar", 123)
        assertEquals("new BeanWithMultiMatchingConstructors(\"foobar\", 123)\n", g.getExpression(bean))
    }

    @Test def void beanWithMultiConstructorArgsSameNameTypeDiff() {
        val bean = new BeanWithMultiConstructorArgsSameNameTypeDiff(1L)
        assertEquals("new BeanWithMultiConstructorArgsSameNameTypeDiff(1bi)\n", g.getExpression(bean))
    }


    @Accessors
    public static class Bean {
        String name
        int anInt
        Integer anInteger

        @Accessors(PUBLIC_GETTER) /* but no setter */ String onlyGetterString = "onlyGetterNoSetterString"

        boolean aBoolean
        Boolean aBooleanObject

        char aChar
        Character aCharacter

        short aShort
        BigInteger bigInteger
        long aLong
        Long aLongObject
        Long nullLong
        Byte aByteObject
        byte aByte
        double aDouble
        Double aDoubleObject
        float aFloat
        Float aFloatObject

        Bean innerBean
        @Accessors(PUBLIC_GETTER) List<Bean> beanz = newArrayList
    }

    @Accessors
    public static class BeanWithArrayProperty {
        String[] names;
    }

    public static class BeanWithOneConstructor {
        @Accessors(PUBLIC_GETTER) final String name
        @Accessors(PUBLIC_GETTER) final Integer age
        @Accessors String address

        @FinalFieldsConstructor
        new() { }
    }

    @Accessors
    public static class BeanWithMultiConstructorBuilder {
        String name
        Integer id
        def build() {
            if (name != null && id == null)
                new BeanWithMultiConstructor(name)
            else if (name == null && id != null)
                new BeanWithMultiConstructor(id)
            else if (name != null && id != null)
                throw new IllegalStateException("Cannot set both name and id")
            else if (name == null && id == null)
                throw new IllegalStateException("Must set either name or id")
            else
                throw new IllegalStateException("WTF?!")
        }
    }

    public static class BeanWithMultiConstructor {
        // Example bean of some structure which has either a name or an id
        // Does not necessarily make sense / is not ideal in real world, as e.g. a
        // private internal subclass with and either or, and factory methods, would be a better design,
        // but ODL has some like this, so needed and tested.

        @Accessors(PUBLIC_GETTER) final String name
        @Accessors(PUBLIC_GETTER) final Integer id

        new(String name) {
            this.name = name
            this.id = null
        }

        new(Integer id) {
            this.name = null
            this.id = id
        }

        // default constructor (does not necessarily make sense in real world, but useful for test)
        new() {
            name = null
            id = null
        }

        // clone/copy constructor (just to have another one)
        new(BeanWithMultiConstructor original) {
            this.name = original.name
            this.id = original.id
        }
    }

    public static class BeanWithMultiMatchingConstructors {
        @Accessors(PUBLIC_GETTER) final String name
        @Accessors(PUBLIC_GETTER) final Integer id

        new(String name, Integer id) {
            this.name = name
            this.id = id
        }

        new(String name) {
            this.name = name
            this.id = null
        }

        new(Integer id) {
            this.name = null
            this.id = id
        }
    }

    public static class BeanWithMultiConstructorArgsSameNameTypeDiff {
        @Accessors(PUBLIC_GETTER) final BigInteger big

        new(BigInteger big) { this.big = big }
        new(long big) { this(BigInteger.valueOf(big)) }
    }

}

/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.navigation;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.test.JUnit3RunnerWithInners;
import org.jetbrains.kotlin.test.KotlinTestUtils;
import org.jetbrains.kotlin.test.TestMetadata;
import org.jetbrains.kotlin.test.TestRoot;
import org.junit.runner.RunWith;

/*
 * This class is generated by {@link org.jetbrains.kotlin.generators.tests.TestsPackage}.
 * DO NOT MODIFY MANUALLY.
 */
@SuppressWarnings("all")
@TestRoot("idea")
@TestDataPath("$CONTENT_ROOT")
@RunWith(JUnit3RunnerWithInners.class)
@TestMetadata("testData/navigation/gotoSuper")
public class GotoSuperTestGenerated extends AbstractGotoSuperTest {
    private void runTest(String testDataFilePath) throws Exception {
        KotlinTestUtils.runTest(this::doTest, this, testDataFilePath);
    }

    @TestMetadata("BadPositionLambdaParameter.test")
    public void testBadPositionLambdaParameter() throws Exception {
        runTest("testData/navigation/gotoSuper/BadPositionLambdaParameter.test");
    }

    @TestMetadata("ClassSimple.test")
    public void testClassSimple() throws Exception {
        runTest("testData/navigation/gotoSuper/ClassSimple.test");
    }

    @TestMetadata("DelegatedFun.test")
    public void testDelegatedFun() throws Exception {
        runTest("testData/navigation/gotoSuper/DelegatedFun.test");
    }

    @TestMetadata("DelegatedProperty.test")
    public void testDelegatedProperty() throws Exception {
        runTest("testData/navigation/gotoSuper/DelegatedProperty.test");
    }

    @TestMetadata("FakeOverrideFun.test")
    public void testFakeOverrideFun() throws Exception {
        runTest("testData/navigation/gotoSuper/FakeOverrideFun.test");
    }

    @TestMetadata("FakeOverrideFunWithMostRelevantImplementation.test")
    public void testFakeOverrideFunWithMostRelevantImplementation() throws Exception {
        runTest("testData/navigation/gotoSuper/FakeOverrideFunWithMostRelevantImplementation.test");
    }

    @TestMetadata("FakeOverrideProperty.test")
    public void testFakeOverrideProperty() throws Exception {
        runTest("testData/navigation/gotoSuper/FakeOverrideProperty.test");
    }

    @TestMetadata("FunctionSimple.test")
    public void testFunctionSimple() throws Exception {
        runTest("testData/navigation/gotoSuper/FunctionSimple.test");
    }

    @TestMetadata("ObjectSimple.test")
    public void testObjectSimple() throws Exception {
        runTest("testData/navigation/gotoSuper/ObjectSimple.test");
    }

    @TestMetadata("PropertySimple.test")
    public void testPropertySimple() throws Exception {
        runTest("testData/navigation/gotoSuper/PropertySimple.test");
    }

    @TestMetadata("SuperWithNativeToGenericMapping.test")
    public void testSuperWithNativeToGenericMapping() throws Exception {
        runTest("testData/navigation/gotoSuper/SuperWithNativeToGenericMapping.test");
    }

    @TestMetadata("TraitSimple.test")
    public void testTraitSimple() throws Exception {
        runTest("testData/navigation/gotoSuper/TraitSimple.test");
    }

    @TestMetadata("TypeAliasInSuperType.test")
    public void testTypeAliasInSuperType() throws Exception {
        runTest("testData/navigation/gotoSuper/TypeAliasInSuperType.test");
    }
}

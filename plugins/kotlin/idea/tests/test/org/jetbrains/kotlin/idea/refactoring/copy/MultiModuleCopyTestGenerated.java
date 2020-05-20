/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.refactoring.copy;

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
@TestMetadata("testData/refactoring/copyMultiModule")
public class MultiModuleCopyTestGenerated extends AbstractMultiModuleCopyTest {
    private void runTest(String testDataFilePath) throws Exception {
        KotlinTestUtils.runTest(this::doTest, this, testDataFilePath);
    }

    @TestMetadata("fileNotUnderSourceRoot/fileNotUnderSourceRoot.test")
    public void testFileNotUnderSourceRoot_FileNotUnderSourceRoot() throws Exception {
        runTest("testData/refactoring/copyMultiModule/fileNotUnderSourceRoot/fileNotUnderSourceRoot.test");
    }

    @TestMetadata("internalReferencesToAnotherModule2/internalReferencesToAnotherModule.test")
    public void testInternalReferencesToAnotherModule2_InternalReferencesToAnotherModule() throws Exception {
        runTest("testData/refactoring/copyMultiModule/internalReferencesToAnotherModule2/internalReferencesToAnotherModule.test");
    }

    @TestMetadata("referencesToUnrelatedModule/referencesToUnrelatedModule.test")
    public void testReferencesToUnrelatedModule_ReferencesToUnrelatedModule() throws Exception {
        runTest("testData/refactoring/copyMultiModule/referencesToUnrelatedModule/referencesToUnrelatedModule.test");
    }
}

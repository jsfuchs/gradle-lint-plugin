/*
 * Copyright 2015-2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.TestKitSpecification
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Issue
import spock.lang.Unroll

class DuplicateDependencyClassRuleSpec extends TestKitSpecification {
    static def guava = 'com.google.guava:guava:18.0'
    static def collections = 'com.google.collections:google-collections:1.0'
    static def guava_transitive = 'com.netflix.nebula:gradle-metrics-plugin:4.1.6'
    static def asm = 'org.ow2.asm:asm:5.0.4'
    static def asm_asm = 'asm:asm:3.3.1'

    @Unroll
    def 'dependencies with duplicate classes cause violations'(List<String> deps, String message) {
        given:
        buildFile.text = """
            plugins {
                id 'java'
                id 'nebula.lint'
            }

            gradleLint.rules = ['duplicate-dependency-class']

            repositories { mavenCentral() }

            dependencies {
                ${deps.collect { "compile '$it'" }.join('\n') }
            }
        """

        when:
        createJavaSourceFile('public class Main {}')
        def result = runTasksSuccessfully('compileJava', 'lintGradle')

        then:
        result.output.contains(message)
        result.output.contains("✖ build.gradle: 1 problem (0 errors, 1 warning)")

        where:
        deps                            | message
        [guava, collections]            | "$collections in configuration 'compile' has 310 classes duplicated by $guava"
        [guava_transitive, collections] | "$collections in configuration 'compile' has 310 classes duplicated by $guava"
        [asm, asm_asm]                  | "$asm_asm in configuration 'compile' has 21 classes duplicated by $asm"
    }
    
    @Issue('47')
    def 'duplicate classes arising from different versions of the module selected in different configurations are not marked as duplicate'() {
        given:
        buildFile.text = """
            plugins {
                id 'java'
                id 'nebula.lint'
            }

            gradleLint.rules = ['duplicate-dependency-class']

            repositories { mavenCentral() }

            dependencies {
                compile 'log4j:log4j:1.2.16'
                testCompile 'org.slf4j:slf4j-log4j12:1.7.7' // transitively depends on log4j:1.2.17
            }
        """

        when:
        createJavaTestFile('public class Main {}')
        def result = runTasksSuccessfully('compileTestJava', 'lintGradle')

        then:
        !result.output.contains("log4j:log4j:1.2.16 in configuration 'compile'")
    }
    
    @Issue('42')
    def 'detect duplicate classes when dependency syntax is defined in an extension property'() {
        setup:
        buildFile.text = """
            plugins {
                id 'java'
                id 'nebula.lint'
            }

            gradleLint.rules = ['duplicate-dependency-class']
            
            repositories { mavenCentral() }
            
            ext.deps = [
                guava: '$guava',
                collections: '$collections'
            ]
            
            dependencies {
                compile deps.guava
                compile deps.collections
            }
        """
        
        when:
        createJavaSourceFile('public class Main{}')
        def result = runTasksSuccessfully('compileJava', 'lintGradle')
        
        then:
        result.output.contains("$collections in configuration 'compile' has 310 classes duplicated by $guava")
    }
}

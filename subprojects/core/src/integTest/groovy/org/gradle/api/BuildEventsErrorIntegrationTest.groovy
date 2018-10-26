/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.build.events.RootBuildCompletionListener
import spock.lang.Unroll

class BuildEventsErrorIntegrationTest extends AbstractIntegrationSpec {

    def "produces reasonable error message when taskGraph.whenReady closure fails"() {
        buildFile << """
    gradle.taskGraph.whenReady {
        throw new RuntimeException('broken')
    }
    task a
"""

        when:
        fails()

        then:
        failure.assertHasDescription("broken")
                .assertHasNoCause()
                .assertHasFileName("Build file '$buildFile'")
                .assertHasLineNumber(3);
    }

    def "produces reasonable error message when taskGraph.whenReady action fails"() {
        buildFile << """
    def action = {
            throw new RuntimeException('broken')
    } as Action
    gradle.taskGraph.whenReady(action) 
    task a
"""

        when:
        fails()

        then:
        failure.assertHasDescription("broken")
                .assertHasNoCause()
                .assertHasFileName("Build file '$buildFile'")
                .assertHasLineNumber(3);
    }

    def "produces reasonable error message when taskGraph listener fails"() {
        buildFile << """
    def listener = {
            throw new RuntimeException('broken')
    } as TaskExecutionGraphListener
    gradle.taskGraph.addTaskExecutionGraphListener(listener) 
    task a
"""

        when:
        fails()

        then:
        failure.assertHasDescription("broken")
                .assertHasNoCause()
                .assertHasFileName("Build file '$buildFile'")
                .assertHasLineNumber(3);
    }

    def "produces reasonable error when Gradle.allprojects action fails"() {
        def initScript = file("init.gradle") << """
allprojects {
    throw new RuntimeException("broken")
}
"""
        when:
        executer.usingInitScript(initScript)
        fails "a"

        then:
        failure.assertHasDescription("broken")
                .assertHasNoCause()
                .assertHasFileName("Initialization script '$initScript'")
                .assertHasLineNumber(3);
    }

    @Unroll
    def "produces reasonable error when Gradle.#method closure fails"() {
        settingsFile << """
gradle.${method} {
    throw new RuntimeException("broken")
}
gradle.rootProject { task a }
"""
        when:
        fails "a"

        then:
        failure.assertHasDescription("broken")
                .assertHasNoCause()
        // TODO - include location information for buildFinished failure
        if (hasLocation) {
            failure.assertHasFileName("Settings file '$settingsFile'")
                    .assertHasLineNumber(3)
        }

        where:
        method              | hasLocation
        "settingsEvaluated" | true
        "projectsLoaded"    | true
        "projectsEvaluated" | true
        "buildFinished"     | false
    }

    @Unroll
    def "produces reasonable error when Gradle.#method action fails"() {
        settingsFile << """
def action = {
    throw new RuntimeException("broken")
} as Action
gradle.${method}(action)
gradle.rootProject { task a }
"""
        when:
        fails "a"

        then:
        failure.assertHasDescription("broken")
                .assertHasNoCause()
        // TODO - include location information for buildFinished failure
        if (hasLocation) {
            failure.assertHasFileName("Settings file '$settingsFile'")
                    .assertHasLineNumber(3)
        }

        where:
        method              | hasLocation
        "settingsEvaluated" | true
        "projectsLoaded"    | true
        "projectsEvaluated" | true
        "buildFinished"     | false
    }

    @Unroll
    def "produces reasonable error when BuildListener.#method method fails"() {
        settingsFile << """
def listener = new BuildAdapter() {
    @Override
    void ${method}(${params}) {
        throw new RuntimeException("broken")
    }
}

gradle.addListener(listener)
gradle.rootProject { task a }
"""
        when:
        fails "a"

        then:
        failure.assertHasDescription("broken")
                .assertHasNoCause()
        // TODO - include location information for buildFinished failure
        if (hasLocation) {
            failure.assertHasFileName("Settings file '$settingsFile'")
                    .assertHasLineNumber(5)
        }

        where:
        method              | params               | hasLocation
        "settingsEvaluated" | "Settings settings"  | true
        "projectsLoaded"    | "Gradle gradle"      | true
        "projectsEvaluated" | "Gradle gradle"      | true
        "buildFinished"     | "BuildResult result" | false
    }

    def "fires internal event on completion of buildFinished events"() {
        buildFile << """
            import ${RootBuildCompletionListener.name}
            def listener = {
                println "post build"
            } as RootBuildCompletionListener
            gradle.addListener(listener)
            gradle.buildFinished {
                println "build finished"
            }
        """

        expect:
        succeeds()

        and:
        outputContains("build finished")
        result.assertHasPostBuildOutput("post build")
    }

    def "fires internal event on completion after failed buildFinished events"() {
        buildFile << """
            import ${RootBuildCompletionListener.name}
            def listener = {
                println "post build"
            } as RootBuildCompletionListener
            gradle.addListener(listener)
            gradle.buildFinished {
                println "breaking"
                throw new RuntimeException("broken")
            }
        """

        expect:
        fails()

        and:
        failure.assertHasDescription("broken")
        result.output.indexOf("post build") > result.output.indexOf("breaking")
    }
}

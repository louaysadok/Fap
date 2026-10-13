package com.fap.core

/**
 * Featherweight test harness.
 *
 * The core module has zero dependencies on purpose, so instead of JUnit these
 * tests are plain Kotlin that can be executed anywhere (`ParserTestMain`), and
 * are wired into Gradle as a JavaExec task (`:core:parserSelfTest`).
 */
public object TestHarness {

    private val failures = ArrayList<String>()
    private var checks = 0
    private var current = ""

    public fun suite(name: String, body: () -> Unit) {
        current = name
        println("── $name")
        try {
            body()
        } catch (e: Throwable) {
            failures.add("$name threw ${e::class.simpleName}: ${e.message}")
            println("   ✗ threw ${e::class.simpleName}: ${e.message}")
        }
    }

    public fun check(name: String, condition: Boolean, detail: () -> String = { "" }) {
        checks++
        if (condition) {
            println("   ✓ $name")
        } else {
            val extra = try { detail() } catch (e: Throwable) { "" }
            failures.add("$current › $name${if (extra.isNotBlank()) " — $extra" else ""}")
            println("   ✗ $name${if (extra.isNotBlank()) " — $extra" else ""}")
        }
    }

    public fun <T> eq(name: String, expected: T, actual: T) {
        check(name, expected == actual) { "expected <$expected> but was <$actual>" }
    }

    public fun summary(): Int {
        println()
        if (failures.isEmpty()) {
            println("ALL GREEN — $checks checks passed")
            return 0
        }
        println("${failures.size} of $checks checks FAILED:")
        failures.forEach { println("  • $it") }
        return 1
    }
}

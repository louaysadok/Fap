package com.fap.core

/**
 * Entry point for `:core:parserSelfTest` (Gradle JavaExec) and for running the
 * scraper tests straight from a terminal with kotlinc.
 */
public fun main() {
    ParserTest.run()
    ClientTest.run()
    SearchTagsTest.run()
    val failures = TestHarness.summary()
    if (failures != 0) {
        throw AssertionError("$failures checks failed")
    }
}

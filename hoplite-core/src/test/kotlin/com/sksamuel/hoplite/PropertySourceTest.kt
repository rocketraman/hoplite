package com.sksamuel.hoplite

import com.sksamuel.hoplite.sources.EnvironmentVariablesPropertySource
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.system.withEnvironment
import io.kotest.matchers.shouldBe

class PropertySourceTest : FunSpec() {
  init {

    test("reads config from string") {
      data class TestConfig(val a: String, val b: Int)

      val config = ConfigLoaderBuilder.default()
        .addPropertySource(
          PropertySource.string(
            """
          a = A value
          b = 42
          """.trimIndent(), "props"
          )
        )
        .build()
        .loadConfigOrThrow<TestConfig>()

      config shouldBe TestConfig("A value", 42)
    }

    test("reads config from input stream") {
      data class TestConfig(val a: String, val b: Int)

      val stream = """
          a = A value
          b = 42
          """.trimIndent().byteInputStream(Charsets.UTF_8)

      val config = ConfigLoaderBuilder.default()
        .addPropertySource(PropertySource.stream(stream, "props"))
        .build()
        .loadConfigOrThrow<TestConfig>()

      config shouldBe TestConfig("A value", 42)
    }

    test("reads config from map") {
      data class TestConfig(val a: String, val b: Int, val other: List<String>)

      val arguments = mapOf(
        "a" to "A value",
        "b" to "42",
        "other" to listOf("Value1", "Value2")
      )

      val config = ConfigLoaderBuilder.default()
        .addPropertySource(PropertySource.map(arguments))
        .build()
        .loadConfigOrThrow<TestConfig>()

      config shouldBe TestConfig("A value", 42, listOf("Value1", "Value2"))
    }

    test("reads config from command line") {
      data class TestConfig(val a: String, val b: Int, val other: List<String>)

      val arguments = arrayOf(
        "--a=A value",
        "--b=42",
        "some other value",
        "--other=Value1",
        "--other=Value2"
      )

      val config = ConfigLoaderBuilder.default()
        .addPropertySource(PropertySource.commandLine(arguments))
        .build()
        .loadConfigOrThrow<TestConfig>()

      config shouldBe TestConfig("A value", 42, listOf("Value1", "Value2"))
    }

    test("reads from added source before default sources") {
      data class TestConfig(val a: String, val b: Int, val other: List<String>)

      withEnvironment(mapOf("b" to "91", "other" to "Random13")) {

        val arguments = arrayOf(
          "--a=A value",
          "--b=42",
          "some other value",
          "--other=Value1",
          "--other=Value2"
        )

        val config = ConfigLoaderBuilder.default()
          .addPropertySource(PropertySource.commandLine(arguments))
          .addDefaultPropertySources()
          .addEnvironmentSource()
          .build()
          .loadConfigOrThrow<TestConfig>()

        config shouldBe TestConfig("A value", 42, listOf("Value1", "Value2"))
      }
    }

    test("reads from default source before specified") {
      data class TestConfig(val a: String, val b: Int, val other: List<String>)

      withEnvironment(mapOf("b" to "91", "other" to "Random13")) {
        val arguments = arrayOf(
          "--a=A value",
          "--b=42",
          "some other value",
          "--other=Value1",
          "--other=Value2"
        )

        val config = ConfigLoaderBuilder.default()
          .addEnvironmentSource()
          .addDefaultPropertySources()
          .addPropertySource(PropertySource.commandLine(arguments))
          .build()
          .loadConfigOrThrow<TestConfig>()

        config shouldBe TestConfig("A value", 91, listOf("Random13"))
      }
    }

    test("maps absolute config alias to env var") {
      data class TestConfig(@ConfigAlias("ENV_VAL") val string: String)

      val config = ConfigLoaderBuilder.default()
        .addPropertySource(EnvironmentVariablesPropertySource(
          useUnderscoresAsSeparator = false,
          allowUppercaseNames = false,
          environmentVariableMap = { mapOf("ENV_VAL" to "foo") }
        ))
        .addDefaultPropertySources()
        .build()
        .loadConfigOrThrow<TestConfig>()

      config shouldBe TestConfig("foo")
    }

    test("maps absolute nested config alias to env var") {
      data class SomeOptions(@ConfigAlias("ENV_VAL") val string: String)
      data class TestConfig(val options: SomeOptions)

      val config = ConfigLoaderBuilder.default()
        .addPropertySource(EnvironmentVariablesPropertySource(
          useUnderscoresAsSeparator = false,
          allowUppercaseNames = false,
          environmentVariableMap = { mapOf("ENV_VAL" to "foo") }
        ))
        .addDefaultPropertySources()
        .build()
        .loadConfigOrThrow<TestConfig>()

      config shouldBe TestConfig(options = SomeOptions("foo"))
    }

    test("maps absolute second-level nested config alias to env var") {
      data class SomeOtherOptions(@ConfigAlias("ENV_VAL2") val options: String, val opt1: String)
      data class SomeOptions(val options: SomeOtherOptions, @ConfigAlias("ENV_VAL1") val value: String)
      data class TestConfig(val options: SomeOptions)

      val config = ConfigLoaderBuilder.default()
        .addPropertySource(EnvironmentVariablesPropertySource(
          useUnderscoresAsSeparator = false,
          allowUppercaseNames = false,
          environmentVariableMap = { mapOf("ENV_VAL1" to "foo", "ENV_VAL2" to "bar", "options.options.opt1" to "baz") }
        ))
        .addDefaultPropertySources()
        .build()
        .loadConfigOrThrow<TestConfig>()

      config shouldBe TestConfig(options = SomeOptions(options = SomeOtherOptions(options = "bar", opt1 = "baz"), value = "foo"))
    }
  }
}

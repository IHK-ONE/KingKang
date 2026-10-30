package com.kingkang.app.agent

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentSettingsParserTest {

    private fun describe(writable: Boolean = true): JSONObject = JSONObject(
        """
        {
          "writable": $writable,
          "hasDocument": true,
          "namespaces": [
            {
              "ns": "web-search-deepseek",
              "revision": 4,
              "value": { "apiKey": "", "maxTokens": 4096, "enabled": true },
              "secrets": [ { "path": ["apiKey"], "set": true } ],
              "schema": {
                "uid": "0",
                "refs": {
                  "0": { "type": "object", "dict": {
                    "apiKey": 1, "maxTokens": 2, "enabled": 3, "providers": 4
                  } },
                  "1": { "type": "string", "meta": { "role": "secret" } },
                  "2": { "type": "number", "meta": { "min": 1, "max": 8192, "step": 1 } },
                  "3": { "type": "boolean", "meta": {} },
                  "4": { "type": "array", "meta": {}, "inner": 5 },
                  "5": { "type": "string", "meta": {} }
                }
              }
            },
            {
              "ns": "bash-sandbox",
              "revision": 1,
              "value": { "mode": "light" },
              "schema": {
                "uid": "0",
                "refs": {
                  "0": { "type": "object", "dict": { "mode": 1 } },
                  "1": { "type": "union", "meta": {}, "list": [2, 3] },
                  "2": { "type": "const", "meta": {}, "value": "light" },
                  "3": { "type": "const", "meta": {}, "value": "dark" }
                }
              }
            },
            {
              "ns": "ui-theme",
              "revision": 0,
              "value": { "preference": "dark" },
              "schema": { "uid": "0", "refs": { "0": { "type": "object", "dict": {} } } }
            },
            {
              "ns": "locale",
              "revision": 2,
              "value": { "preference": "en" },
              "schema": { "uid": "0", "refs": { "0": { "type": "object", "dict": {} } } }
            },
            {
              "ns": "extra-ns",
              "revision": 0,
              "value": {},
              "schema": { "uid": "0", "refs": { "0": { "type": "object", "dict": {} } } }
            }
          ]
        }
        """.trimIndent()
    )

    private fun AgentSettingGroup.field(key: String): AgentSettingField =
        fields.first { it.key == key }

    @Test
    fun `only curated namespaces become native groups`() {
        val snapshot = AgentSettingsParser.parse(describe())
        assertTrue(snapshot.loaded)
        assertTrue(snapshot.writable)
        assertEquals(listOf("web-search-deepseek", "bash-sandbox"), snapshot.groups.map { it.ns })
        assertEquals(1, snapshot.harnessOnlyCount)
    }

    @Test
    fun `secret fields start blank and report whether a value is stored`() {
        val group = AgentSettingsParser.parse(describe()).groups.first()
        val secret = group.field("apiKey")
        assertEquals(AgentSettingKind.Secret, secret.kind)
        assertEquals("", secret.text)
        assertTrue(secret.configured)
        assertEquals("API Key", secret.label)
        assertEquals(4, group.revision)
    }

    @Test
    fun `scalars keep their values and schema bounds`() {
        val group = AgentSettingsParser.parse(describe()).groups.first()
        val tokens = group.field("maxTokens")
        assertEquals(AgentSettingKind.Number, tokens.kind)
        assertEquals("4096", tokens.text)
        assertEquals(1.0, tokens.min!!, 0.0)
        assertEquals(8192.0, tokens.max!!, 0.0)
        assertEquals(1.0, tokens.step!!, 0.0)
        assertTrue(group.field("enabled").checked)
        assertEquals(AgentSettingKind.Toggle, group.field("enabled").kind)
    }

    @Test
    fun `structured fields stay visible but point at the Harness`() {
        val group = AgentSettingsParser.parse(describe()).groups.first()
        val providers = group.field("providers")
        assertEquals(AgentSettingKind.Unsupported, providers.kind)
        assertTrue(providers.unsupportedReason!!.contains("Harness"))
    }

    @Test
    fun `union of literals becomes a labeled choice`() {
        val group = AgentSettingsParser.parse(describe()).groups.first { it.ns == "bash-sandbox" }
        val mode = group.field("mode")
        assertEquals(AgentSettingKind.Choice, mode.kind)
        assertEquals("light", mode.text)
        assertEquals(listOf("light", "dark"), mode.options.map { it.value })
        assertEquals(listOf("浅色", "深色"), mode.options.map { it.label })
    }

    @Test
    fun `read-only profiles turn every editable field into a Harness pointer`() {
        val snapshot = AgentSettingsParser.parse(describe(writable = false))
        assertFalse(snapshot.writable)
        val group = snapshot.groups.first()
        assertTrue(group.fields.all { it.kind == AgentSettingKind.Unsupported })
        assertEquals("当前 dsh 配置不可写", group.field("maxTokens").unsupportedReason)
        // The secret value still never leaks into the model.
        assertEquals("", group.field("apiKey").text)
    }

    @Test
    fun `language and theme read the dedicated namespaces`() {
        val language = AgentSettingsParser.parseLanguage(describe())
        assertTrue(language.loaded)
        assertTrue(language.writable)
        assertEquals("en", language.value)
        assertEquals(2, language.revision)
        assertEquals("dark", AgentSettingsParser.parseTheme(describe()))
    }

    @Test
    fun `missing locale leaves the Harness on its default`() {
        val empty = JSONObject("""{"writable": true, "namespaces": []}""")
        val language = AgentSettingsParser.parseLanguage(empty)
        assertTrue(language.loaded)
        assertNull(language.value)
        assertNull(AgentSettingsParser.parseTheme(empty))
    }

    @Test
    fun `mutated view parses only curated namespaces`() {
        val view = JSONObject(
            """
            {
              "ns": "bash-sandbox",
              "revision": 5,
              "value": { "mode": "dark" },
              "schema": {
                "uid": "0",
                "refs": {
                  "0": { "type": "object", "dict": { "mode": 1 } },
                  "1": { "type": "union", "list": [2, 3] },
                  "2": { "type": "const", "value": "light" },
                  "3": { "type": "const", "value": "dark" }
                }
              }
            }
            """.trimIndent()
        )
        val group = AgentSettingsParser.parseMutated(view, globalWritable = true)!!
        assertEquals(5, group.revision)
        assertEquals("dark", group.field("mode").text)
        assertNull(AgentSettingsParser.parseMutated(view.put("ns", "other"), true))
    }

    @Test
    fun `schema-less namespace says so instead of rendering an empty form`() {
        val view = JSONObject("""{"ns": "web-search-deepseek", "revision": 0, "value": {}}""")
        val group = AgentSettingsParser.parseMutated(view, globalWritable = true)!!
        assertTrue(group.fields.isEmpty())
        assertTrue(group.note!!.contains("Harness"))
    }

    @Test
    fun `number validation respects bounds, steps and required`() {
        val field = AgentSettingField(
            path = listOf("maxTokens"),
            label = "最大输出 Tokens",
            kind = AgentSettingKind.Number,
            required = true,
            min = 1.0,
            max = 100.0,
            step = 1.0
        )
        assertEquals("必填", field.validate())
        assertEquals("请输入数字", field.copy(text = "abc").validate())
        assertEquals("不能小于 1", field.copy(text = "0").validate())
        assertEquals("不能大于 100", field.copy(text = "101").validate())
        assertEquals("需为 1 的倍数", field.copy(text = "1.5").validate())
        assertNull(field.copy(text = "42").validate())
    }
}

package com.mtzallqmy.agentna.runtime

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalToolRegistryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun preventsPathTraversalOutsideWorkspace() {
        val tools = LocalToolRegistry(context)
        val result = tools.execute("workspace.read", JSONObject().put("path", "../../shared_prefs/secret.xml"))
        assertTrue(result is ToolResult.Failure)
    }

    @Test fun workspaceRootCannotBeDeleted() {
        val tools = LocalToolRegistry(context)
        val result = tools.execute("workspace.delete", JSONObject().put("path", "."), approved = true)
        assertTrue(result is ToolResult.Failure)
    }

    @Test fun overwriteRequiresApproval() {
        val tools = LocalToolRegistry(context)
        val name = "test-${System.nanoTime()}.txt"
        assertTrue(tools.execute("workspace.write", JSONObject().put("path", name).put("content", "one")) is ToolResult.Success)
        val overwrite = tools.execute("workspace.write", JSONObject().put("path", name).put("content", "two"))
        assertTrue(overwrite is ToolResult.RequiresApproval)
    }
}

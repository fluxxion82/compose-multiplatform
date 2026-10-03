package org.jetbrains.compose.resources.vector.xmldom

import kotlin.test.Test
import kotlin.test.assertEquals

class KotlinXmlParserTest {
    @Test
    fun inheritsRootNamespacesForNestedVectorElements() {
        val root = parse(
            """
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:aapt="http://schemas.android.com/aapt">
                <group android:name="nested" android:rotation="15">
                    <path android:pathData="M0,0L1,1">
                        <aapt:attr name="android:fillColor">
                            <gradient android:type="linear" android:startColor="#000000">
                                <item android:color="#ffffff" android:offset="1" />
                            </gradient>
                        </aapt:attr>
                    </path>
                </group>
            </vector>
            """.trimIndent()
        )

        val group = root.child(0)
        val path = group.child(0)
        val gradient = path.child(0).child(0)
        val item = gradient.child(0)

        assertEquals("nested", group.getAttributeNS(ANDROID_NS, "name"))
        assertEquals("15", group.getAttributeNS(ANDROID_NS, "rotation"))
        assertEquals("M0,0L1,1", path.getAttributeNS(ANDROID_NS, "pathData"))
        assertEquals("linear", gradient.getAttributeNS(ANDROID_NS, "type"))
        assertEquals("#000000", gradient.getAttributeNS(ANDROID_NS, "startColor"))
        assertEquals("#ffffff", item.getAttributeNS(ANDROID_NS, "color"))
        assertEquals("1", item.getAttributeNS(ANDROID_NS, "offset"))
    }

    @Test
    fun localNamespaceDeclarationShadowsInheritedNamespace() {
        val root = parse(
            """
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                    android:name="root">
                <group xmlns:android="urn:local" android:name="local">
                    <path android:pathData="M0,0L1,1" />
                </group>
            </vector>
            """.trimIndent()
        )

        val group = root.child(0)
        val path = group.child(0)

        assertEquals("root", root.getAttributeNS(ANDROID_NS, "name"))
        assertEquals("local", group.getAttributeNS("urn:local", "name"))
        assertEquals("M0,0L1,1", path.getAttributeNS("urn:local", "pathData"))
        assertEquals("", path.getAttributeNS(ANDROID_NS, "pathData"))
    }

    private fun Element.child(index: Int): Element = childNodes.item(index) as Element

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}

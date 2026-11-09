package cc.slokker.goatlauncher

import cc.slokker.goatlauncher.data.Updater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Os nomes que o sistema e as outras apps guardam ou chamam: o pacote, os componentes do manifesto e os extras. Um
 * nome que mude aqui não dá erro nenhum, só deixa de funcionar em quem já tinha a app instalada:
 *  - o MainActivity é o ecrã inicial escolhido pela pessoa (e o `set-home-activity` do adb);
 *  - o HomeGuardService fica gravado pelo nome em ENABLED_ACCESSIBILITY_SERVICES: mudar o nome da classe desliga-o
 *    sem aviso em todas as TVs;
 *  - o AmbientDream é o protetor de ecrã escolhido nas definições do Android.
 */
class ManifestContractTest {
    private val ns = "http://schemas.android.com/apk/res/android"
    private val manifest: Element = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(File("src/main/AndroidManifest.xml")).documentElement

    private fun Element.children(tag: String): List<Element> =
        (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>().filter { it.tagName == tag }

    private fun Element.attr(name: String): String = getAttributeNS(ns, name)

    private fun component(tag: String, name: String): Element =
        manifest.children("application").single().children(tag).single { it.attr("name") == name }

    private fun Element.filterValues(tag: String): Set<String> =
        children("intent-filter").flatMap { it.children(tag) }.map { it.attr("name") }.toSet()

    @Test
    fun packageAndExtras() {
        assertEquals("cc.slokker.goatlauncher", BuildConfig.APPLICATION_ID)
        // Pedido pelo HomeGuardService e pelo /open do GoatRemote (AutoStart.unlockIntent).
        assertEquals("cc.slokker.goatlauncher.UNLOCK", AutoStart.EXTRA_UNLOCK)
    }

    @Test
    fun homeActivity() {
        assertEquals("cc.slokker.goatlauncher.MainActivity", MainActivity::class.java.name)
        val main = component("activity", ".MainActivity")
        assertEquals("true", main.attr("exported"))
        assertEquals("singleTask", main.attr("launchMode"))
        assertEquals(setOf("android.intent.action.MAIN"), main.filterValues("action"))
        assertEquals(
            setOf("android.intent.category.HOME", "android.intent.category.DEFAULT", "android.intent.category.LEANBACK_LAUNCHER"),
            main.filterValues("category"),
        )
    }

    @Test
    fun servicesKeptByNameInTheSystemSettings() {
        assertEquals("cc.slokker.goatlauncher.HomeGuardService", HomeGuardService::class.java.name)
        val guard = component("service", ".HomeGuardService")
        assertEquals("android.permission.BIND_ACCESSIBILITY_SERVICE", guard.attr("permission"))
        assertEquals(setOf("android.accessibilityservice.AccessibilityService"), guard.filterValues("action"))
        assertEquals("android.accessibilityservice", guard.children("meta-data").single().attr("name"))

        assertEquals("cc.slokker.goatlauncher.AmbientDream", AmbientDream::class.java.name)
        val dream = component("service", ".AmbientDream")
        assertEquals("android.permission.BIND_DREAM_SERVICE", dream.attr("permission"))
        assertEquals(setOf("android.service.dreams.DreamService"), dream.filterValues("action"))
    }

    @Test
    fun updateReceivers() {
        assertEquals("cc.slokker.goatlauncher.InstallResultReceiver", InstallResultReceiver::class.java.name)
        assertEquals("false", component("receiver", ".InstallResultReceiver").attr("exported"))
        assertEquals("cc.slokker.goatlauncher.PackageReplacedReceiver", PackageReplacedReceiver::class.java.name)
        assertEquals(setOf("android.intent.action.MY_PACKAGE_REPLACED"), component("receiver", ".PackageReplacedReceiver").filterValues("action"))
    }

    @Test
    fun watchNextNeedsTheTvListingsPermission() {
        // Sem ela o TvProvider só devolve os programas da própria app: o "Continuar a ver" do GoatTV desaparecia.
        val permissions = manifest.children("uses-permission").map { it.attr("name") }
        assertTrue("android.permission.READ_TV_LISTINGS" in permissions)
        val queries = manifest.children("queries").single()
        assertTrue(queries.children("provider").any { it.attr("authorities") == "android.media.tv" })
    }

    @Test
    fun updatesComeFromThePublicReleases() {
        // Versões em github.com/GOAThings/GoatLauncher/releases: etiqueta "v<versão>", o primeiro .apk.
        assertEquals("GOAThings/GoatLauncher", Updater.RELEASES_REPO)
        assertTrue(Updater.isNewer("0.10.0", "0.9.11"))
        assertTrue(Updater.isNewer("v0.9.12", "0.9.11"))
        assertTrue(Updater.isNewer("1.0", "0.9.11"))
        assertEquals(false, Updater.isNewer("0.9.11", "0.9.11"))
        assertEquals(false, Updater.isNewer("0.9.9", "0.9.11"))
        assertEquals(false, Updater.isNewer("0.9.11-beta", "0.9.11"))
    }
}

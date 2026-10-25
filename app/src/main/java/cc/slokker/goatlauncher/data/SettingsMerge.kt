package cc.slokker.goatlauncher.data

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject

/**
 * Gravar as definições sem desfazer o que mudou noutro sítio (site ou outra TV): a app manda só o que mudou,
 * como JSON Merge Patch (RFC 7396), em `account_merge` / `device_merge` (ver PATCH /api/launcher/settings).
 * Igual ao da GoatTV.
 */
object SettingsMerge {

    /**
     * O patch que leva de [base] a [target]: as chaves mudadas; nos objetos, só as sub-chaves mudadas; as chaves
     * apagadas vão a null. Arrays e valores vão inteiros.
     */
    fun diff(base: JsonObject, target: JsonObject): JsonObject {
        val patch = JsonObject()
        for ((key, value) in target.entrySet()) {
            val old = base.get(key)
            when {
                old == value -> Unit
                old is JsonObject && value is JsonObject -> diff(old, value).takeIf { it.size() > 0 }?.let { patch.add(key, it) }
                else -> patch.add(key, value.deepCopy())
            }
        }
        for (key in base.keySet()) if (!target.has(key)) patch.add(key, JsonNull.INSTANCE)
        return patch
    }

    /** RFC 7396: os objetos juntam-se chave a chave, null apaga, o resto substitui. */
    fun apply(base: JsonObject, patch: JsonObject): JsonObject {
        val result = base.deepCopy()
        for ((key, value) in patch.entrySet()) {
            val old = result.get(key)
            when {
                value.isJsonNull -> result.remove(key)
                value is JsonObject -> result.add(key, apply(old as? JsonObject ?: JsonObject(), value))
                else -> result.add(key, value.deepCopy())
            }
        }
        return result
    }

    /**
     * As alterações locais ([local] em relação a [base]) postas por cima do que o servidor tem agora ([server]).
     * Os arrays de valores simples do primeiro nível são conjuntos (ex.: `hidden`, `locked`): em vez de os
     * substituir, tiram-se e juntam-se só os valores que mudaram aqui, para não apagar os que o site juntou
     * entretanto. As chaves em [ordered] (ex.: `apps`, que é uma ordem) vão inteiras: ganha a ordem desta TV.
     */
    fun rebase(server: JsonObject, base: JsonObject, local: JsonObject, ordered: Set<String> = emptySet()): JsonObject {
        val patch = diff(base, local)
        for ((key, value) in patch.entrySet().toList()) {
            if (key in ordered) continue
            // Uma chave que ainda não existia aqui conta como conjunto vazio.
            val old = base.get(key) ?: JsonArray()
            if (value is JsonArray && old is JsonArray && value.isPrimitives() && old.isPrimitives()) {
                val added = value.filter { it !in old }
                val removed = old.filter { it !in value }.toSet()
                val merged = JsonArray()
                (server.get(key) as? JsonArray)?.filter { it !in removed }?.forEach { merged.add(it) }
                added.filter { it !in merged }.forEach { merged.add(it) }
                patch.add(key, merged)
            }
        }
        return apply(server, patch)
    }

    private fun JsonArray.isPrimitives() = all { it.isJsonPrimitive }
}

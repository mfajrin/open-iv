package com.example.core

import java.util.Locale

data class JOAATResult(
    val input: String,
    val normalized: String,
    val unsignedHash: Long,
    val signedHash: Int,
    val hexUpper: String
)

object JoaatHasher {

    fun hash(input: String, forceLowercase: Boolean = true): JOAATResult {
        val normalized = if (forceLowercase) input.lowercase(Locale.US) else input
        var hash = 0L
        for (ch in normalized) {
            hash = (hash + ch.code.toLong()) and 0xFFFFFFFFL
            hash = (hash + ((hash shl 10) and 0xFFFFFFFFL)) and 0xFFFFFFFFL
            hash = hash xor (hash ushr 6)
        }
        hash = (hash + ((hash shl 3) and 0xFFFFFFFFL)) and 0xFFFFFFFFL
        hash = hash xor (hash ushr 11)
        hash = (hash + ((hash shl 15) and 0xFFFFFFFFL)) and 0xFFFFFFFFL

        val signed = hash.toInt()
        val hex = String.format(Locale.US, "0x%08X", hash)
        return JOAATResult(
            input = input,
            normalized = normalized,
            unsignedHash = hash,
            signedHash = signed,
            hexUpper = hex
        )
    }

    val knownGtaDictionary: List<Pair<String, String>> = listOf(
        "adder" to "Kendaraan Super (Truffade Adder)",
        "zentorno" to "Kendaraan Super (Pegassi Zentorno)",
        "t20" to "Kendaraan Super (Progen T20)",
        "kuruma" to "Kendaraan Sport (Karin Kuruma)",
        "kuruma2" to "Kendaraan Armored (Kuruma Armored)",
        "sultanrs" to "Kendaraan Super (Karin Sultan RS)",
        "elegy2" to "Kendaraan Sport (Annis Elegy RH8)",
        "police" to "Kendaraan Polisi (Stanier Cruiser)",
        "police2" to "Kendaraan Polisi (Buffalo Cruiser)",
        "police3" to "Kendaraan Polisi (Interceptor)",
        "lazer" to "Pesawat Tempur (P-996 Lazer)",
        "hydra" to "Pesawat VTOL (Mammoth Hydra)",
        "buzzard" to "Helikopter (Nagasaki Buzzard)",
        "oppressor2" to "Motor Terbang (Pegassi Oppressor Mk II)",
        "mp_m_freemode_01" to "Ped Karakter Online Pria",
        "mp_f_freemode_01" to "Ped Karakter Online Wanita",
        "player_zero" to "Ped Protaganis (Michael De Santa)",
        "player_one" to "Ped Protaganis (Franklin Clinton)",
        "player_two" to "Ped Protaganis (Trevor Philips)",
        "weapon_pistol" to "Senjata (Pistol)",
        "weapon_carbinerifle" to "Senjata (Carbine Rifle)",
        "weapon_assaultrifle" to "Senjata (Assault Rifle)",
        "handling.meta" to "Konfigurasi Fisika Kendaraan",
        "vehicles.meta" to "Definisi Model & Audio Kendaraan",
        "dlclist.xml" to "Daftar DLC Pack GTA V"
    )

    fun lookupByHexOrName(query: String): List<Pair<JOAATResult, String>> {
        val trimmed = query.trim()
        return knownGtaDictionary.mapNotNull { (key, desc) ->
            val res = hash(key)
            if (trimmed.isEmpty() ||
                key.contains(trimmed, ignoreCase = true) ||
                desc.contains(trimmed, ignoreCase = true) ||
                res.hexUpper.contains(trimmed, ignoreCase = true) ||
                res.unsignedHash.toString().contains(trimmed)
            ) {
                res to desc
            } else {
                null
            }
        }
    }
}

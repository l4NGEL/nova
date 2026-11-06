package com.l4ngel.ritim.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Tüm veriyi tek JSON dosyasına yazar / okur. Biçim sürümlü; ileride alan eklenirse
 * eski yedekler eksik alanları varsayılanla açılır.
 */
object Backup {
    /** v2: alışkanlık puanı, ödüller ve alınan ödüller; v3: gök cisimleri. Eski yedekler eksik kısımlar boş açılır. */
    const val VERSION = 3

    class Content(
        val habits: List<HabitEntity>,
        val checks: List<CheckEntity>,
        val rewards: List<RewardEntity> = emptyList(),
        val redemptions: List<RedemptionEntity> = emptyList(),
        val sky: List<SkyObjectEntity> = emptyList(),
    )

    fun encode(content: Content): String {
        val (habits, checks) = content.habits to content.checks
        val h = JSONArray()
        for (e in habits) {
            h.put(
                JSONObject()
                    .put("id", e.id)
                    .put("name", e.name)
                    .put("icon", e.icon)
                    .put("color", e.color)
                    .put("scheduleType", e.scheduleType)
                    .put("weekdayMask", e.weekdayMask)
                    .put("timesPerWeek", e.timesPerWeek)
                    .put("target", e.target)
                    .put("unit", e.unit)
                    .put("reminderMinute", e.reminderMinute ?: JSONObject.NULL)
                    .put("createdEpochDay", e.createdEpochDay)
                    .put("archived", e.archived)
                    .put("sortOrder", e.sortOrder)
                    .put("note", e.note)
                    .put("points", e.points),
            )
        }
        val c = JSONArray()
        for (e in checks) c.put(JSONArray().put(e.habitId).put(e.epochDay).put(e.value))
        val r = JSONArray()
        for (e in content.rewards) {
            r.put(
                JSONObject().put("id", e.id).put("name", e.name).put("emoji", e.emoji)
                    .put("cost", e.cost).put("repeatable", e.repeatable).put("sortOrder", e.sortOrder),
            )
        }
        val rd = JSONArray()
        for (e in content.redemptions) {
            rd.put(
                JSONObject().put("id", e.id).put("rewardId", e.rewardId ?: JSONObject.NULL).put("name", e.name)
                    .put("emoji", e.emoji).put("cost", e.cost).put("epochDay", e.epochDay).put("at", e.at),
            )
        }
        val sky = JSONArray()
        for (e in content.sky) {
            sky.put(
                JSONObject().put("id", e.id).put("kind", e.kind).put("color", e.color).put("name", e.name)
                    .put("x", e.x.toDouble()).put("y", e.y.toDouble()).put("cost", e.cost).put("epochDay", e.epochDay),
            )
        }
        return JSONObject()
            .put("app", "ritim")
            .put("version", VERSION)
            .put("habits", h)
            .put("checks", c)
            .put("rewards", r)
            .put("redemptions", rd)
            .put("sky", sky)
            .toString(2)
    }

    /** Bozuk ya da başka uygulamanın dosyasıysa anlaşılır mesajla [IllegalArgumentException]. */
    fun decode(json: String): Content {
        val root = try { JSONObject(json) } catch (e: Exception) { throw IllegalArgumentException("Dosya okunamadı: geçerli bir yedek değil.") }
        require(root.optString("app") == "ritim") { "Bu dosya bir Ritim yedeği değil." }
        require(root.optInt("version", 0) in 1..VERSION) { "Bu yedek daha yeni bir sürümle alınmış." }
        val h = root.getJSONArray("habits")
        val habits = (0 until h.length()).map { i ->
            val o = h.getJSONObject(i)
            HabitEntity(
                id = o.getLong("id"),
                name = o.getString("name"),
                icon = o.optString("icon", "star"),
                color = o.getInt("color"),
                scheduleType = o.optString("scheduleType", "DAILY"),
                weekdayMask = o.optInt("weekdayMask", 0b1111111),
                timesPerWeek = o.optInt("timesPerWeek", 3),
                target = o.optInt("target", 1).coerceAtLeast(1),
                unit = o.optString("unit", ""),
                reminderMinute = if (o.isNull("reminderMinute")) null else o.getInt("reminderMinute"),
                createdEpochDay = o.getLong("createdEpochDay"),
                archived = o.optBoolean("archived", false),
                sortOrder = o.optInt("sortOrder", i),
                note = o.optString("note", ""),
                points = o.optInt("points", 10).coerceIn(1, 1000),
            )
        }
        val ids = habits.map { it.id }.toSet()
        val c = root.getJSONArray("checks")
        val checks = (0 until c.length()).map { i ->
            val a = c.getJSONArray(i)
            CheckEntity(a.getLong(0), a.getLong(1), a.getInt(2))
        }.filter { it.habitId in ids && it.value > 0 }
        val r = root.optJSONArray("rewards") ?: JSONArray()
        val rewards = (0 until r.length()).map { i ->
            val o = r.getJSONObject(i)
            RewardEntity(
                id = o.getLong("id"),
                name = o.getString("name"),
                emoji = o.optString("emoji", "🎁"),
                cost = o.optInt("cost", 50).coerceAtLeast(1),
                repeatable = o.optBoolean("repeatable", true),
                sortOrder = o.optInt("sortOrder", i),
            )
        }
        val rd = root.optJSONArray("redemptions") ?: JSONArray()
        val redemptions = (0 until rd.length()).map { i ->
            val o = rd.getJSONObject(i)
            RedemptionEntity(
                id = o.getLong("id"),
                rewardId = if (o.isNull("rewardId")) null else o.getLong("rewardId"),
                name = o.getString("name"),
                emoji = o.optString("emoji", "🎁"),
                cost = o.getInt("cost"),
                epochDay = o.getLong("epochDay"),
                at = o.optLong("at", 0),
            )
        }
        val s = root.optJSONArray("sky") ?: JSONArray()
        val sky = (0 until s.length()).map { i ->
            val o = s.getJSONObject(i)
            SkyObjectEntity(
                id = o.getLong("id"),
                kind = o.getString("kind"),
                color = o.optInt("color", 0),
                name = o.optString("name", ""),
                x = o.getDouble("x").toFloat(),
                y = o.getDouble("y").toFloat(),
                cost = o.optInt("cost", 0),
                epochDay = o.getLong("epochDay"),
            )
        }
        return Content(habits, checks, rewards, redemptions, sky)
    }
}

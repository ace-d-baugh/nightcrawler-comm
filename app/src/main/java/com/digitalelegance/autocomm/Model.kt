package com.digitalelegance.autocomm

import org.json.JSONObject

enum class Target { FRONT, REAR, BOTH }

enum class Effect(val label: String) {
    SCROLL_LEFT("Scroll Left"),
    SCROLL_RIGHT("Scroll Right"),
    SCROLL_UP("Scroll Up"),
    SCROLL_DOWN("Scroll Down"),
    HOLD("Hold"),
    STATIC("Static"),
    LASER("Laser"),
    SNOWFALL("Snowfall"),
    BUILD("Build"),
    INTERWEAVE("Interweave"),
    TYPEWRITER("Typewriter"),
    BLINK("Blink"),
    FADE("Fade"),
    RAINBOW("Rainbow"),
    WAVE("Wave"),
    SPARKLE("Sparkle"),
}

enum class Font(val label: String) {
    SANS_BOLD("Sans Bold"),
    SANS("Sans"),
    CONDENSED("Condensed"),
    SERIF("Serif"),
    MONO("Mono"),
    CASUAL("Casual"),
    CURSIVE("Cursive"),
}

enum class BorderStyle(val label: String) {
    NONE("None"), SOLID("Solid"), DASHED("Dashed"), CHASE("Chase"), RAINBOW("Rainbow")
}

data class TextStyle(
    val color: Int = 0x00FF40,
    val bg: Int = 0x000000,
    val fontSize: Int = 0, // 0 = auto-fit panel height
    val font: Font = Font.SANS_BOLD,
    val brightness: Int = 100,
    val speed: Int = 5,
    val border: BorderStyle = BorderStyle.NONE,
    val borderColor: Int = 0xFFFFFF,
    val effect: Effect = Effect.SCROLL_LEFT,
    val wrap: Boolean = false, // multi-line when the font is small enough
) {
    fun toJson(): JSONObject = JSONObject()
        .put("color", color).put("bg", bg).put("fontSize", fontSize).put("font", font.name)
        .put("brightness", brightness).put("speed", speed)
        .put("border", border.name).put("borderColor", borderColor).put("effect", effect.name).put("wrap", wrap)

    companion object {
        fun fromJson(o: JSONObject?): TextStyle {
            if (o == null) return TextStyle()
            val d = TextStyle()
            val fx = when (val e = o.optString("effect")) {
                "SCROLL" -> if (o.optString("dir") == "LTR") Effect.SCROLL_RIGHT else Effect.SCROLL_LEFT
                else -> enumOr(e, d.effect)
            }
            return TextStyle(
                color = o.optInt("color", d.color),
                bg = o.optInt("bg", d.bg),
                fontSize = o.optInt("fontSize", d.fontSize),
                font = enumOr(o.optString("font"), d.font),
                brightness = o.optInt("brightness", d.brightness),
                speed = o.optInt("speed", d.speed),
                border = enumOr(o.optString("border"), d.border),
                borderColor = o.optInt("borderColor", d.borderColor),
                effect = fx,
                wrap = o.optBoolean("wrap", false),
            )
        }
    }
}

inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
    runCatching { enumValueOf<E>(name ?: "") }.getOrDefault(fallback)

enum class MsgKind(val label: String) { TEXT("Text"), IMAGE("Image"), ANIMATION("Animation") }

/**
 * A saved message: styled text, an image, or an animation. Pixels are ARGB, alpha 0 = LED off.
 * [target] is where it goes when used as a Quick Response.
 */
class Message(
    val id: String,
    val name: String,
    val kind: MsgKind,
    val target: Target = Target.BOTH,
    val text: String = "",
    val style: TextStyle = TextStyle(),
    val w: Int = 0,
    val h: Int = 0,
    val frames: List<IntArray> = emptyList(),
    val delays: List<Int> = emptyList(),
) {
    val isText get() = kind == MsgKind.TEXT

    fun toFrames(): Frames? =
        if (isText || frames.isEmpty()) null
        else Frames(w, h, frames, List(frames.size) { delays.getOrElse(it) { 120 } })

    fun copy(
        id: String = this.id, name: String = this.name, target: Target = this.target,
        text: String = this.text, style: TextStyle = this.style,
    ) = Message(id, name, kind, target, text, style, w, h, frames, delays)
}

const val QUICK_SLOTS = 12
const val MAX_ANIM_FRAMES = 40
